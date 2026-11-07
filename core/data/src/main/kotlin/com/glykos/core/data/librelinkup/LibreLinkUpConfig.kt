package com.glykos.core.data.librelinkup

/**
 * Credentials of the LibreLinkUp FOLLOWER account (never the main LibreLink account).
 * Never log them: [toString] is redacted.
 */
class LibreLinkUpCredentials(val email: String, val password: String) {
    override fun toString() = "LibreLinkUpCredentials(***)"
}

/**
 * @property host initial regional host; the login may redirect to another region.
 * @property product / [version] identify the client to the API. Abbott can start rejecting old
 *   versions at any time. TODO(verify): values match pylibrelinkup and nightscout-librelink-up as of
 *   2026-11 ("4.16.0"); confirm against a live login and bump if requests start failing.
 */
data class LibreLinkUpConfig(
    val host: String = "api-eu.libreview.io",
    val product: String = "llu.android",
    val version: String = "4.16.0",
)
