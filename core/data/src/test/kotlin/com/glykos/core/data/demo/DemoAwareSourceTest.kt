package com.glykos.core.data.demo

import com.glykos.core.data.librelinkup.LibreLinkUpCredentials
import com.glykos.core.data.source.GlucoseSource
import com.glykos.core.domain.model.GlucoseResult
import com.glykos.core.domain.model.GlucoseResult.Reason
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DemoAwareSourceTest {

    private val realResult = GlucoseResult.Unavailable(Reason.NETWORK)
    private val demoResult = GlucoseResult.Unavailable(Reason.UNKNOWN)
    private var realCalls = 0
    private val real = object : GlucoseSource {
        override suspend fun latest(): GlucoseResult { realCalls++; return realResult }
    }
    private val demo = object : GlucoseSource {
        override suspend fun latest(): GlucoseResult = demoResult
    }
    private val demoCredentials = DemoCredentials("revisor@example.com", "s3cret")

    private fun source(saved: LibreLinkUpCredentials?, demoCreds: DemoCredentials? = demoCredentials) =
        DemoAwareSource(real, demo, demoCreds, credentials = { saved })

    @Test
    fun `demo credentials use the simulated source and never the network`() = runTest {
        assertEquals(demoResult, source(LibreLinkUpCredentials(" Revisor@Example.com ", "s3cret")).latest())
        assertEquals(0, realCalls)
    }

    @Test
    fun `other credentials use the real source`() = runTest {
        assertEquals(realResult, source(LibreLinkUpCredentials("me@example.com", "s3cret")).latest())
    }

    @Test
    fun `demo email with a wrong password uses the real source`() = runTest {
        assertEquals(realResult, source(LibreLinkUpCredentials("revisor@example.com", "nope")).latest())
    }

    @Test
    fun `without configured demo credentials everything uses the real source`() = runTest {
        assertEquals(realResult, source(LibreLinkUpCredentials("", ""), demoCreds = null).latest())
    }

    @Test
    fun `demo credentials are only built when both values are set`() {
        assertNull(DemoCredentials.of("", "x"))
        assertNull(DemoCredentials.of("a@b.c", ""))
        assertTrue(DemoCredentials.of("a@b.c", "x")!!.matchesEmail("A@B.C"))
        assertFalse(DemoCredentials.of("a@b.c", "x")!!.matchesEmail("other@b.c"))
    }
}
