package com.glykos.core.domain.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SpanishNumbersTest {

    @ParameterizedTest
    @CsvSource(
        "0, cero",
        "1, uno",
        "8, ocho",
        "15, quince",
        "16, dieciséis",
        "19, diecinueve",
        "20, veinte",
        "21, veintiuno",
        "22, veintidós",
        "23, veintitrés",
        "26, veintiséis",
        "30, treinta",
        "31, treinta y uno",
        "54, cincuenta y cuatro",
        "70, setenta",
        "99, noventa y nueve",
        "100, cien",
        "101, ciento uno",
        "112, ciento doce",
        "180, ciento ochenta",
        "199, ciento noventa y nueve",
        "200, doscientos",
        "250, doscientos cincuenta",
        "315, trescientos quince",
        "422, cuatrocientos veintidós",
        "500, quinientos",
        "571, quinientos setenta y uno",
        "700, setecientos",
        "900, novecientos",
        "999, novecientos noventa y nueve",
    )
    fun `converts integers to Spanish words`(value: Int, expected: String) {
        assertEquals(expected, SpanishNumbers.toWords(value))
    }

    @Test
    fun `rejects negative numbers`() {
        assertThrows<IllegalArgumentException> { SpanishNumbers.toWords(-1) }
    }

    @Test
    fun `rejects numbers above 999`() {
        assertThrows<IllegalArgumentException> { SpanishNumbers.toWords(1000) }
    }
}
