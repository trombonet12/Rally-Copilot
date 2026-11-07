package app.trombito.rallycopilot.motor.voz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltroAvisosTest {
    private val radar = Frase("radar-r1", "radar, ochenta", TipoFrase.ALERTA, 500.0)
    private val radarCerca = Frase("radar-r1-cerca", "radar, ochenta", TipoFrase.ALERTA, 500.0)
    private val tramoRadar = Frase("radar-t7", "tramo de radar", TipoFrase.ALERTA, 500.0)
    private val incidencia = Frase("incidencia-i9", "obras a quinientos", TipoFrase.ALERTA, 700.0)
    private val exceso = Frase("exceso-1", "límite ochenta", TipoFrase.AVISO_VELOCIDAD, null)
    private val media = Frase("tramo-media-t7-1", "media alta", TipoFrase.AVISO_VELOCIDAD, null)
    private val nota = Frase("nota-1", "derecha cuatro", TipoFrase.NOTA, 100.0)
    private val maniobra = Frase("r0-maniobra-0-inminente", "gira a la derecha", TipoFrase.MANIOBRA, 100.0)
    private val crono = Frase("crono-1", "menos dos", TipoFrase.CRONO, null)
    private val gps = Frase("gps-perdido", "sin señal GPS", TipoFrase.ALERTA, null)

    private val todas = listOf(radar, radarCerca, tramoRadar, incidencia, exceso, media, nota, maniobra, crono, gps)

    private fun callada(filtro: FiltroAvisos) = todas.filterNot(filtro::permite)

    @Test
    fun `por defecto no calla nada`() {
        assertTrue(FiltroAvisos().todoActivo)
        assertEquals(emptyList<Frase>(), callada(FiltroAvisos()))
    }

    @Test
    fun `sin radares calla solo los avisos de radar`() {
        val filtro = FiltroAvisos(avisarRadares = false)
        assertFalse(filtro.todoActivo)
        assertEquals(listOf(radar, radarCerca, tramoRadar), callada(filtro))
    }

    @Test
    fun `sin incidencias calla solo las incidencias`() {
        assertEquals(listOf(incidencia), callada(FiltroAvisos(avisarIncidencias = false)))
    }

    @Test
    fun `sin velocidad calla el exceso y la media del tramo y nada mas`() {
        assertEquals(listOf(exceso, media), callada(FiltroAvisos(avisarVelocidad = false)))
    }

    @Test
    fun `con todo apagado notas maniobras crono y otras alertas siguen sonando`() {
        val filtro = FiltroAvisos(avisarRadares = false, avisarIncidencias = false, avisarVelocidad = false)
        assertEquals(listOf(nota, maniobra, crono, gps), todas.filter(filtro::permite))
    }
}
