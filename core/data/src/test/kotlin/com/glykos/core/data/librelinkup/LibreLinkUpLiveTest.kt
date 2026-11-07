package com.glykos.core.data.librelinkup

import com.glykos.core.domain.model.GlucoseResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

/**
 * Hits the REAL LibreLinkUp API with the follower account from local.properties.
 * Opt-in (-Pllu.live=true). Run sparingly: every run is a real login.
 * Never prints credentials or tokens.
 */
@EnabledIfSystemProperty(named = "llu.live", matches = "true")
class LibreLinkUpLiveTest {

    @Test
    fun `reads the current glucose from the real API`() = runBlocking {
        val email = System.getProperty("llu.email").orEmpty()
        val password = System.getProperty("llu.password").orEmpty()
        check(email.isNotBlank() && password.isNotBlank()) { "Set llu.email and llu.password in local.properties" }

        val config = System.getProperty("llu.host")?.let { LibreLinkUpConfig(host = it) } ?: LibreLinkUpConfig()
        HttpClient(OkHttp).use { http ->
            val source = LibreLinkUpSource(http, { LibreLinkUpCredentials(email, password) }, config = config)
            val result = source.latest()
            when (result) {
                is GlucoseResult.Available -> {
                    val r = result.reading
                    println("LIVE ✓ ${r.valueMgDl} mg/dl, ${r.trend}, sensor time ${r.timestamp}, age ${r.age(Clock.System.now())}")
                }
                is GlucoseResult.Unavailable -> println("LIVE ✗ ${result.reason}")
            }
            assertTrue(result is GlucoseResult.Available, "Expected a reading, got $result")
        }
    }
}
