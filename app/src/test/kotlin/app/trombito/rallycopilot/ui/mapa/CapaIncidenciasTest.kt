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
}
