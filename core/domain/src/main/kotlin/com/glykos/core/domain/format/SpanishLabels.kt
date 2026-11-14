package com.glykos.core.domain.format

import com.glykos.core.domain.model.GlucoseResult.Reason
import com.glykos.core.domain.model.Trend

/** Short user-facing explanation of why there is no data, or null when there is nothing useful to say. */
internal fun Reason.label(): String? = when (this) {
    Reason.NETWORK -> "sin conexión"
    Reason.AUTH -> "revisa la cuenta"
    Reason.RATE_LIMITED -> "servicio saturado"
    Reason.NO_SENSOR_DATA -> "el sensor no envía datos"
    Reason.UNKNOWN -> null
}

/** Trend in words ("bajando", "subiendo rápido"...), or null when unknown. */
internal fun Trend.label(): String? = when (this) {
    Trend.FALLING_FAST -> "bajando rápido"
    Trend.FALLING -> "bajando"
    Trend.STABLE -> "estable"
    Trend.RISING -> "subiendo"
    Trend.RISING_FAST -> "subiendo rápido"
    Trend.UNKNOWN -> null
}

internal fun String.capitalizeFirst(): String = replaceFirstChar { it.uppercaseChar() }
