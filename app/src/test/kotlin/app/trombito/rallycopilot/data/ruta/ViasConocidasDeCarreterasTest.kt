package app.trombito.rallycopilot.data.ruta

import app.trombito.rallycopilot.motor.analisis.AgregadoCarreteras
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import org.junit.Assert.assertEquals
import org.junit.Test

class ViasConocidasDeCarreterasTest {
    private val origen = PuntoGeo(39.6, 2.6)
    private val medio = PuntoGeo(39.6, 2.601) // unos 86 m al este
    private val fin = PuntoGeo(39.6, 2.602)

    private fun via(id: Long) =
        Via(
            id = id,
            clase = "secondary",
            maxspeed = null,
            maxspeedAdelante = null,
            maxspeedAtras = null,
            sentidoUnico = SentidoUnico.NO,
            ref = null,
            nombre = null,
            puntos = listOf(origen, fin),
        )

    private fun viaSegunLongitud(porLongitud: (PuntoGeo) -> Long?) =
        AgregadoCarreteras.ViaDe { punto, _ -> porLongitud(punto)?.let { via(it) } }

    @Test
    fun `sin vias conocidas no suma metros`() {
        val m = ViasConocidasDeCarreteras.medir(listOf(origen, fin), emptySet(), viaSegunLongitud { 1L })
        assertEquals(0.0, m, 0.0)
    }

    @Test
    fun `suma solo los metros sobre vias conocidas`() {
        // La primera mitad (hasta lon 2,601) es la vía 1 (conocida); la segunda, la vía 2.
        val viaDe = viaSegunLongitud { if (it.lon < medio.lon) 1L else 2L }
        val total = origen.distanciaA(medio) + medio.distanciaA(fin)
        val m = ViasConocidasDeCarreteras.medir(listOf(origen, medio, fin), setOf(1L), viaDe)
        assertEquals(origen.distanciaA(medio), m, 1.0)
        val todo = ViasConocidasDeCarreteras.medir(listOf(origen, medio, fin), setOf(1L, 2L), viaDe)
        assertEquals(total, todo, 1e-6)
    }

    @Test
    fun `una ruta sin puntos o sin via cercana da cero`() {
        val sinVia = AgregadoCarreteras.ViaDe { _, _ -> null }
        assertEquals(0.0, ViasConocidasDeCarreteras.medir(emptyList(), setOf(1L), sinVia), 0.0)
        assertEquals(0.0, ViasConocidasDeCarreteras.medir(listOf(origen, fin), setOf(1L), sinVia), 0.0)
    }
}
