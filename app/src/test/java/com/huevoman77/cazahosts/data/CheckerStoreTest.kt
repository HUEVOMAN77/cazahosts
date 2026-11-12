package com.huevoman77.cazahosts.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckerStoreTest {

    @Test
    fun descartaLasIpsRepetidasDeLaMismaLista() {
        val resultado = ipsParaVigilar(emptyList(), listOf("10.0.0.5", "10.0.0.5", "10.0.0.6"))
        assertEquals(listOf("10.0.0.5", "10.0.0.6"), resultado)
    }

    @Test
    fun descartaLasIpsQueYaEstanVigiladas() {
        val resultado = ipsParaVigilar(listOf("10.0.0.5"), listOf("10.0.0.5", "10.0.0.7"))
        assertEquals(listOf("10.0.0.7"), resultado)
    }

    @Test
    fun limpiaEspaciosYDescartaLasVacias() {
        val resultado = ipsParaVigilar(emptyList(), listOf(" 10.0.0.5 ", "", "   "))
        assertEquals(listOf("10.0.0.5"), resultado)
    }
}
