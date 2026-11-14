package com.glykos.core.data.demo

import com.glykos.core.data.librelinkup.LibreLinkUpCredentials
import com.glykos.core.data.source.GlucoseSource

/**
 * Login for the Google Play reviewer. Configured per build from `local.properties`
 * (`demo.email`, `demo.password`), so it is not in the public repository.
 */
class DemoCredentials(private val email: String, private val password: String) {

    fun matchesEmail(other: String): Boolean = other.trim().equals(email, ignoreCase = true)

    fun matches(credentials: LibreLinkUpCredentials): Boolean =
        matchesEmail(credentials.email) && credentials.password == password

    companion object {
        /** Null (demo mode disabled) unless both values are set. */
        fun of(email: String, password: String): DemoCredentials? =
            if (email.isBlank() || password.isEmpty()) null else DemoCredentials(email.trim(), password)
    }
}

/** Whether this build has a reviewer login. Injected wrapper so the credentials can be absent. */
class DemoMode(val credentials: DemoCredentials?) {
    fun isDemoEmail(email: String?): Boolean = email != null && credentials?.matchesEmail(email) == true
}

/** Uses [demo] when the saved credentials are the reviewer's, [real] otherwise. */
class DemoAwareSource(
    private val real: GlucoseSource,
    private val demo: GlucoseSource,
    private val demoCredentials: DemoCredentials?,
    private val credentials: suspend () -> LibreLinkUpCredentials?,
) : GlucoseSource {

    override suspend fun latest() =
        if (isDemo()) demo.latest() else real.latest()

    private suspend fun isDemo(): Boolean {
        val demoCreds = demoCredentials ?: return false
        return credentials()?.let(demoCreds::matches) == true
    }
}
