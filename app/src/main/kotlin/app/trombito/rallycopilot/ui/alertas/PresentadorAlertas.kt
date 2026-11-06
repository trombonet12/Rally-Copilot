package app.trombito.rallycopilot.ui.alertas

import app.trombito.rallycopilot.domain.alertas.AlertasEnCurso
import app.trombito.rallycopilot.domain.alertas.IncidenciasVigentes
import app.trombito.rallycopilot.domain.alertas.RadarProximo
import app.trombito.rallycopilot.domain.alertas.TramoActivo
import app.trombito.rallycopilot.motor.alertas.Incidencia
import app.trombito.rallycopilot.motor.alertas.IncidenciaCercana
import app.trombito.rallycopilot.motor.alertas.TipoRadar
import java.time.Instant
import kotlin.math.roundToInt

/** Los datos de incidencias se declaran antiguos pasados 30 minutos. */
internal const val UMBRAL_ANTIGUEDAD_MS = 30L * 60_000L

/** El banner avisa de una incidencia de la ruta desde esta distancia (la ventana máxima del aviso por voz). */
internal const val DISTANCIA_AVISO_INCIDENCIA_M = 800.0

private const val PASO_DISTANCIA_M = 10

/**
 * Convierte lo que publica el dominio en lo que pinta la UI. Sin conducción en curso ([alertas] null) se enseñan las
 * incidencias vigentes de [vigentes] (sin las caducadas a [ahoraMs]); conduciendo, las que ya filtró la sesión (las de
 * la ruta y las del radio de 3 km). Prioridad del banner: radar antes que incidencia. [seleccionadaId] es la
 * incidencia tocada en el mapa; si ya no está entre los marcadores, no hay tarjeta.
 */
internal fun presentarAlertas(
    alertas: AlertasEnCurso?,
    vigentes: IncidenciasVigentes,
    ahoraMs: Long,
    seleccionadaId: String? = null,
    radares: RadaresUi = RadaresUi(),
): AlertasUiState {
    val cercanas = alertas?.incidencias ?: incidenciasFueraDeConduccion(vigentes, ahoraMs)
    val marcadores = marcadoresDe(cercanas)
    val antiguedadMs =
        if (alertas != null) alertas.antiguedadIncidenciasMs else antiguedadDe(vigentes, ahoraMs)
    val hayError = if (alertas != null) alertas.errorIncidencias != null else vigentes.errorUltimo != null
    return AlertasUiState(
        aviso = alertas?.let { avisoDe(it, cercanas) },
        panel = alertas?.let(::panelDe) ?: AlertasPanel(),
        marcadores = marcadores,
        frescura = frescuraDe(antiguedadMs, hayError),
        radares = radares,
        bocadillo = alertas?.let { bocadilloDe(it, cercanas, marcadores, radares) },
        seleccionada = marcadores.firstOrNull { it.id == seleccionadaId },
    )
}

private fun incidenciasFueraDeConduccion(
    vigentes: IncidenciasVigentes,
    ahoraMs: Long,
): List<IncidenciaCercana> {
    val ahora = Instant.ofEpochMilli(ahoraMs)
    return vigentes.incidencias
        .filterNot { it.caducadaEn(ahora) }
        .map { IncidenciaCercana(it, enRuta = false, distanciaM = 0.0) }
}

private fun antiguedadDe(
    vigentes: IncidenciasVigentes,
    ahoraMs: Long,
): Long? = vigentes.obtenidasEn?.let { (ahoraMs - it.toEpochMilli()).coerceAtLeast(0L) }

/**
 * Sin copia ([antiguedadMs] null) no hay nada que decir (ni incidencias): ni siquiera un error. Con copia, el
 * indicador sale si es vieja o si el último refresco falló.
 */
internal fun frescuraDe(
    antiguedadMs: Long?,
    hayError: Boolean,
): FrescuraIncidencias? =
    antiguedadMs
        ?.takeIf { hayError || it > UMBRAL_ANTIGUEDAD_MS }
        ?.let { FrescuraIncidencias(it, hayError) }

/** Radar próximo antes que incidencia de la ruta (a ≤ 800 m, la más cercana). */
private fun avisoDe(
    alertas: AlertasEnCurso,
    cercanas: List<IncidenciaCercana>,
): AvisoAlerta? =
    alertas.radarProximo?.let(::avisoDeRadar)
        ?: cercanas
            .filter { it.enRuta && it.distanciaM <= DISTANCIA_AVISO_INCIDENCIA_M }
            .minByOrNull { it.distanciaM }
            ?.let { AvisoAlerta.Incidencia(it.incidencia.tipo, it.incidencia.carretera, redondear(it.distanciaM)) }

private fun avisoDeRadar(radar: RadarProximo): AvisoAlerta.Radar {
    // Un fijo del otro sentido no enseña su límite: no es el de este coche.
    val limite = radar.limiteKmh.takeUnless { radar.sentidoContrario }
    return AvisoAlerta.Radar(claseDe(radar.tipo), redondear(radar.distanciaM), limite, radar.sentidoContrario)
}

private fun redondear(metros: Double): Int =
    (metros.coerceAtLeast(0.0) / PASO_DISTANCIA_M).roundToInt() * PASO_DISTANCIA_M

private fun panelDe(alertas: AlertasEnCurso) =
    AlertasPanel(
        rojo = alertas.exceso.rojo,
        // El parpadeo siempre va con el rojo: si el dominio dijera exceso sin rojo, no se contradice la cifra.
        exceso = alertas.exceso.rojo && alertas.exceso.exceso,
        tramo = alertas.tramo?.let(::mediaDe),
    )

private fun mediaDe(tramo: TramoActivo) =
    MediaTramoUi(tramo.mediaKmh?.roundToInt(), tramo.superaLimite, tramo.terminado)
