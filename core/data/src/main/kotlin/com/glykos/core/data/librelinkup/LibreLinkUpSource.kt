package com.glykos.core.data.librelinkup

import com.glykos.core.data.source.GlucoseSource
import com.glykos.core.domain.model.GlucoseReading
import com.glykos.core.domain.model.GlucoseResult
import com.glykos.core.domain.model.GlucoseResult.Reason
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * [GlucoseSource] backed by the unofficial LibreLinkUp API (follower account).
 *
 * Flow: `POST /llu/auth/login` (following one region redirect) → `GET /llu/connections` (patient id,
 * cached) → `GET /llu/connections/{patientId}/graph` on every poll.
 *
 * Protects the account from lockout: the token is reused until shortly before it expires, a failed
 * login blocks further logins with exponential backoff (1 min doubling up to 30 min), and 429
 * responses block all requests until `Retry-After`. While blocked it answers without network.
 *
 * Never throws for expected failures: everything becomes [GlucoseResult.Unavailable].
 * Callers must poll at most every 60 s.
 */
class LibreLinkUpSource(
    private val httpClient: HttpClient,
    private val credentials: suspend () -> LibreLinkUpCredentials?,
    private val clock: Clock = Clock.System,
    private val config: LibreLinkUpConfig = LibreLinkUpConfig(),
) : GlucoseSource {

    private class Session(val token: String, val expires: Instant, val accountId: String)

    private class Block(val until: Instant, val reason: Reason)

    private class Failure(val reason: Reason, message: String) : Exception(message)

    private class Unauthorized : Exception("401")

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private var host = config.host
    private var session: Session? = null
    private var patientId: String? = null
    private var block: Block? = null
    private var failedLogins = 0

    override suspend fun latest(): GlucoseResult = mutex.withLock {
        block?.let { if (clock.now() < it.until) return GlucoseResult.Unavailable(it.reason) }
        block = null
        try {
            GlucoseResult.Available(fetch())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Failure) {
            GlucoseResult.Unavailable(e.reason)
        } catch (e: IOException) {
            GlucoseResult.Unavailable(Reason.NETWORK)
        } catch (e: SerializationException) {
            GlucoseResult.Unavailable(Reason.UNKNOWN)
        } catch (e: Exception) {
            GlucoseResult.Unavailable(Reason.UNKNOWN)
        }
    }

    private suspend fun fetch(): GlucoseReading {
        val fresh = session == null || clock.now() >= session!!.expires - TOKEN_MARGIN
        val current = if (fresh) login() else session!!
        return try {
            readGraph(current)
        } catch (_: Unauthorized) {
            session = null
            try {
                readGraph(login())
            } catch (_: Unauthorized) {
                session = null
                registerFailedLogin()
                throw Failure(Reason.AUTH, "Unauthorized after re-login")
            }
        }
    }

    private suspend fun readGraph(session: Session): GlucoseReading {
        val pid = patientId ?: findPatient(session).also { patientId = it }
        val graph = json.decodeFromString<GraphResponse>(
            authorizedGet("/llu/connections/$pid/graph", session).bodyAsText(),
        )
        val m = graph.data?.connection?.glucoseMeasurement
            ?: throw Failure(Reason.NO_SENSOR_DATA, "No current measurement")
        val timestamp = parseFactoryTimestamp(m.factoryTimestamp)
            ?: throw Failure(Reason.UNKNOWN, "Unparseable timestamp")
        return GlucoseReading(m.valueInMgPerDl.roundToInt(), trendFromArrow(m.trendArrow), timestamp)
    }

    private suspend fun findPatient(session: Session): String {
        val connections = json.decodeFromString<ConnectionsResponse>(
            authorizedGet("/llu/connections", session).bodyAsText(),
        )
        // A follower account normally sees exactly one patient: the owner.
        return connections.data.firstOrNull()?.patientId
            ?: throw Failure(Reason.NO_SENSOR_DATA, "No LibreLinkUp connections")
    }

    private suspend fun login(): Session {
        val creds = credentials() ?: throw Failure(Reason.AUTH, "No credentials configured")
        val body = json.encodeToString(LoginRequest.serializer(), LoginRequest(creds.email, creds.password))
        var redirected = false
        while (true) {
            val response = httpClient.post("https://$host/llu/auth/login") {
                commonHeaders()
                setBody(TextContent(body, ContentType.Application.Json))
            }
            checkRateLimit(response)
            if (!response.status.isSuccess()) loginFailed("HTTP ${response.status.value}")

            val login = json.decodeFromString<LoginResponse>(response.bodyAsText())
            val data = login.data
            if (data?.step?.type != null) loginFailed("Pending account step: ${data.step.type}")
            if (login.status != 0) loginFailed("Login status ${login.status}")

            if (data?.redirect == true) {
                val region = data.region?.lowercase()
                if (redirected || region == null || !REGION.matches(region)) loginFailed("Invalid redirect")
                redirected = true
                host = "api-$region.libreview.io"
                continue
            }

            val user = data?.user ?: loginFailed("Missing user")
            val ticket = data.authTicket ?: loginFailed("Missing auth ticket")
            failedLogins = 0
            return Session(ticket.token, Instant.fromEpochSeconds(ticket.expires), accountIdHash(user.id))
                .also { session = it }
        }
    }

    private fun loginFailed(message: String): Nothing {
        registerFailedLogin()
        throw Failure(Reason.AUTH, message)
    }

    private fun registerFailedLogin() {
        val wait = (LOGIN_BACKOFF_BASE * (1 shl failedLogins.coerceAtMost(5))).coerceAtMost(LOGIN_BACKOFF_MAX)
        failedLogins++
        block = Block(clock.now() + wait, Reason.AUTH)
    }

    private suspend fun authorizedGet(path: String, session: Session): HttpResponse {
        val response = httpClient.get("https://$host$path") {
            commonHeaders()
            header(HttpHeaders.Authorization, "Bearer ${session.token}")
            header("account-id", session.accountId)
        }
        checkRateLimit(response)
        if (response.status == HttpStatusCode.Unauthorized) throw Unauthorized()
        if (!response.status.isSuccess()) throw Failure(Reason.UNKNOWN, "HTTP ${response.status.value}")
        return response
    }

    private fun checkRateLimit(response: HttpResponse) {
        if (response.status != HttpStatusCode.TooManyRequests) return
        val wait = response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()?.seconds ?: RATE_LIMIT_DEFAULT
        block = Block(clock.now() + wait, Reason.RATE_LIMITED)
        throw Failure(Reason.RATE_LIMITED, "429")
    }

    private fun HttpRequestBuilder.commonHeaders() {
        header("product", config.product)
        header("version", config.version)
        header(HttpHeaders.Accept, "application/json")
    }

    private companion object {
        val TOKEN_MARGIN: Duration = 60.seconds
        val LOGIN_BACKOFF_BASE: Duration = 1.minutes
        val LOGIN_BACKOFF_MAX: Duration = 30.minutes
        val RATE_LIMIT_DEFAULT: Duration = 5.minutes
        val REGION = Regex("^[a-z0-9]{2,4}$")
    }
}
