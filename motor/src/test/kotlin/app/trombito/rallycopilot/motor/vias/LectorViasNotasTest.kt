package app.trombito.rallycopilot.motor.vias

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.test.assertFailsWith

class LectorViasNotasTest {
    private val cabecera = "#vias-notas v1"

    private fun linea(vararg campos: String) = campos.joinToString("\t")

    @Test
    fun `lee etiquetas nodos y perfiles bien formados`() {
        val datos =
            LectorViasNotas.leer(
                sequenceOf(
                    cabecera,
                    linea("V", "10", "yes", "", "roundabout", "2", "6.5", "", "asphalt"),
                    linea("V", "11", "", "yes", "", "", "", "ice", ""),
                    linea("N", "39600000,2900000", "bump"),
                    linea("N", "39610000,2910000", "hazard:rockfall"),
                    linea("P", "10", "5", "1000,5,-10,0"),
                ),
            )
        assertEquals(0, datos.descartadas)
        val v = datos.etiquetas.getValue(10L)
        assertEquals("yes", v.bridge)
        assertNull(v.tunnel)
        assertEquals("roundabout", v.junction)
        assertEquals(2, v.lanes)
        assertEquals(6.5, v.width!!, 1e-9)
        assertNull(v.hazard)
        assertEquals("asphalt", v.surface)
        val w = datos.etiquetas.getValue(11L)
        assertEquals("yes", w.tunnel)
        assertEquals("ice", w.hazard)
        assertNull(w.lanes)
        assertEquals(2, datos.nodos.size)
        assertEquals(39.6, datos.nodos[0].punto.lat, 1e-9)
        assertEquals(2.9, datos.nodos[0].punto.lon, 1e-9)
        assertEquals("bump", datos.nodos[0].tipo)
        assertEquals("hazard:rockfall", datos.nodos[1].tipo)
        val perfil = datos.perfiles.getValue(10L)
        assertEquals(5.0, perfil.pasoM, 0.0)
        assertEquals(listOf(100.0, 100.5, 99.5, 99.5), perfil.alturasM)
    }

    @Test
    fun `cuenta y salta las lineas malformadas`() {
        val datos =
            LectorViasNotas.leer(
                sequenceOf(
                    cabecera,
                    linea("V", "abc", "", "", "", "", "", "", ""),
                    linea("V", "1", "", ""),
                    linea("N", "39600000", "bump"),
                    linea("N", "99000000,2900000", "bump"),
                    linea("N", "39600000,2900000", ""),
                    linea("P", "1", "5", "10,x,3"),
                    linea("P", "1", "0", "10,1,1"),
                    linea("P", "1", "5", "10"),
                    linea("P", "1", "5"),
                    linea("X", "lo que sea"),
                    "",
                    linea("N", "39600000,2900000", "stop"),
                    linea("P", "2", "5", "0,10,10"),
                ),
            )
        assertEquals(10, datos.descartadas)
        assertEquals(1, datos.nodos.size)
        assertEquals(setOf(2L), datos.perfiles.keys)
        assertEquals(emptyMap<Long, EtiquetasVia>(), datos.etiquetas)
    }

    @Test
    fun `exige la cabecera y tolera BOM y fin de linea CRLF`() {
        assertFailsWith<IllegalArgumentException> { LectorViasNotas.leer(emptySequence()) }
        assertFailsWith<IllegalArgumentException> { LectorViasNotas.leer(sequenceOf("#vias-notas v2")) }
        assertFailsWith<IllegalArgumentException> { LectorViasNotas.leer(sequenceOf("#vias-mallorca v1")) }
        val datos = LectorViasNotas.leer(sequenceOf("\uFEFF$cabecera\r", "N\t39600000,2900000\tford\r"))
        assertEquals("ford", datos.nodos.single().tipo)
        assertEquals(0, datos.descartadas)
    }

    @Test
    fun `la altura interpola linealmente y la ultima muestra cae en la longitud de la via`() {
        // Muestras en 0, 5, 10 y la final en 12 m: 0 m, 1 m, 3 m, 4 m.
        val perfil = PerfilVia(5.0, listOf(0.0, 1.0, 3.0, 4.0))
        assertEquals(0.0, perfil.alturaM(0.0, 12.0), 1e-9)
        assertEquals(0.5, perfil.alturaM(2.5, 12.0), 1e-9)
        assertEquals(1.0, perfil.alturaM(5.0, 12.0), 1e-9)
        assertEquals(2.0, perfil.alturaM(7.5, 12.0), 1e-9)
        assertEquals(3.0, perfil.alturaM(10.0, 12.0), 1e-9)
        assertEquals(3.5, perfil.alturaM(11.0, 12.0), 1e-9)
        assertEquals(4.0, perfil.alturaM(12.0, 12.0), 1e-9)
        assertEquals(4.0, perfil.alturaM(99.0, 12.0), 1e-9)
        assertEquals(0.0, perfil.alturaM(-3.0, 12.0), 1e-9)
    }

    @Test
    fun `sin longitud de via las muestras se suponen separadas un paso`() {
        val perfil = PerfilVia(5.0, listOf(0.0, 1.0, 3.0))
        assertEquals(2.0, perfil.alturaM(7.5), 1e-9)
        assertEquals(3.0, perfil.alturaM(10.0), 1e-9)
    }

    @Test
    fun `un perfil necesita dos muestras`() {
        assertFailsWith<IllegalArgumentException> { PerfilVia(5.0, listOf(1.0)) }
    }
}
