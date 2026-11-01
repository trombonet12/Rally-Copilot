package app.trombito.rallycopilot.ui.mapa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibilidadBarraDestinoTest {
    @Test
    fun `sin notas la barra se ve siempre`() {
        assertTrue(barraDestinoVisible(hayNotas = false, reclamada = false, retenida = false))
        assertTrue(barraDestinoVisible(hayNotas = false, reclamada = true, retenida = true))
    }

    @Test
    fun `con notas la barra se esconde salvo que se haya tocado el mapa o se este usando`() {
        assertFalse(barraDestinoVisible(hayNotas = true, reclamada = false, retenida = false))
        assertTrue(barraDestinoVisible(hayNotas = true, reclamada = true, retenida = false))
        assertTrue(barraDestinoVisible(hayNotas = true, reclamada = false, retenida = true))
    }

    @Test
    fun `foco, texto, resultados o vista previa retienen la barra`() {
        assertFalse(barraDestinoRetenida(false, false, false, false))
        assertTrue(barraDestinoRetenida(true, false, false, false))
        assertTrue(barraDestinoRetenida(false, true, false, false))
        assertTrue(barraDestinoRetenida(false, false, true, false))
        assertTrue(barraDestinoRetenida(false, false, false, true))
    }

    @Test
    fun `la cuenta atras solo corre con notas, barra pedida y sin uso`() {
        assertTrue(barraDestinoCuentaAtras(hayNotas = true, reclamada = true, retenida = false))
        assertFalse(barraDestinoCuentaAtras(hayNotas = false, reclamada = true, retenida = false))
        assertFalse(barraDestinoCuentaAtras(hayNotas = true, reclamada = false, retenida = false))
        assertFalse(barraDestinoCuentaAtras(hayNotas = true, reclamada = true, retenida = true))
    }

    @Test
    fun `tocar pide la barra y ocultar la esconde`() {
        val estado = EstadoBarraDestino()
        assertFalse(estado.reclamada)
        estado.tocar()
        estado.tocar()
        assertTrue(estado.reclamada)
        assertEquals(2, estado.toques)
        estado.ocultar()
        assertFalse(estado.reclamada)
    }

    @Test
    fun `el retardo para esconder son 8 segundos`() {
        assertEquals(8_000L, RETARDO_OCULTAR_BARRA_MS)
    }
}
