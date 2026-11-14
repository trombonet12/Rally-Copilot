package app.trombito.rallycopilot.ui.mapa

import androidx.compose.ui.graphics.toArgb
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.Ruta
import app.trombito.rallycopilot.ui.miscarreteras.NivelUso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.util.Locale

/** La capa de calor: cuándo se ve, qué lleva cada vía en el GeoJSON y la escala de color de la velocidad. */
class CapaCalorTest {
    private val recta = listOf(PuntoGeo(39.7, 2.9), PuntoGeo(39.71, 2.91))

    private fun tramo(
        id: Long,
        pasadas: Int,
        kmh: Double? = null,
        ref: String? = "Ma-10",
        nombre: String? = null,
    ) = TramoCalor(id, nombre, ref, recta, pasadas, km = 12.5, velMediaKmh = kmh)

    private val libre = EstadoNavegacion.Libre(desdeMs = 0L, distanciaM = 0.0)

    @Test
    fun `se ve en reposo y parado con la capa encendida`() {
        assertTrue(calorVisible(activo = true, EstadoNavegacion.Inactiva, enMarcha = false))
        assertTrue(calorVisible(activo = true, libre, enMarcha = false))
    }

    @Test
    fun `apagada no se ve nunca`() {
        assertFalse(calorVisible(activo = false, EstadoNavegacion.Inactiva, enMarcha = false))
    }

    @Test
    fun `en marcha se oculta tambien en modo libre`() {
        assertFalse(calorVisible(activo = true, libre, enMarcha = true))
        assertFalse(calorVisible(activo = true, EstadoNavegacion.Inactiva, enMarcha = true))
    }

    @Test
    fun `guiando no se ve aunque se este parado`() {
        val destino = Destino("Sóller", PuntoGeo(39.7, 2.7))
        val ruta = Ruta(listOf(PuntoGeo(39.6, 2.6), destino.punto), emptyList(), 12_340.0, 900.0)
        val guiando = EstadoNavegacion.Guiando(destino, PerfilRuta.SINUOSA, ruta, null, recalculando = false)
        assertFalse(calorVisible(activo = true, guiando, enMarcha = false))
        assertFalse(calorVisible(activo = true, EstadoNavegacion.Llegada(destino), enMarcha = false))
        assertFalse(calorVisible(activo = true, EstadoNavegacion.Error("sin ruta"), enMarcha = false))
    }

    @Test
    fun `en modo uso cada via lleva el color y el ancho de su nivel`() {
        val coleccion = calorGeoJson(listOf(tramo(1, 1), tramo(2, 12)), ModoCalor.USO)
        val (una, muchas) = coleccion.features().orEmpty()
        assertEquals("#9C7430", una.getStringProperty(PROPIEDAD_CALOR_COLOR))
        assertEquals(NivelUso.UNA.grosorPx, una.getNumberProperty(PROPIEDAD_CALOR_ANCHO).toFloat(), 0f)
        assertEquals(
            String.format(Locale.ROOT, "#%06X", NivelUso.MUCHAS.color.toArgb() and 0xFFFFFF),
            muchas.getStringProperty(PROPIEDAD_CALOR_COLOR),
        )
        assertEquals(NivelUso.MUCHAS.grosorPx, muchas.getNumberProperty(PROPIEDAD_CALOR_ANCHO).toFloat(), 0f)
    }

    @Test
    fun `en modo velocidad el color sale de la media y sin dato va en gris fino`() {
        val coleccion = calorGeoJson(listOf(tramo(1, 3, kmh = 30.0), tramo(2, 4, kmh = null)), ModoCalor.VELOCIDAD)
        val (conMedia, sinMedia) = coleccion.features().orEmpty()
        assertEquals(colorVelocidadCalor(30.0), conMedia.getStringProperty(PROPIEDAD_CALOR_COLOR))
        assertEquals(ANCHO_CALOR_VELOCIDAD, conMedia.getNumberProperty(PROPIEDAD_CALOR_ANCHO).toFloat(), 0f)
        assertEquals(COLOR_CALOR_SIN_DATO, sinMedia.getStringProperty(PROPIEDAD_CALOR_COLOR))
        assertEquals(ANCHO_CALOR_SIN_DATO, sinMedia.getNumberProperty(PROPIEDAD_CALOR_ANCHO).toFloat(), 0f)
    }

    @Test
    fun `la escala de velocidad es continua y se queda en los extremos`() {
        assertEquals(0f, fraccionVelocidadCalor(0.0), 0f)
        assertEquals(0f, fraccionVelocidadCalor(VELOCIDAD_CALOR_LENTA_KMH), 0f)
        assertEquals(0.5f, fraccionVelocidadCalor(65.0), 1e-6f)
        assertEquals(1f, fraccionVelocidadCalor(VELOCIDAD_CALOR_RAPIDA_KMH), 0f)
        assertEquals(1f, fraccionVelocidadCalor(180.0), 0f)
        val lento = colorVelocidadCalor(20.0)
        val medio = colorVelocidadCalor(60.0)
        val rapido = colorVelocidadCalor(110.0)
        assertEquals(3, setOf(lento, medio, rapido).size)
        assertNotEquals(colorVelocidadCalor(60.0), colorVelocidadCalor(61.0))
        assertTrue(lento.matches(Regex("#[0-9A-F]{6}")))
    }

    @Test
    fun `cada via lleva los datos de su ficha y las mas repetidas se dibujan al final`() {
        val coleccion =
            calorGeoJson(
                listOf(tramo(1, 9, kmh = 72.4, nombre = "Camí vell"), tramo(2, 2, ref = null)),
                ModoCalor.USO,
            )
        val features = coleccion.features().orEmpty()
        assertEquals(listOf(2, 9), features.map { it.getNumberProperty(PROPIEDAD_CALOR_ORDEN).toInt() })
        val ficha = fichaDeFeature(features.last())
        assertEquals(FichaCalor("Ma-10", "Camí vell", 9, 12.5, 72.4), ficha)
        // Sin ref, sin nombre y sin velocidad: los datos que faltan quedan en null.
        assertEquals(FichaCalor(null, null, 2, 12.5, null), fichaDeFeature(features.first()))
    }

    @Test
    fun `una via de menos de dos puntos no se pinta`() {
        val corta = tramo(1, 5).copy(puntos = listOf(PuntoGeo(39.7, 2.9)))
        assertTrue(calorGeoJson(listOf(corta), ModoCalor.USO).features().orEmpty().isEmpty())
    }

    @Test
    fun `la geometria es una linea con longitud y latitud en ese orden`() {
        val feature = calorGeoJson(listOf(tramo(1, 1)), ModoCalor.USO).features().orEmpty().single()
        val linea = feature.geometry() as LineString
        assertEquals(2.9, linea.coordinates().first().longitude(), 0.0)
        assertEquals(39.7, linea.coordinates().first().latitude(), 0.0)
    }

    @Test
    fun `un feature sin datos de ficha no abre ficha`() {
        val vacio = Feature.fromGeometry(Point.fromLngLat(2.9, 39.7))
        assertNull(fichaDeFeature(vacio))
    }
}
