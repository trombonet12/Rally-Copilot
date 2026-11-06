package app.trombito.rallycopilot.motor.vias

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Columnas nuevas (junction, bridge, tunnel, layer) del TSV de vías y su compatibilidad con las líneas de 9 campos. */
class LectorViasNivelesTest {
    private val cabecera = "#vias-mallorca v1"
    private val coords = "39600000,2900000;39601000,2901000"

    private fun linea(vararg campos: String) = campos.joinToString("\t")

    @Test
    fun `lee junction bridge tunnel y layer de las lineas nuevas de 13 campos`() {
        val puente = linea("9", "motorway", "", "", "", "yes", "Ma-13", "", "", "yes", "", "1", coords)
        val rotonda = linea("10", "residential", "", "", "", "yes", "", "", "roundabout", "", "", "", coords)
        val tunel = linea("11", "primary", "", "", "", "", "", "", "", "", "yes", "-1", coords)
        val antigua = linea("123", "primary", "80", "90", "70", "yes", "Ma-13", "Carretera", coords)
        val r = LectorVias.leer(sequenceOf(cabecera, puente, rotonda, tunel, antigua))
        assertEquals(0, r.descartadas)
        assertEquals("Ma-13", r.vias[0].ref)
        assertEquals("yes", r.vias[0].bridge)
        assertEquals(1, r.vias[0].layer)
        assertEquals(1, r.vias[0].nivel)
        assertEquals("roundabout", r.vias[1].junction)
        assertTrue(r.vias[1].esRotonda)
        assertEquals(0, r.vias[1].nivel)
        assertEquals("yes", r.vias[2].tunnel)
        assertEquals(-1, r.vias[2].nivel)
        // La línea antigua de 9 campos se sigue leyendo, sin esos datos.
        assertEquals("Carretera", r.vias[3].nombre)
        assertNull(r.vias[3].junction)
        assertNull(r.vias[3].bridge)
        assertNull(r.vias[3].tunnel)
        assertNull(r.vias[3].layer)
        assertEquals(0, r.vias[3].nivel)
        assertEquals(2, r.vias[3].puntos.size)
    }

    @Test
    fun `el nivel sale del layer o del puente o tunel y una alcantarilla no cuenta`() {
        fun nivel(
            bridge: String? = null,
            tunnel: String? = null,
            layer: Int? = null,
        ) = Via(
            1,
            "road",
            null,
            null,
            null,
            SentidoUnico.NO,
            null,
            null,
            emptyList(),
            null,
            bridge,
            tunnel,
            layer,
        ).nivel
        assertEquals(0, nivel())
        assertEquals(1, nivel(bridge = "viaduct"))
        assertEquals(0, nivel(bridge = "no"))
        assertEquals(-1, nivel(tunnel = "yes"))
        assertEquals(0, nivel(tunnel = "culvert"))
        assertEquals(2, nivel(bridge = "yes", layer = 2))
        assertEquals(-1, nivel(layer = -1))
    }
}
