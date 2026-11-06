package app.trombito.rallycopilot.ui.alertas

import app.trombito.rallycopilot.domain.alertas.AlertasEnCurso
import app.trombito.rallycopilot.domain.alertas.PasoRadarEnCurso
import app.trombito.rallycopilot.domain.alertas.RadaresEnMapa
import app.trombito.rallycopilot.motor.alertas.IncidenciaCercana
import app.trombito.rallycopilot.motor.alertas.TipoRadar
import kotlin.math.roundToInt

/** Los radares y tramos de los datos tal como se pintan: la entrada de cada tramo es su marcador (la salida, no). */
internal fun radaresUiDe(datos: RadaresEnMapa): RadaresUi =
    RadaresUi(
        marcadores =
            datos.radares
                .filter { it.tipo != TipoRadar.TRAMO_FIN }
                .map { MarcadorRadar(it.id, claseDe(it.tipo), it.punto.lat, it.punto.lon, it.limiteKmh) },
        tramos = datos.tramos.map { TramoRadarMapa(it.id, it.limiteKmh, it.trazado) },
    )

internal fun claseDe(tipo: TipoRadar): ClaseRadar =
    when (tipo) {
        TipoRadar.FIJO -> ClaseRadar.FIJO
        TipoRadar.ZONA_MOVIL -> ClaseRadar.MOVIL
        TipoRadar.TRAMO_INICIO, TipoRadar.TRAMO_FIN -> ClaseRadar.TRAMO
    }

/**
 * Bocadillo del mapa, por prioridad: el paso por un radar fijo (unos segundos), el radar que se acerca, el tramo de
 * radar en curso (en su salida) y la incidencia de la ruta a ≤ 800 m. Sin marcador al que anclarlo, nada.
 */
internal fun bocadilloDe(
    alertas: AlertasEnCurso,
    cercanas: List<IncidenciaCercana>,
    incidencias: List<MarcadorIncidencia>,
    radares: RadaresUi,
): BocadilloMapa? =
    alertas.pasoRadar?.let { bocadilloDePaso(it, radares) }
        ?: alertas.radarProximo?.let { proximo ->
            radares.marcadores.firstOrNull { it.id == proximo.id }?.let {
                val contenido = ContenidoBocadillo.Radar(it.clase, proximo.limiteKmh, proximo.sentidoContrario)
                BocadilloMapa("proximo-${it.id}", it.lat, it.lon, contenido)
            }
        }
        ?: bocadilloDeTramo(alertas, radares)
        ?: cercanas
            .filter { it.enRuta && it.distanciaM <= DISTANCIA_AVISO_INCIDENCIA_M }
            .minByOrNull { it.distanciaM }
            ?.let { c -> incidencias.firstOrNull { it.id == c.incidencia.id } }
            ?.let { BocadilloMapa("incidencia-${it.id}", it.lat, it.lon, ContenidoBocadillo.Incidencia(it.tipo)) }

private fun bocadilloDePaso(
    paso: PasoRadarEnCurso,
    radares: RadaresUi,
): BocadilloMapa? =
    radares.marcadores.firstOrNull { it.id == paso.radarId }?.let {
        val contenido = ContenidoBocadillo.Paso(paso.velocidadKmh.roundToInt(), paso.limiteKmh)
        BocadilloMapa("paso-${it.id}-${contenido.velocidadKmh}", it.lat, it.lon, contenido)
    }

/** La media del tramo en curso, anclada a su salida (último punto del trazado). */
private fun bocadilloDeTramo(
    alertas: AlertasEnCurso,
    radares: RadaresUi,
): BocadilloMapa? {
    val tramo = alertas.tramo
    val salida =
        radares.tramos
            .firstOrNull { it.id == tramo?.id }
            ?.puntos
            ?.lastOrNull()
    return if (tramo == null || salida == null) {
        null
    } else {
        val contenido = ContenidoBocadillo.Tramo(tramo.limiteKmh, tramo.mediaKmh?.roundToInt(), tramo.superaLimite)
        BocadilloMapa("tramo-${tramo.id}", salida.lat, salida.lon, contenido)
    }
}
