package com.glykos.core.data.librelinkup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Minimal subset of the unofficial LibreLinkUp API. Field names checked (2026-11) against
// nightscout-librelink-up and pylibrelinkup. Unknown fields are ignored on purpose.

@Serializable
internal data class LoginRequest(val email: String, val password: String) {
    override fun toString() = "LoginRequest(email=***, password=***)"
}

@Serializable
internal data class LoginResponse(val status: Int = -1, val data: LoginData? = null)

@Serializable
internal data class LoginData(
    val redirect: Boolean = false,
    val region: String? = null,
    /** Pending action on the account (tou, pp, verifyEmail): must be resolved in the official app. */
    val step: Step? = null,
    val user: User? = null,
    val authTicket: AuthTicket? = null,
)

@Serializable
internal data class Step(val type: String? = null)

@Serializable
internal data class User(val id: String)

@Serializable
internal data class AuthTicket(val token: String, val expires: Long)

@Serializable
internal data class ConnectionsResponse(val status: Int = 0, val data: List<Connection> = emptyList())

@Serializable
internal data class Connection(val patientId: String)

@Serializable
internal data class GraphResponse(val status: Int = 0, val data: GraphData? = null)

@Serializable
internal data class GraphData(val connection: GraphConnection? = null)

@Serializable
internal data class GraphConnection(val glucoseMeasurement: GlucoseMeasurementDto? = null)

@Serializable
internal data class GlucoseMeasurementDto(
    /** Sensor time in UTC, "M/d/yyyy h:mm:ss AM|PM". `Timestamp` is local time and is ignored. */
    @SerialName("FactoryTimestamp") val factoryTimestamp: String,
    @SerialName("ValueInMgPerDl") val valueInMgPerDl: Double,
    @SerialName("TrendArrow") val trendArrow: Int? = null,
)
