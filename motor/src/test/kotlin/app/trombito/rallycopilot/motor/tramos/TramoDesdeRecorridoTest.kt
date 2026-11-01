package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.notas.TrazasDePrueba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TramoDesdeRecorridoTest {
    @Test
    fun `un recorrido de menos de un kilometro se descarta`() {
        assertNull(TramoDesdeRecorrido.crear("corto", TrazasDePrueba.recta(990.0, pasoM = 5.0)))
    }

    @Test
    fun `un recorrido de un kilometro o mas es tramo con sectores y microsectores por defecto`() {
        val tramo = TramoDesdeRecorrido.crear("largo", TrazasDePrueba.recta(2000.0, pasoM = 5.0))
        assertNotNull(tramo)
        tramo!!
        assertEquals("largo", tramo.id)
        assertEquals(2000.0, tramo.longitudM, 1.0)
        assertEquals(listOf(500.0, 1000.0, 1500.0), tramo.sectores.map { Math.round(it / 10.0) * 10.0 })
        assertEquals(1, tramo.microsectores.size)
    }

    @Test
    fun `simplifica la traza a unos 5 metros`() {
        val recorrido = TrazasDePrueba.recta(2000.0, pasoM = 5.0)
        val tramo = TramoDesdeRecorrido.crear("recta", recorrido)!!
        assertEquals(2, tramo.traza.size)
        assertEquals(recorrido.first(), tramo.traza.first())
        assertEquals(recorrido.last(), tramo.traza.last())
    }

    @Test
    fun `una curva conserva sus vertices pero con menos puntos`() {
        val metros = TrazasDePrueba.arcoEnMetros(radioM = 300.0, anguloGrados = 90.0, entradaM = 500.0, salidaM = 500.0)
        val recorrido = TrazasDePrueba.enPlano(metros)
        val tramo = TramoDesdeRecorrido.crear("curva", recorrido)!!
        assertTrue(tramo.traza.size in 4 until recorrido.size)
    }

    @Test
    fun `el ruido del gps parado no cuenta para la longitud`() {
        val parado = TrazasDePrueba.enPlano((0 until 500).map { (it % 2) * 2.0 to 0.0 })
        assertNull(TramoDesdeRecorrido.crear("quieto", parado))
    }
}
