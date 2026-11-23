package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.TramoEnCamino
import app.trombito.rallycopilot.ui.alertas.SeleccionMapa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

class CapaTramosTest {
    private fun trozo(
        id: String,
        empiezaEnSalida: Boolean,
        acabaEnMeta: Boolean,
        geometria: List<PuntoGeo> = listOf(PuntoGeo(39.70, 2.90), PuntoGeo(39.71, 2.91), PuntoGeo(39.72, 2.92)),
    ) = TramoEnCamino(
        tramoId = id,
        desdeM = 0.0,
        hastaM = 2_000.0,
        geometria = geometria,
        salida = geometria.first(),
        meta = geometria.last(),
        empiezaEnSalida = empiezaEnSalida,
        acabaEnMeta = acabaEnMeta,
        tramoDesdeM = 0.0,
        tramoHastaM = 2_000.0,
    )

    @Test
    fun `sin tramos las dos fuentes estan vacias`() {
        assertTrue(featuresLineasTramos(emptyList()).features().orEmpty().isEmpty())
        assertTrue(featuresBanderasTramos(emptyList()).features().orEmpty().isEmpty())
    }

    @Test
    fun `cada trozo es una linea con su id y los puntos en lon lat`() {
        val features = featuresLineasTramos(listOf(trozo("A", true, true))).features().orEmpty()
        assertEquals(1, features.size)
        assertEquals("A", features.single().getStringProperty(PROPIEDAD_ID))
        val linea = features.single().geometry() as LineString
        assertEquals(3, linea.coordinates().size)
        assertEquals(2.90, linea.coordinates().first().longitude(), 0.0)
        assertEquals(39.70, linea.coordinates().first().latitude(), 0.0)
    }

    @Test
    fun `un trozo de menos de dos puntos no se pinta como linea`() {
        val corto = trozo("A", true, true, geometria = listOf(PuntoGeo(39.7, 2.9)))
        assertTrue(featuresLineasTramos(listOf(corto)).features().orEmpty().isEmpty())
    }

    @Test
    fun `un trozo entero lleva bandera verde en la salida y de cuadros en la meta`() {
        val features = featuresBanderasTramos(listOf(trozo("A", true, true))).features().orEmpty()
        assertEquals(2, features.size)
        val salida = features.single { it.getStringProperty(PROPIEDAD_BANDERA) == IMAGEN_BANDERA_SALIDA }
        val meta = features.single { it.getStringProperty(PROPIEDAD_BANDERA) == IMAGEN_BANDERA_META }
        assertEquals(39.70, (salida.geometry() as Point).latitude(), 0.0)
        assertEquals(39.72, (meta.geometry() as Point).latitude(), 0.0)
        assertEquals("A", salida.getStringProperty(PROPIEDAD_ID))
    }

    @Test
    fun `solo hay bandera en el extremo que es la salida o la meta reales del tramo`() {
        val soloMeta = featuresBanderasTramos(listOf(trozo("A", false, true))).features().orEmpty()
        assertEquals(listOf(IMAGEN_BANDERA_META), soloMeta.map { it.getStringProperty(PROPIEDAD_BANDERA) })
        val soloSalida = featuresBanderasTramos(listOf(trozo("B", true, false))).features().orEmpty()
        assertEquals(listOf(IMAGEN_BANDERA_SALIDA), soloSalida.map { it.getStringProperty(PROPIEDAD_BANDERA) })
        assertTrue(featuresBanderasTramos(listOf(trozo("C", false, false))).features().orEmpty().isEmpty())
    }

    @Test
    fun `varios tramos dan una linea cada uno y las banderas de cada uno`() {
        val tramos = listOf(trozo("A", true, true), trozo("B", false, true))
        assertEquals(2, featuresLineasTramos(tramos).features().orEmpty().size)
        assertEquals(3, featuresBanderasTramos(tramos).features().orEmpty().size)
    }

    @Test
    fun `las banderas tocables son las mismas que se pintan`() {
        val entero = trozo("A", true, true)
        val recortado = trozo("B", empiezaEnSalida = false, acabaEnMeta = true)
        val banderas = banderasDe(listOf(entero, recortado))
        assertEquals(
            listOf(
                SeleccionMapa.Bandera("A", esMeta = false),
                SeleccionMapa.Bandera("A", esMeta = true),
                SeleccionMapa.Bandera("B", esMeta = true),
            ),
            banderas.map { it.seleccion },
        )
        assertEquals(39.70, banderas.first().punto.lat, 0.0)
        assertEquals(39.72, banderas.last().punto.lat, 0.0)
        assertEquals(
            featuresBanderasTramos(listOf(entero, recortado)).features().orEmpty().size,
            banderas.size,
        )
    }
}
