package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.motor.alertas.TipoIncidencia
import app.trombito.rallycopilot.ui.alertas.MarcadorIncidencia
import app.trombito.rallycopilot.ui.alertas.imagenMarcador
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Point

class CapaIncidenciasTest {
    private fun marcador(
        id: String,
        tipo: TipoIncidencia,
        lat: Double = 39.7,
        lon: Double = 2.9,
    ) = MarcadorIncidencia(id, tipo, lat, lon, "descripción", "Ma-10", enRuta = false)

    @Test
    fun `sin marcadores la fuente esta vacia`() {
        assertTrue(featuresIncidencias(emptyList()).features().orEmpty().isEmpty())
    }

    @Test
    fun `cada incidencia es un punto con su id y el icono de su tipo`() {
        val coleccion =
            featuresIncidencias(
                listOf(
                    marcador("a", TipoIncidencia.OBRAS, 39.7, 2.9),
                    marcador("b", TipoIncidencia.METEO, 39.8, 3.0),
                ),
            )
        val features = coleccion.features().orEmpty()
        assertEquals(listOf("a", "b"), features.map { it.getStringProperty(PROPIEDAD_ID) })
        assertEquals(
            listOf("incidencia-obras", "incidencia-meteo"),
            features.map { it.getStringProperty(PROPIEDAD_ICONO) },
        )
        val punto = features[1].geometry() as Point
        assertEquals(3.0, punto.longitude(), 0.0)
        assertEquals(39.8, punto.latitude(), 0.0)
    }

    @Test
    fun `hay una imagen distinta por cada tipo de incidencia`() {
        val nombres =
            TipoIncidencia.entries.map { imagenMarcador(it) }
        assertEquals(TipoIncidencia.entries.size, nombres.toSet().size)
    }

    // Proyección de prueba: cada marcador "a", "b"... en una x conocida y la misma y.
    private val posiciones = mapOf("a" to (100f to 500f), "b" to (180f to 500f), "lejos" to (900f to 1800f))

    private fun elegir(
        x: Float,
        y: Float,
        margen: Float = 72f,
    ): String? =
        incidenciaMasCercana(
            posiciones.keys.map { marcador(it, TipoIncidencia.OBRAS) },
            x,
            y,
            margen,
        ) { posiciones[it.id] }?.id

    @Test
    fun `un toque sobre la insignia elige su incidencia aunque la capa no la devuelva`() {
        assertEquals("a", elegir(110f, 510f))
    }

    @Test
    fun `entre dos incidencias al alcance gana la mas cercana al dedo`() {
        assertEquals("b", elegir(150f, 500f))
    }

    @Test
    fun `fuera del margen no hay incidencia`() {
        assertEquals(null, elegir(500f, 500f))
    }

    @Test
    fun `un marcador sin proyeccion se ignora`() {
        val sinProyeccion = incidenciaMasCercana(listOf(marcador("x", TipoIncidencia.OBRAS)), 0f, 0f, 72f) { null }
        assertEquals(null, sinProyeccion)
    }
}
