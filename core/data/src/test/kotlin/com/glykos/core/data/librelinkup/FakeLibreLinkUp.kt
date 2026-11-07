package com.glykos.core.data.librelinkup

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Scripted LibreLinkUp server. Replies for a route are consumed in order; the last one repeats. */
class FakeLibreLinkUp {

    data class Reply(
        val status: HttpStatusCode = HttpStatusCode.OK,
        val body: String = "",
        val headers: Map<String, String> = emptyMap(),
        val error: Throwable? = null,
    )

    val requests = mutableListOf<HttpRequestData>()
    private val routes = mutableMapOf<String, ArrayDeque<Reply>>()

    fun on(method: HttpMethod, host: String, path: String, vararg replies: Reply) {
        routes[key(method, host, path)] = ArrayDeque(replies.toList())
    }

    fun count(path: String, host: String? = null): Int =
        requests.count { it.url.encodedPath == path && (host == null || it.url.host == host) }

    fun last(path: String): HttpRequestData = requests.last { it.url.encodedPath == path }

    val client = HttpClient(
        MockEngine { request ->
            requests += request
            val queue = routes[key(request.method, request.url.host, request.url.encodedPath)]
                ?: return@MockEngine respond("unrouted", HttpStatusCode.NotFound)
            val reply = if (queue.size > 1) queue.removeFirst() else queue.first()
            reply.error?.let { throw it }
            val headers = (reply.headers + (HttpHeaders.ContentType to "application/json"))
            respond(
                content = reply.body,
                status = reply.status,
                headers = headersOf(*headers.map { (k, v) -> k to listOf(v) }.toTypedArray()),
            )
        },
    )

    private fun key(method: HttpMethod, host: String, path: String) = "${method.value} $host $path"
}

class FakeClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}

object Fixtures {
    const val EU = "api-eu.libreview.io"
    const val DE = "api-de.libreview.io"
    const val LOGIN = "/llu/auth/login"
    const val CONNECTIONS = "/llu/connections"
    const val PATIENT_ID = "pat-1"
    const val GRAPH = "/llu/connections/$PATIENT_ID/graph"
    const val USER_ID = "user-123"

    /** printf 'user-123' | sha256sum */
    const val USER_ID_SHA256 = "fcdec6df4d44dbc637c7c5b58efface52a7f8a88535423430255be0bb89bedd8"

    fun loginOk(token: String = "tok-1", expires: Instant) = """
        {"status":0,"data":{
          "user":{"id":"$USER_ID","firstName":"F","lastName":"A","country":"ES"},
          "authTicket":{"token":"$token","expires":${expires.epochSeconds},"duration":15552000000}
        }}
    """.trimIndent()

    fun loginRedirect(region: String) = """{"status":0,"data":{"redirect":true,"region":"$region"}}"""

    const val LOGIN_BAD_CREDENTIALS = """{"status":2,"error":{"message":"notAuthenticated"}}"""

    fun loginStep(type: String) = """
        {"status":4,"data":{"step":{"type":"$type","componentName":"AcceptDocument"},
          "user":{"id":"$USER_ID"},"authTicket":{"token":"tmp","expires":0,"duration":0}}}
    """.trimIndent()

    val connectionsOk = """
        {"status":0,"data":[{"id":"c-1","patientId":"$PATIENT_ID","firstName":"F","lastName":"A",
          "glucoseMeasurement":{"FactoryTimestamp":"11/7/2026 9:58:00 AM","Timestamp":"11/7/2026 10:58:00 AM",
          "ValueInMgPerDl":100,"TrendArrow":3,"isHigh":false,"isLow":false}}],
         "ticket":{"token":"tok-x","expires":0,"duration":0}}
    """.trimIndent()

    const val CONNECTIONS_EMPTY = """{"status":0,"data":[]}"""

    fun graphOk(value: Number = 112, arrow: Int? = 2, factoryTimestamp: String = "11/7/2026 9:59:00 AM"): String {
        val trend = arrow?.let { ""","TrendArrow":$it""" } ?: ""
        return """
            {"status":0,"data":{
              "connection":{"patientId":"$PATIENT_ID","glucoseMeasurement":{
                "FactoryTimestamp":"$factoryTimestamp","Timestamp":"11/7/2026 10:59:00 AM",
                "type":1,"ValueInMgPerDl":$value$trend,"MeasurementColor":1,"GlucoseUnits":1,"Value":$value,
                "isHigh":false,"isLow":false}},
              "activeSensors":[],"graphData":[]},
             "ticket":{"token":"tok-x","expires":0,"duration":0}}
        """.trimIndent()
    }

    const val GRAPH_NO_MEASUREMENT = """{"status":0,"data":{"connection":{"patientId":"$PATIENT_ID"},"graphData":[]}}"""
}
