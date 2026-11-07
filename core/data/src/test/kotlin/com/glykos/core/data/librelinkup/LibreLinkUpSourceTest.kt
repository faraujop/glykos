package com.glykos.core.data.librelinkup

import com.glykos.core.data.librelinkup.FakeLibreLinkUp.Reply
import com.glykos.core.data.librelinkup.Fixtures.CONNECTIONS
import com.glykos.core.data.librelinkup.Fixtures.CONNECTIONS_EMPTY
import com.glykos.core.data.librelinkup.Fixtures.DE
import com.glykos.core.data.librelinkup.Fixtures.EU
import com.glykos.core.data.librelinkup.Fixtures.GRAPH
import com.glykos.core.data.librelinkup.Fixtures.GRAPH_NO_MEASUREMENT
import com.glykos.core.data.librelinkup.Fixtures.LOGIN
import com.glykos.core.data.librelinkup.Fixtures.LOGIN_BAD_CREDENTIALS
import com.glykos.core.data.librelinkup.Fixtures.USER_ID_SHA256
import com.glykos.core.data.librelinkup.Fixtures.connectionsOk
import com.glykos.core.data.librelinkup.Fixtures.graphOk
import com.glykos.core.data.librelinkup.Fixtures.loginOk
import com.glykos.core.data.librelinkup.Fixtures.loginRedirect
import com.glykos.core.data.librelinkup.Fixtures.loginStep
import com.glykos.core.domain.model.GlucoseReading
import com.glykos.core.domain.model.GlucoseResult
import com.glykos.core.domain.model.GlucoseResult.Reason
import com.glykos.core.domain.model.Trend
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class LibreLinkUpSourceTest {

    private val t0 = Instant.parse("2026-11-07T10:00:00Z")
    private val clock = FakeClock(t0)
    private val server = FakeLibreLinkUp()
    private var credentials: LibreLinkUpCredentials? = LibreLinkUpCredentials("follower@example.com", "secret")

    private val source = LibreLinkUpSource(
        httpClient = server.client,
        credentials = { credentials },
        clock = clock,
    )

    private fun happyServer(expires: Instant = t0 + 180.days) {
        server.on(HttpMethod.Post, EU, LOGIN, Reply(body = loginOk(expires = expires)))
        server.on(HttpMethod.Get, EU, CONNECTIONS, Reply(body = connectionsOk))
        server.on(HttpMethod.Get, EU, GRAPH, Reply(body = graphOk()))
    }

    private fun unavailable(reason: Reason) = GlucoseResult.Unavailable(reason)

    private fun HttpRequestData.header(name: String): String? = headers[name]

    @Nested
    inner class HappyPath {

        @Test
        fun `logs in, finds the patient and returns the current measurement`() = runTest {
            happyServer()
            val expected = GlucoseReading(112, Trend.FALLING, Instant.parse("2026-11-07T09:59:00Z"))
            assertEquals(GlucoseResult.Available(expected), source.latest())
        }

        @Test
        fun `login sends the credentials as JSON`() = runTest {
            happyServer()
            source.latest()
            val body = (server.last(LOGIN).body as TextContent).text
            val json = Json.parseToJsonElement(body).jsonObject
            assertEquals("follower@example.com", json["email"]?.jsonPrimitive?.content)
            assertEquals("secret", json["password"]?.jsonPrimitive?.content)
        }

        @Test
        fun `every request carries product and version headers`() = runTest {
            happyServer()
            source.latest()
            server.requests.forEach {
                assertEquals("llu.android", it.header("product"))
                assertEquals("4.16.0", it.header("version"))
            }
        }

        @Test
        fun `authenticated requests carry bearer token and hashed account id`() = runTest {
            happyServer()
            source.latest()
            listOf(server.last(CONNECTIONS), server.last(GRAPH)).forEach {
                assertEquals("Bearer tok-1", it.header("Authorization"))
                assertEquals(USER_ID_SHA256, it.header("account-id"))
            }
        }

        @Test
        fun `login does not send a token`() = runTest {
            happyServer()
            source.latest()
            assertNull(server.last(LOGIN).header("Authorization"))
        }

        @Test
        fun `fractional values are rounded`() = runTest {
            happyServer()
            server.on(HttpMethod.Get, EU, GRAPH, Reply(body = graphOk(value = 112.6)))
            assertEquals(113, (source.latest() as GlucoseResult.Available).reading.valueMgDl)
        }

        @Test
        fun `missing trend arrow becomes unknown`() = runTest {
            happyServer()
            server.on(HttpMethod.Get, EU, GRAPH, Reply(body = graphOk(arrow = null)))
            assertEquals(Trend.UNKNOWN, (source.latest() as GlucoseResult.Available).reading.trend)
        }
    }

    @Nested
    inner class Session {

        @Test
        fun `token and patient id are reused across polls`() = runTest {
            happyServer()
            repeat(3) { source.latest() }
            assertEquals(1, server.count(LOGIN))
            assertEquals(1, server.count(CONNECTIONS))
            assertEquals(3, server.count(GRAPH))
        }

        @Test
        fun `logs in again when the token is about to expire`() = runTest {
            happyServer(expires = t0 + 10.minutes)
            source.latest()
            clock.now = t0 + 9.minutes + 30.seconds
            source.latest()
            assertEquals(2, server.count(LOGIN))
        }

        @Test
        fun `unauthorized graph triggers one re-login and retry`() = runTest {
            happyServer()
            source.latest()
            server.on(
                HttpMethod.Get, EU, GRAPH,
                Reply(status = HttpStatusCode.Unauthorized),
                Reply(body = graphOk(value = 120)),
            )
            assertEquals(120, (source.latest() as GlucoseResult.Available).reading.valueMgDl)
            assertEquals(2, server.count(LOGIN))
        }

        @Test
        fun `unauthorized again after re-login is an auth failure, without looping`() = runTest {
            happyServer()
            server.on(HttpMethod.Get, EU, GRAPH, Reply(status = HttpStatusCode.Unauthorized))
            assertEquals(unavailable(Reason.AUTH), source.latest())
            assertEquals(2, server.count(LOGIN))
        }
    }

    @Nested
    inner class Redirect {

        @Test
        fun `follows the region redirect and keeps using the new region`() = runTest {
            server.on(HttpMethod.Post, EU, LOGIN, Reply(body = loginRedirect("de")))
            server.on(HttpMethod.Post, DE, LOGIN, Reply(body = loginOk(expires = t0 + 180.days)))
            server.on(HttpMethod.Get, DE, CONNECTIONS, Reply(body = connectionsOk))
            server.on(HttpMethod.Get, DE, GRAPH, Reply(body = graphOk()))

            assertEquals(112, (source.latest() as GlucoseResult.Available).reading.valueMgDl)
            source.latest()
            assertEquals(1, server.count(LOGIN, host = EU))
            assertEquals(2, server.count(GRAPH, host = DE))
        }

        @Test
        fun `a second redirect is not followed`() = runTest {
            server.on(HttpMethod.Post, EU, LOGIN, Reply(body = loginRedirect("de")))
            server.on(HttpMethod.Post, DE, LOGIN, Reply(body = loginRedirect("eu")))
            assertEquals(unavailable(Reason.AUTH), source.latest())
            assertEquals(1, server.count(LOGIN, host = DE))
        }

        @Test
        fun `an invalid region is rejected`() = runTest {
            server.on(HttpMethod.Post, EU, LOGIN, Reply(body = loginRedirect("evil.com/x")))
            assertEquals(unavailable(Reason.AUTH), source.latest())
            assertEquals(1, server.requests.size)
        }
    }

    @Nested
    inner class AuthFailures {

        @Test
        fun `bad credentials are an auth failure`() = runTest {
            server.on(HttpMethod.Post, EU, LOGIN, Reply(body = LOGIN_BAD_CREDENTIALS))
            assertEquals(unavailable(Reason.AUTH), source.latest())
        }

        @ParameterizedTest
        @ValueSource(strings = ["tou", "pp", "verifyEmail"])
        fun `pending account steps are an auth failure`(step: String) = runTest {
            server.on(HttpMethod.Post, EU, LOGIN, Reply(body = loginStep(step)))
            assertEquals(unavailable(Reason.AUTH), source.latest())
            assertEquals(0, server.count(CONNECTIONS))
        }

        @Test
        fun `missing credentials fail without touching the network`() = runTest {
            credentials = null
            assertEquals(unavailable(Reason.AUTH), source.latest())
            assertEquals(0, server.requests.size)
        }

        @Test
        fun `after a failed login, retries are blocked with exponential backoff`() = runTest {
            server.on(HttpMethod.Post, EU, LOGIN, Reply(body = LOGIN_BAD_CREDENTIALS))

            source.latest()
            clock.now = t0 + 59.seconds
            assertEquals(unavailable(Reason.AUTH), source.latest())
            assertEquals(1, server.count(LOGIN))

            clock.now = t0 + 1.minutes
            source.latest()
            assertEquals(2, server.count(LOGIN))

            clock.now = t0 + 2.minutes + 59.seconds
            source.latest()
            assertEquals(2, server.count(LOGIN))

            clock.now = t0 + 3.minutes
            source.latest()
            assertEquals(3, server.count(LOGIN))
        }

        @Test
        fun `login backoff is capped at 30 minutes`() = runTest {
            server.on(HttpMethod.Post, EU, LOGIN, Reply(body = LOGIN_BAD_CREDENTIALS))
            repeat(10) {
                source.latest()
                clock.now = clock.now + 30.minutes
            }
            assertEquals(10, server.count(LOGIN))
        }

        @Test
        fun `a successful login resets the backoff`() = runTest {
            server.on(
                HttpMethod.Post, EU, LOGIN,
                Reply(body = LOGIN_BAD_CREDENTIALS),
                Reply(body = LOGIN_BAD_CREDENTIALS),
                Reply(body = loginOk(expires = t0 + 1.minutes)),
                Reply(body = LOGIN_BAD_CREDENTIALS),
            )
            server.on(HttpMethod.Get, EU, CONNECTIONS, Reply(body = connectionsOk))
            server.on(HttpMethod.Get, EU, GRAPH, Reply(body = graphOk()))

            source.latest() // fail, wait 1 min
            clock.now = t0 + 1.minutes
            source.latest() // fail, wait 2 min
            clock.now = t0 + 3.minutes
            assertEquals(112, (source.latest() as GlucoseResult.Available).reading.valueMgDl)
            clock.now = t0 + 4.minutes // token expired -> login fails again
            source.latest()
            clock.now = t0 + 5.minutes // backoff restarted at 1 min, so this retries
            source.latest()
            assertEquals(5, server.count(LOGIN))
        }
    }

    @Nested
    inner class RateLimit {

        @Test
        fun `429 is rate limited and honours Retry-After`() = runTest {
            happyServer()
            server.on(
                HttpMethod.Get, EU, GRAPH,
                Reply(status = HttpStatusCode.TooManyRequests, headers = mapOf("Retry-After" to "120")),
                Reply(body = graphOk()),
            )
            assertEquals(unavailable(Reason.RATE_LIMITED), source.latest())

            clock.now = t0 + 119.seconds
            assertEquals(unavailable(Reason.RATE_LIMITED), source.latest())
            assertEquals(1, server.count(GRAPH))

            clock.now = t0 + 120.seconds
            assertEquals(112, (source.latest() as GlucoseResult.Available).reading.valueMgDl)
        }

        @Test
        fun `429 without Retry-After waits 5 minutes`() = runTest {
            happyServer()
            server.on(HttpMethod.Get, EU, GRAPH, Reply(status = HttpStatusCode.TooManyRequests), Reply(body = graphOk()))
            source.latest()
            clock.now = t0 + 4.minutes + 59.seconds
            source.latest()
            assertEquals(1, server.count(GRAPH))
            clock.now = t0 + 5.minutes
            source.latest()
            assertEquals(2, server.count(GRAPH))
        }

        @Test
        fun `429 on login is rate limited too`() = runTest {
            server.on(HttpMethod.Post, EU, LOGIN, Reply(status = HttpStatusCode.TooManyRequests))
            assertEquals(unavailable(Reason.RATE_LIMITED), source.latest())
        }
    }

    @Nested
    inner class OtherFailures {

        @Test
        fun `network error is reported as network`() = runTest {
            happyServer()
            server.on(HttpMethod.Get, EU, GRAPH, Reply(error = java.io.IOException("no route")))
            assertEquals(unavailable(Reason.NETWORK), source.latest())
        }

        @Test
        fun `network error does not block the next poll`() = runTest {
            happyServer()
            server.on(HttpMethod.Get, EU, GRAPH, Reply(error = java.io.IOException("no route")), Reply(body = graphOk()))
            source.latest()
            assertEquals(112, (source.latest() as GlucoseResult.Available).reading.valueMgDl)
        }

        @Test
        fun `server error is unknown`() = runTest {
            happyServer()
            server.on(HttpMethod.Get, EU, GRAPH, Reply(status = HttpStatusCode.InternalServerError))
            assertEquals(unavailable(Reason.UNKNOWN), source.latest())
        }

        @Test
        fun `malformed body is unknown`() = runTest {
            happyServer()
            server.on(HttpMethod.Get, EU, GRAPH, Reply(body = "<html>cloudflare</html>"))
            assertEquals(unavailable(Reason.UNKNOWN), source.latest())
        }

        @Test
        fun `no connections means no sensor data`() = runTest {
            happyServer()
            server.on(HttpMethod.Get, EU, CONNECTIONS, Reply(body = CONNECTIONS_EMPTY))
            assertEquals(unavailable(Reason.NO_SENSOR_DATA), source.latest())
        }

        @Test
        fun `graph without measurement means no sensor data`() = runTest {
            happyServer()
            server.on(HttpMethod.Get, EU, GRAPH, Reply(body = GRAPH_NO_MEASUREMENT))
            assertEquals(unavailable(Reason.NO_SENSOR_DATA), source.latest())
        }

        @Test
        fun `unparseable timestamp is unknown`() = runTest {
            happyServer()
            server.on(HttpMethod.Get, EU, GRAPH, Reply(body = graphOk(factoryTimestamp = "yesterday")))
            assertEquals(unavailable(Reason.UNKNOWN), source.latest())
        }

        @Test
        fun `cancellation is propagated, not swallowed`() = runTest {
            happyServer()
            server.on(HttpMethod.Get, EU, GRAPH, Reply(error = CancellationException("stop")))
            assertThrows<CancellationException> { source.latest() }
        }
    }
}
