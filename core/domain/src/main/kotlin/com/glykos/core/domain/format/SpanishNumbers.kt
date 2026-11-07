package com.glykos.core.domain.format

/**
 * Spells integers 0..999 in Spanish, so TTS reads "ciento doce" naturally
 * instead of depending on each engine's number handling.
 * Glucose values (mg/dl) and deltas always fit this range.
 */
object SpanishNumbers {

    private val units = listOf(
        "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
        "diez", "once", "doce", "trece", "catorce", "quince",
        "dieciséis", "diecisiete", "dieciocho", "diecinueve",
        "veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro",
        "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve",
    )

    private val tens = listOf(
        "", "", "", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa",
    )

    private val hundreds = listOf(
        "", "ciento", "doscientos", "trescientos", "cuatrocientos",
        "quinientos", "seiscientos", "setecientos", "ochocientos", "novecientos",
    )

    fun toWords(value: Int): String {
        require(value in 0..999) { "Only 0..999 supported, got $value" }
        if (value == 100) return "cien"
        val h = value / 100
        val rest = value % 100
        if (h == 0) return belowHundred(rest)
        return if (rest == 0) hundreds[h] else "${hundreds[h]} ${belowHundred(rest)}"
    }

    private fun belowHundred(value: Int): String {
        if (value < 30) return units[value]
        val t = value / 10
        val u = value % 10
        return if (u == 0) tens[t] else "${tens[t]} y ${units[u]}"
    }
}
