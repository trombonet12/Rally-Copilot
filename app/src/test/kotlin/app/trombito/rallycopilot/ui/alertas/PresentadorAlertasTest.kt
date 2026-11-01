package app.trombito.rallycopilot.ui.alertas

import app.trombito.rallycopilot.domain.alertas.AlertasEnCurso
import app.trombito.rallycopilot.domain.alertas.ErrorIncidencias
import app.trombito.rallycopilot.domain.alertas.ExcesoEnCurso
import app.trombito.rallycopilot.domain.alertas.IncidenciasVigentes
import app.trombito.rallycopilot.domain.alertas.RadarProximo
import app.trombito.rallycopilot.domain.alertas.TramoActivo
import app.trombito.rallycopilot.motor.alertas.Incidencia
import app.trombito.rallycopilot.motor.alertas.IncidenciaCercana
import app.trombito.rallycopilot.motor.alertas.TipoIncidencia
import app.trombito.rallycopilot.motor.alertas.TipoRadar
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

internal const val AHORA_MS = 1_800_000_000_000L

internal fun incidencia(
    id: String,
    tipo: TipoIncidencia = TipoIncidencia.OBRAS,
    puntos: List<PuntoGeo> = listOf(PuntoGeo(39.80, 2.80)),
    carretera: String? = "Ma-10",
    fin: Instant? = null,
) = Incidencia(id, tipo, puntos, carretera, "Obras en $carretera, calzada cortada", null, fin, null)

internal fun cercana(
    incidencia: Incidencia,
    enRuta: Boolean,
    distanciaM: Double,
) = IncidenciaCercana(incidencia, enRuta, distanciaM)

class PresentadorAlertasTest {
    private fun presentar(
        alertas: AlertasEnCurso?,
        vigentes: IncidenciasVigentes = IncidenciasVigentes(),
        seleccionada: String? = null,
    ) = presentarAlertas(alertas, vigentes, AHORA_MS, seleccionada)

    @Test
    fun `sin conduccion ni incidencias no hay nada`() {
        assertEquals(AlertasUiState(), presentar(null))
    }

    @Test
    fun `el radar fijo sale en el banner con su limite y la distancia redondeada a 10 m`() {
        val ui = presentar(AlertasEnCurso(radarProximo = RadarProximo(TipoRadar.FIJO, 487.0, 70)))
        assertEquals(AvisoAlerta.Radar(ClaseRadar.FIJO, 490, 70), ui.aviso)
    }

    @Test
    fun `cada clase de radar tiene su aviso`() {
        fun clase(tipo: TipoRadar) =
            (presentar(AlertasEnCurso(radarProximo = RadarProximo(tipo, 300.0, null))).aviso as AvisoAlerta.Radar).clase
        assertEquals(ClaseRadar.MOVIL, clase(TipoRadar.ZONA_MOVIL))
        assertEquals(ClaseRadar.TRAMO, clase(TipoRadar.TRAMO_INICIO))
        assertEquals(ClaseRadar.TRAMO, clase(TipoRadar.TRAMO_FIN))
    }

    @Test
    fun `la distancia al radar nunca es negativa`() {
        val ui = presentar(AlertasEnCurso(radarProximo = RadarProximo(TipoRadar.FIJO, -4.0, null)))
        assertEquals(0, ui.aviso?.distanciaM)
    }

    @Test
    fun `una incidencia de la ruta cercana sale en el banner con su carretera`() {
        val accidente = incidencia("a", TipoIncidencia.ACCIDENTE, carretera = "Ma-13")
        val ui = presentar(AlertasEnCurso(incidencias = listOf(cercana(accidente, true, 640.0))))
        assertEquals(AvisoAlerta.Incidencia(TipoIncidencia.ACCIDENTE, "Ma-13", 640), ui.aviso)
    }

    @Test
    fun `una incidencia de la ruta a mas de 800 m todavia no avisa pero se pinta`() {
        val obras = incidencia("o")
        val ui = presentar(AlertasEnCurso(incidencias = listOf(cercana(obras, true, 2_500.0))))
        assertNull(ui.aviso)
        assertEquals(listOf("o"), ui.marcadores.map { it.id })
    }

    @Test
    fun `una incidencia que no es de la ruta no entra en el banner`() {
        val ui = presentar(AlertasEnCurso(incidencias = listOf(cercana(incidencia("fuera"), false, 400.0))))
        assertNull(ui.aviso)
        assertFalse(ui.marcadores.single().enRuta)
    }

    @Test
    fun `de varias incidencias de la ruta avisa la mas cercana`() {
        val lejos = cercana(incidencia("lejos", TipoIncidencia.RETENCION), true, 700.0)
        val cerca = cercana(incidencia("cerca", TipoIncidencia.CORTE), true, 200.0)
        val ui = presentar(AlertasEnCurso(incidencias = listOf(lejos, cerca)))
        assertEquals(TipoIncidencia.CORTE, (ui.aviso as AvisoAlerta.Incidencia).tipo)
    }

    @Test
    fun `dentro de la incidencia la distancia es cero`() {
        val ui = presentar(AlertasEnCurso(incidencias = listOf(cercana(incidencia("o"), true, 0.0))))
        assertEquals(0, ui.aviso?.distanciaM)
    }

    @Test
    fun `el radar tiene prioridad sobre la incidencia`() {
        val alertas =
            AlertasEnCurso(
                radarProximo = RadarProximo(TipoRadar.ZONA_MOVIL, 400.0, null),
                incidencias = listOf(cercana(incidencia("o"), true, 100.0)),
            )
        val aviso = presentar(alertas).aviso
        assertTrue(aviso is AvisoAlerta.Radar)
    }

    @Test
    fun `la velocidad en rojo y el exceso salen del dominio y el parpadeo exige el rojo`() {
        val rojo = presentar(AlertasEnCurso(exceso = ExcesoEnCurso(50, rojo = true, exceso = false))).panel
        assertTrue(rojo.rojo)
        assertFalse(rojo.exceso)
        val exceso = presentar(AlertasEnCurso(exceso = ExcesoEnCurso(50, rojo = true, exceso = true))).panel
        assertTrue(exceso.rojo && exceso.exceso)
        val incoherente = presentar(AlertasEnCurso(exceso = ExcesoEnCurso(50, rojo = false, exceso = true))).panel
        assertFalse(incoherente.rojo || incoherente.exceso)
    }

    @Test
    fun `la media del tramo se redondea y conserva si supera y si ha terminado`() {
        val activo = TramoActivo(mediaKmh = 77.6, limiteKmh = 80, superaLimite = false, terminado = false)
        assertEquals(MediaTramoUi(78, false, false), presentar(AlertasEnCurso(tramo = activo)).panel.tramo)
        val final = TramoActivo(mediaKmh = 91.2, limiteKmh = 80, superaLimite = true, terminado = true)
        assertEquals(MediaTramoUi(91, true, true), presentar(AlertasEnCurso(tramo = final)).panel.tramo)
        val sinMedia = TramoActivo(mediaKmh = null, limiteKmh = 80, superaLimite = false, terminado = false)
        assertNull(presentar(AlertasEnCurso(tramo = sinMedia)).panel.tramo?.mediaKmh)
        assertNull(presentar(AlertasEnCurso()).panel.tramo)
    }

    // ---- Marcadores

    @Test
    fun `fuera de conduccion se pintan las vigentes y no las caducadas`() {
        val ahora = Instant.ofEpochMilli(AHORA_MS)
        val vigente = incidencia("viva", fin = ahora.plusSeconds(3_600))
        val caducada = incidencia("muerta", fin = ahora.minusSeconds(60))
        val sinFin = incidencia("sinfin")
        val ui = presentar(null, IncidenciasVigentes(listOf(vigente, caducada, sinFin), ahora))
        assertEquals(listOf("viva", "sinfin"), ui.marcadores.map { it.id })
        assertNull(ui.aviso)
    }

    @Test
    fun `conduciendo se pintan las que filtro la sesion y no el repositorio`() {
        val deSesion = cercana(incidencia("sesion"), true, 900.0)
        val vigentes = IncidenciasVigentes(listOf(incidencia("repo")), Instant.ofEpochMilli(AHORA_MS))
        val ui = presentar(AlertasEnCurso(incidencias = listOf(deSesion)), vigentes)
        assertEquals(listOf("sesion"), ui.marcadores.map { it.id })
    }

    @Test
    fun `un marcador lleva el punto, el tipo, la carretera y la descripcion`() {
        val i = incidencia("x", TipoIncidencia.METEO, listOf(PuntoGeo(39.1, 2.2), PuntoGeo(39.2, 2.3)), "Ma-2141")
        val m = presentar(null, IncidenciasVigentes(listOf(i), null)).marcadores.single()
        assertEquals(MarcadorIncidencia("x", TipoIncidencia.METEO, 39.1, 2.2, i.descripcion, "Ma-2141", false), m)
    }

    @Test
    fun `una incidencia sin puntos no se pinta`() {
        val ui = presentar(null, IncidenciasVigentes(listOf(incidencia("sin", puntos = emptyList())), null))
        assertTrue(ui.marcadores.isEmpty())
    }

    @Test
    fun `la tarjeta es la incidencia elegida mientras exista`() {
        val vigentes = IncidenciasVigentes(listOf(incidencia("a"), incidencia("b")), null)
        assertEquals("b", presentar(null, vigentes, "b").seleccionada?.id)
        assertNull(presentar(null, vigentes, "c").seleccionada)
        assertNull(presentar(null, vigentes, null).seleccionada)
    }

    // ---- Antigüedad de los datos

    @Test
    fun `datos de menos de 30 minutos y sin error no llevan indicador`() {
        val vigentes = IncidenciasVigentes(emptyList(), Instant.ofEpochMilli(AHORA_MS - 29 * 60_000L))
        assertNull(presentar(null, vigentes).frescura)
    }

    @Test
    fun `datos de mas de 30 minutos llevan el indicador de antiguedad`() {
        val vigentes = IncidenciasVigentes(emptyList(), Instant.ofEpochMilli(AHORA_MS - 2 * 3_600_000L))
        assertEquals(FrescuraIncidencias(2 * 3_600_000L, conError = false), presentar(null, vigentes).frescura)
    }

    @Test
    fun `un error con copia lleva el indicador aunque sea reciente`() {
        val vigentes =
            IncidenciasVigentes(emptyList(), Instant.ofEpochMilli(AHORA_MS - 60_000L), ErrorIncidencias.SIN_RED)
        assertEquals(FrescuraIncidencias(60_000L, conError = true), presentar(null, vigentes).frescura)
    }

    @Test
    fun `sin red y sin copia no hay indicador ni incidencias`() {
        val vigentes = IncidenciasVigentes(emptyList(), null, ErrorIncidencias.SIN_RED)
        val ui = presentar(null, vigentes)
        assertNull(ui.frescura)
        assertTrue(ui.marcadores.isEmpty())
    }

    @Test
    fun `conduciendo la antiguedad sale de lo que publica la sesion`() {
        val vieja = AlertasEnCurso(antiguedadIncidenciasMs = 3 * 3_600_000L)
        assertEquals(FrescuraIncidencias(3 * 3_600_000L, false), presentar(vieja).frescura)
        val error = AlertasEnCurso(antiguedadIncidenciasMs = 1_000L, errorIncidencias = ErrorIncidencias.SERVIDOR)
        assertEquals(FrescuraIncidencias(1_000L, true), presentar(error).frescura)
        val sinCopia = AlertasEnCurso(antiguedadIncidenciasMs = null, errorIncidencias = ErrorIncidencias.DATOS)
        assertNull(presentar(sinCopia).frescura)
    }
}
