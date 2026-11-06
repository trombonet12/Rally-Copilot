package app.trombito.rallycopilot.ui.alertas

import app.trombito.rallycopilot.domain.alertas.AlertasEnCurso
import app.trombito.rallycopilot.domain.alertas.IncidenciasVigentes
import app.trombito.rallycopilot.domain.alertas.PasoRadarEnCurso
import app.trombito.rallycopilot.domain.alertas.RadarProximo
import app.trombito.rallycopilot.domain.alertas.RadaresEnMapa
import app.trombito.rallycopilot.domain.alertas.TramoActivo
import app.trombito.rallycopilot.domain.alertas.TramoEnMapa
import app.trombito.rallycopilot.motor.alertas.Radar
import app.trombito.rallycopilot.motor.alertas.TipoIncidencia
import app.trombito.rallycopilot.motor.alertas.TipoRadar
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BocadilloRadarTest {
    private val fijo = Radar("f1", TipoRadar.FIJO, PuntoGeo(39.60, 2.90), 0.0, 80, null, "t")
    private val movil = Radar("m1", TipoRadar.ZONA_MOVIL, PuntoGeo(39.61, 2.91), 90.0, 90, null, "t")
    private val ini = Radar("t-ini", TipoRadar.TRAMO_INICIO, PuntoGeo(39.62, 2.92), 0.0, 100, "t", "t")
    private val fin = Radar("t-fin", TipoRadar.TRAMO_FIN, PuntoGeo(39.63, 2.93), 0.0, 100, "t", "t")
    private val tramo = TramoEnMapa("t", 100, listOf(ini.punto, PuntoGeo(39.625, 2.92), fin.punto))
    private val radares = radaresUiDe(RadaresEnMapa(listOf(fijo, movil, ini, fin), listOf(tramo)))

    private fun presentar(alertas: AlertasEnCurso?) =
        presentarAlertas(alertas, IncidenciasVigentes(), AHORA_MS, null, radares)

    @Test
    fun `todos los radares salen como marcadores menos la salida de los tramos`() {
        val ui = presentar(null)
        assertEquals(listOf("f1", "m1", "t-ini"), ui.radares.marcadores.map { it.id })
        assertEquals(
            listOf(ClaseRadar.FIJO, ClaseRadar.MOVIL, ClaseRadar.TRAMO),
            ui.radares.marcadores.map { it.clase },
        )
        assertEquals(listOf(tramo.trazado), ui.radares.tramos.map { it.puntos })
        assertNull(ui.bocadillo)
    }

    @Test
    fun `el radar que se acerca saca su bocadillo anclado a su marcador`() {
        val ui = presentar(AlertasEnCurso(radarProximo = RadarProximo(TipoRadar.FIJO, 400.0, 80, "f1")))
        val bocadillo = checkNotNull(ui.bocadillo)
        assertEquals(39.60, bocadillo.lat, 0.0)
        assertEquals(2.90, bocadillo.lon, 0.0)
        assertEquals(ContenidoBocadillo.Radar(ClaseRadar.FIJO, 80, sentidoContrario = false), bocadillo.contenido)
    }

    @Test
    fun `un fijo del sentido contrario sale en el banner y el bocadillo sin limite en el banner`() {
        val proximo = RadarProximo(TipoRadar.FIJO, 400.0, 80, "f1", sentidoContrario = true)
        val ui = presentar(AlertasEnCurso(radarProximo = proximo))
        assertEquals(AvisoAlerta.Radar(ClaseRadar.FIJO, 400, null, sentidoContrario = true), ui.aviso)
        assertEquals(true, (ui.bocadillo?.contenido as ContenidoBocadillo.Radar).sentidoContrario)
    }

    @Test
    fun `el paso por un radar fijo manda sobre el que se acerca`() {
        val ui =
            presentar(
                AlertasEnCurso(
                    radarProximo = RadarProximo(TipoRadar.ZONA_MOVIL, 300.0, 90, "m1"),
                    pasoRadar = PasoRadarEnCurso("f1", 86.6, 80),
                ),
            )
        val bocadillo = checkNotNull(ui.bocadillo)
        assertEquals(ContenidoBocadillo.Paso(87, 80), bocadillo.contenido)
        assertEquals(39.60, bocadillo.lat, 0.0)
    }

    @Test
    fun `la velocidad de paso es verde dentro del limite, roja por encima y neutra sin limite`() {
        assertEquals(false, ContenidoBocadillo.Paso(80, 80).supera)
        assertEquals(false, ContenidoBocadillo.Paso(60, 80).supera)
        assertEquals(true, ContenidoBocadillo.Paso(81, 80).supera)
        assertNull(ContenidoBocadillo.Paso(120, null).supera)
    }

    @Test
    fun `dentro de un tramo el bocadillo va en su salida con la media`() {
        val activo = TramoActivo(mediaKmh = 92.4, limiteKmh = 100, superaLimite = false, terminado = false, id = "t")
        val bocadillo = checkNotNull(presentar(AlertasEnCurso(tramo = activo)).bocadillo)
        assertEquals(ContenidoBocadillo.Tramo(100, 92, supera = false), bocadillo.contenido)
        assertEquals(fin.punto.lat, bocadillo.lat, 0.0)
        assertEquals(fin.punto.lon, bocadillo.lon, 0.0)
    }

    @Test
    fun `sin marcador al que anclarlo no hay bocadillo`() {
        val ui = presentar(AlertasEnCurso(radarProximo = RadarProximo(TipoRadar.FIJO, 400.0, 80, "desconocido")))
        assertNull(ui.bocadillo)
        assertNull(presentar(AlertasEnCurso(pasoRadar = PasoRadarEnCurso("nada", 90.0, 80))).bocadillo)
    }

    @Test
    fun `una incidencia de la ruta cercana tambien saca bocadillo`() {
        val obra = incidencia("i1", TipoIncidencia.OBRAS, listOf(PuntoGeo(39.7, 2.8)))
        val alertas = AlertasEnCurso(incidencias = listOf(cercana(obra, enRuta = true, distanciaM = 500.0)))
        val bocadillo = checkNotNull(presentar(alertas).bocadillo)
        assertEquals(ContenidoBocadillo.Incidencia(TipoIncidencia.OBRAS), bocadillo.contenido)
        assertEquals(39.7, bocadillo.lat, 0.0)
        val lejos = AlertasEnCurso(incidencias = listOf(cercana(obra, enRuta = true, distanciaM = 900.0)))
        assertNull(presentar(lejos).bocadillo)
    }
}
