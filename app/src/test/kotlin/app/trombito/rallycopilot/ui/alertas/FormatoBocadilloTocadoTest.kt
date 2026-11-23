package app.trombito.rallycopilot.ui.alertas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.motor.alertas.TipoIncidencia
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FormatoBocadilloTocadoTest {
    private val recursos = ApplicationProvider.getApplicationContext<Context>().resources

    private fun titulo(c: ContenidoBocadillo) = textoBocadillo(recursos, c)

    private fun detalle(c: ContenidoBocadillo) = lineasDetalleBocadillo(recursos, c)

    @Test
    fun `el radar fijo dice su limite y el sentido que vigila`() {
        val c = ContenidoBocadillo.RadarTocado(ClaseRadar.FIJO, 80, 50.0)
        assertEquals("Radar fijo · 80 km/h", titulo(c))
        assertEquals(listOf("Sentido noreste"), detalle(c))
    }

    @Test
    fun `sin limite el titulo es solo la clase y sin rumbo vigila los dos sentidos`() {
        val c = ContenidoBocadillo.RadarTocado(ClaseRadar.FIJO, null, null)
        assertEquals("Radar fijo", titulo(c))
        assertEquals(listOf("Ambos sentidos"), detalle(c))
    }

    @Test
    fun `el radar movil avisa de que la zona es aproximada`() {
        val c = ContenidoBocadillo.RadarTocado(ClaseRadar.MOVIL, 90, 200.0)
        assertEquals("Radar móvil · 90 km/h", titulo(c))
        assertEquals(listOf("Ubicación aproximada · sentido sur"), detalle(c))
    }

    @Test
    fun `los ocho rumbos de la brujula`() {
        val esperados =
            listOf("norte", "noreste", "este", "sureste", "sur", "suroeste", "oeste", "noroeste", "norte")
        listOf(0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0, 359.0).forEachIndexed { i, grados ->
            assertEquals("$grados", esperados[i], nombreRumbo(recursos, grados))
        }
        assertEquals("este", nombreRumbo(recursos, 80.0))
        assertEquals("norte", nombreRumbo(recursos, 22.0))
        assertEquals("noreste", nombreRumbo(recursos, 23.0))
    }

    @Test
    fun `el radar de tramo da su longitud y el tiempo minimo para no pasarse de la media`() {
        val c = ContenidoBocadillo.TramoRadarTocado(80, 3_200.0)
        assertEquals("Radar de tramo · 80 km/h", titulo(c))
        assertEquals(listOf("3,2 km · mínimo 2:24 para ir a 80"), detalle(c))
    }

    @Test
    fun `el radar de tramo sin limite solo da la longitud y sin longitud nada`() {
        assertEquals(listOf("850 m"), detalle(ContenidoBocadillo.TramoRadarTocado(null, 850.0)))
        assertEquals(emptyList<String>(), detalle(ContenidoBocadillo.TramoRadarTocado(80, 0.0)))
        assertEquals("Radar de tramo", titulo(ContenidoBocadillo.TramoRadarTocado(null, 850.0)))
    }

    @Test
    fun `el tiempo minimo redondea hacia arriba al segundo`() {
        assertEquals("2:24", tiempoMinimo(3_200.0, 80))
        assertEquals("0:45", tiempoMinimo(1_000.0, 80))
        assertEquals("0:46", tiempoMinimo(1_001.0, 80))
        assertEquals("1:05:00", tiempoMinimo(65_000.0, 60))
    }

    @Test
    fun `la bandera lleva el nombre del tramo y tu mejor tiempo si hay`() {
        val salida = ContenidoBocadillo.Bandera(esMeta = false, nombre = "Sóller – Deià", mejorMs = 239_200L)
        assertEquals("Salida: Sóller – Deià", titulo(salida))
        assertEquals(listOf("Tu mejor: 3:59,2"), detalle(salida))
        val meta = ContenidoBocadillo.Bandera(esMeta = true, nombre = "Sóller – Deià", mejorMs = null)
        assertEquals("Meta: Sóller – Deià", titulo(meta))
        assertEquals(emptyList<String>(), detalle(meta))
    }

    @Test
    fun `la bandera sin nombre se llama del tramo`() {
        assertEquals("Salida del tramo", titulo(ContenidoBocadillo.Bandera(false, null, null)))
        assertEquals("Meta del tramo", titulo(ContenidoBocadillo.Bandera(true, "  ", null)))
    }

    @Test
    fun `el tiempo de crono no lleva cero a la izquierda en los minutos`() {
        assertEquals("3:59,2", tiempoCorto(239_200L))
        assertEquals("0:45,3", tiempoCorto(45_300L))
        assertEquals("12:00,0", tiempoCorto(720_000L))
    }

    @Test
    fun `la nota del mapa es su frase y no lleva detalle`() {
        val c = ContenidoBocadillo.NotaDelMapa("Izquierda 3 larga · cuidado", 0)
        assertEquals("Izquierda 3 larga · cuidado", titulo(c))
        assertEquals(emptyList<String>(), detalle(c))
    }

    @Test
    fun `la incidencia tocada acaba con de cuando son los datos de la DGT`() {
        val c =
            ContenidoBocadillo.Incidencia(
                TipoIncidencia.OBRAS,
                "Ma-13",
                DetalleIncidencia(pkDesde = 56.5, comentario = "Desvío por Inca"),
                completo = true,
                antiguedadMs = 5 * 60_000L,
            )
        assertEquals(listOf("Ma-13 km 56,5", "Desvío por Inca", "DGT hace 5 min"), detalle(c))
        assertEquals(listOf("Ma-13 km 56,5", "Desvío por Inca"), detalle(c.copy(antiguedadMs = null)))
        val dosHoras = detalle(c.copy(antiguedadMs = 2 * 3_600_000L))
        assertEquals(listOf("Ma-13 km 56,5", "Desvío por Inca", "DGT hace 2 h"), dosHoras)
        // La que se acerca no lleva la línea de la DGT.
        assertEquals(listOf("Ma-13 km 56,5"), detalle(c.copy(completo = false)))
    }

    @Test
    fun `cada contenido tiene su icono`() {
        val contenidos =
            listOf(
                ContenidoBocadillo.RadarTocado(ClaseRadar.FIJO, 80, null),
                ContenidoBocadillo.RadarTocado(ClaseRadar.MOVIL, 80, null),
                ContenidoBocadillo.TramoRadarTocado(80, 1.0),
                ContenidoBocadillo.Bandera(false, null, null),
            )
        assertEquals(contenidos.size, contenidos.map { iconoBocadillo(it) }.toSet().size)
        assertEquals(42, iconoBocadillo(ContenidoBocadillo.NotaDelMapa("x", 42)))
    }
}
