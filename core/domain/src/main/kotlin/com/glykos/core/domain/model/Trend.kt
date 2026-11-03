package com.glykos.core.domain.model

/** Glucose trend direction, independent of any specific data source. */
enum class Trend {
    FALLING_FAST,
    FALLING,
    STABLE,
    RISING,
    RISING_FAST,
    UNKNOWN,
}
