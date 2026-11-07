package com.glykos.core.domain.format

import com.glykos.core.domain.model.GlucoseResult.Reason

/** Short user-facing explanation of why there is no data, or null when there is nothing useful to say. */
internal fun Reason.label(): String? = when (this) {
    Reason.NETWORK -> "sin conexión"
    Reason.AUTH -> "revisa la cuenta"
    Reason.RATE_LIMITED -> "servicio saturado"
    Reason.NO_SENSOR_DATA -> "el sensor no envía datos"
    Reason.UNKNOWN -> null
}

internal fun String.capitalizeFirst(): String = replaceFirstChar { it.uppercaseChar() }
