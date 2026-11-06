package app.trombito.rallycopilot.ui.mapa

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.ruta.EstadoRuta
import app.trombito.rallycopilot.motor.ruta.Ruta
import app.trombito.rallycopilot.ui.theme.DarkColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EstelaRecorridoTest {
    /** Puntos hacia el este separados unos [pasoM] metros (a 39,7 N un milésimo de grado de longitud son ~85,5 m). */
    private fun hacia(
        n: Int,
        pasoM: Double,
    ) = (0 until n).map { PuntoGeo(39.70, 2.90 + it * pasoM / METROS_POR_GRADO_LON) }

    @Test
    fun `descarta los puntos a menos de 5 m del ultimo guardado`() {
        val estela = EstelaRecorrido()
        assertTrue(estela.anadir(PuntoGeo(39.70, 2.90)))
        assertFalse(estela.anadir(PuntoGeo(39.70, 2.90 + 2.0 / METROS_POR_GRADO_LON)))
        assertTrue(estela.anadir(PuntoGeo(39.70, 2.90 + 6.0 / METROS_POR_GRADO_LON)))
        assertEquals(2, estela.puntos().size)
    }

    @Test
    fun `la distancia minima se mide desde el ultimo guardado y no desde el descartado`() {
        val estela = EstelaRecorrido()
        hacia(8, pasoM = 2.0).forEach(estela::anadir)
        // Paso de 2 m: se guarda el 1.º, el 4.º (6 m) y el 7.º (12 m).
        assertEquals(3, estela.puntos().size)
    }

    @Test
    fun `conserva solo los ultimos puntos del limite`() {
        val estela = EstelaRecorrido(maximo = 4)
        val todos = hacia(10, pasoM = 10.0)
        todos.forEach(estela::anadir)
        assertEquals(todos.takeLast(4), estela.puntos())
    }

    @Test
    fun `el limite por defecto es de 5000 puntos`() {
        val estela = EstelaRecorrido()
        hacia(5_100, pasoM = 6.0).forEach(estela::anadir)
        assertEquals(5_000, estela.puntos().size)
    }

    @Test
    fun `limpiar vacia la estela y el siguiente punto se guarda`() {
        val estela = EstelaRecorrido()
        estela.anadir(PuntoGeo(39.70, 2.90))
        estela.limpiar()
        assertTrue(estela.puntos().isEmpty())
        assertTrue(estela.anadir(PuntoGeo(39.70, 2.90)))
    }

    @Test
    fun `la copia publicada no cambia al seguir anadiendo`() {
        val estela = EstelaRecorrido()
        estela.anadir(PuntoGeo(39.70, 2.90))
        val publicada = estela.puntos()
        estela.anadir(PuntoGeo(39.71, 2.90))
        assertEquals(1, publicada.size)
    }

    @Test
    fun `un salto de mas de 150 m abre un segmento nuevo en vez de unir con una recta`() {
        val estela = EstelaRecorrido()
        val antes = hacia(3, pasoM = 20.0)
        antes.forEach(estela::anadir)
        val despues = hacia(3, pasoM = 20.0).map { PuntoGeo(it.lat, it.lon + 400.0 / METROS_POR_GRADO_LON) }
        despues.forEach(estela::anadir)
        assertEquals(listOf(antes, despues), estela.segmentos())
        assertEquals(6, estela.puntos().size)
    }

    @Test
    fun `140 m todavia se unen`() {
        val estela = EstelaRecorrido()
        estela.anadir(PuntoGeo(39.70, 2.90))
        estela.anadir(PuntoGeo(39.70, 2.90 + 140.0 / METROS_POR_GRADO_LON))
        assertEquals(1, estela.segmentos().size)
    }

    @Test
    fun `mas de 10 s sin fijaciones corta la estela aunque el salto sea corto`() {
        val estela = EstelaRecorrido()
        estela.registrar(PuntoGeo(39.70, 2.90), ahoraMs = 0)
        estela.registrar(PuntoGeo(39.70, 2.90 + 30.0 / METROS_POR_GRADO_LON), ahoraMs = 1_000)
        estela.registrar(PuntoGeo(39.70, 2.90 + 60.0 / METROS_POR_GRADO_LON), ahoraMs = 12_500)
        estela.registrar(PuntoGeo(39.70, 2.90 + 90.0 / METROS_POR_GRADO_LON), ahoraMs = 13_500)
        assertEquals(listOf(2, 2), estela.segmentos().map { it.size })
    }

    @Test
    fun `parado mas de 10 s sin moverse no corta la estela`() {
        val estela = EstelaRecorrido()
        estela.registrar(PuntoGeo(39.70, 2.90), ahoraMs = 0)
        // Fijaciones cada segundo en el mismo sitio (no se guardan) y luego sale.
        (1..30).forEach { estela.registrar(PuntoGeo(39.70, 2.90), ahoraMs = it * 1_000L) }
        estela.registrar(PuntoGeo(39.70, 2.90 + 30.0 / METROS_POR_GRADO_LON), ahoraMs = 31_000)
        assertEquals(listOf(2), estela.segmentos().map { it.size })
    }

    @Test
    fun `el tope de puntos descarta los antiguos y no deja segmentos vacios`() {
        val estela = EstelaRecorrido(maximo = 4)
        val primero = hacia(3, pasoM = 20.0)
        primero.forEach(estela::anadir)
        val segundo = hacia(3, pasoM = 20.0).map { PuntoGeo(it.lat, it.lon + 1_000.0 / METROS_POR_GRADO_LON) }
        segundo.forEach(estela::anadir)
        assertEquals(listOf(primero.takeLast(1), segundo), estela.segmentos())
        listOf(PuntoGeo(39.7, 2.99), PuntoGeo(39.7, 2.9901)).forEach(estela::anadir)
        assertTrue(estela.segmentos().none { it.isEmpty() })
        assertEquals(4, estela.puntos().size)
    }

    @Test
    fun `el geojson de la estela lleva una linea por segmento y descarta los de un solo punto`() {
        val a = hacia(3, pasoM = 20.0)
        val b = hacia(2, pasoM = 20.0)
        val c = listOf(PuntoGeo(39.9, 2.9))
        assertEquals(2, estelaGeoJson(listOf(a, c, b)).features()?.size)
        assertEquals(0, estelaGeoJson(emptyList()).features()?.size)
    }

    private val destino = Destino("Destino", PuntoGeo(39.71, 2.90))
    private val ruta = Ruta(hacia(3, 100.0), maniobras = emptyList(), longitudM = 200.0, tiempoS = 20.0)

    private fun guiando(
        progresoM: Double? = null,
        recalculando: Boolean = false,
    ) = EstadoNavegacion.Guiando(destino, PerfilRuta.MAS_RAPIDA, ruta, progresoM?.let(::progreso), recalculando)

    @Test
    fun `la clave del recorrido distingue libre, guiado y sin conduccion`() {
        assertNull(claveRecorrido(EstadoNavegacion.Inactiva))
        assertNull(claveRecorrido(EstadoNavegacion.Error("x")))
        val libre = claveRecorrido(EstadoNavegacion.Libre(0, 0.0))
        val reconocimiento = claveRecorrido(EstadoNavegacion.Libre(0, 0.0, reconocimientoTramoId = "t1"))
        assertNotEquals(libre, reconocimiento)
        assertNotEquals(libre, claveRecorrido(guiando()))
        // Recalcular o llegar no abre un recorrido nuevo.
        assertEquals(claveRecorrido(guiando()), claveRecorrido(guiando(recalculando = true)))
        assertEquals(claveRecorrido(guiando()), claveRecorrido(EstadoNavegacion.Llegada(destino)))
    }

    @Test
    fun `puntosPorDelante con 0 m devuelve la ruta entera`() {
        val puntos = hacia(4, 100.0)
        assertEquals(puntos, puntosPorDelante(puntos, 0.0))
    }

    @Test
    fun `puntosPorDelante empieza en el punto de corte interpolado`() {
        val puntos = hacia(4, 100.0)
        val resto = puntosPorDelante(puntos, 150.0)
        assertEquals(3, resto.size)
        assertEquals(puntos[2], resto[1])
        assertEquals(150.0, resto.first().distanciaA(puntos.first()), 1.0)
        assertEquals(150.0, resto.longitudMetros(), 1.0)
    }

    @Test
    fun `puntosPorDelante pasado el final no deja nada que pintar`() {
        val puntos = hacia(3, 100.0)
        assertTrue(puntosPorDelante(puntos, 500.0).size < 2)
    }

    @Test
    fun `el recorrido de la ruta va en escalones de 20 m y solo guiando`() {
        assertEquals(0.0, recorridoDeLaRuta(EstadoNavegacion.Libre(0, 0.0)), 0.0)
        assertEquals(0.0, recorridoDeLaRuta(guiando()), 0.0)
        assertEquals(0.0, recorridoDeLaRuta(guiando(19.0)), 0.0)
        assertEquals(40.0, recorridoDeLaRuta(guiando(59.9)), 0.0)
        assertEquals(0.0, recorridoDeLaRuta(guiando(59.9, recalculando = true)), 0.0)
    }

    @Test
    fun `la ruta es roja y no del laton de la flecha, la estela es gris al 70 por ciento`() {
        val rojo = Color(COLOR_RUTA)
        assertTrue(rojo.red > 0.8f && rojo.green < 0.3f && rojo.blue < 0.3f)
        assertNotEquals(DarkColors.primary.toArgb(), COLOR_RUTA)
        val gris = Color(COLOR_ESTELA)
        assertTrue(abs(gris.red - gris.green) < 0.1f && abs(gris.green - gris.blue) < 0.1f)
        assertEquals(0.7f, OPACIDAD_ESTELA, 0f)
    }

    private fun progreso(progresoM: Double) =
        EstadoRuta(
            progresoM = progresoM,
            distanciaRestanteM = 0.0,
            tiempoRestanteS = 0.0,
            llegadaMs = 0,
            maniobraActual = null,
            distanciaAManiobraM = 0.0,
            maniobraSiguiente = null,
            distanciaALaRutaM = 0.0,
            llegado = false,
        )

    private companion object {
        const val METROS_POR_GRADO_LON = 85_500.0
    }
}
