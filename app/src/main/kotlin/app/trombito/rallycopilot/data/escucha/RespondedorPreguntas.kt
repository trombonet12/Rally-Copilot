package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.vias.ProveedorPuntoKilometrico
import app.trombito.rallycopilot.domain.vias.ProveedorViaActual
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.edicion.TipoPregunta
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.voz.RespuestasVoz
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId

/**
 * Responde a las preguntas de voz con una frase corta (`RespuestasVoz` da el texto; aquí se buscan los datos):
 *
 * - «¿cuánto falta?» y «¿a qué hora llego?»: el progreso de la ruta guiada ([estado]: distancia y tiempo restantes y
 *   hora de llegada prevista); sin guiado, «no hay destino».
 * - «¿qué límite hay?» y «¿dónde estoy?»: la vía de la posición actual (una fijación nueva de [ubicacion], que bajo
 *   la conducción llega al instante con la última conocida, y [vias]); sin vía o sin límite, «no lo sé».
 * - «¿cómo voy?»: el cronómetro en vivo ([crono]): tiempo del tramo y diferencia con el mejor si lo hay.
 *
 * «¿Dónde estoy?» dice la carretera, su nombre y, si los hitos kilométricos de OSM (archivo opcional, [puntosKm]) dan
 * el de esa carretera cerca de la posición, el kilómetro («Ma diez, kilómetro veintitrés»); sin ese archivo o sin
 * hito cercano, solo la carretera y su nombre. El municipio no está en los datos.
 */
@Suppress("LongParameterList") // colaboradores de las preguntas de voz, inyectados
class RespondedorPreguntas(
    private val estado: () -> EstadoNavegacion,
    private val crono: ProveedorCrono,
    private val vias: ProveedorViaActual,
    private val ubicacion: FuenteUbicacion,
    private val ahoraMs: () -> Long,
    private val zona: () -> ZoneId = ZoneId::systemDefault,
    private val puntosKm: ProveedorPuntoKilometrico = ProveedorPuntoKilometrico.Ninguno,
) {
    suspend fun responder(tipo: TipoPregunta): String =
        when (tipo) {
            TipoPregunta.CUANTO_FALTA -> cuantoFalta()
            TipoPregunta.HORA_LLEGADA -> horaLlegada()
            TipoPregunta.LIMITE -> RespuestasVoz.limite(viaActual()?.limiteKmh)
            TipoPregunta.DONDE_ESTOY -> dondeEstoy()
            TipoPregunta.COMO_VOY -> comoVoy()
        }

    private fun cuantoFalta(): String =
        when (val e = estado()) {
            is EstadoNavegacion.Guiando -> {
                e.progreso?.let { RespuestasVoz.cuantoFalta(it.distanciaRestanteM, it.tiempoRestanteS) }
                    ?: RespuestasVoz.NO_SE
            }

            is EstadoNavegacion.Llegada -> {
                YA_HAS_LLEGADO
            }

            else -> {
                RespuestasVoz.SIN_DESTINO
            }
        }

    private fun horaLlegada(): String =
        when (val e = estado()) {
            is EstadoNavegacion.Guiando -> {
                e.progreso?.let {
                    val hora = Instant.ofEpochMilli(it.llegadaMs).atZone(zona())
                    RespuestasVoz.horaLlegada(hora.hour, hora.minute)
                } ?: RespuestasVoz.NO_SE
            }

            is EstadoNavegacion.Llegada -> {
                YA_HAS_LLEGADO
            }

            else -> {
                RespuestasVoz.SIN_DESTINO
            }
        }

    private fun comoVoy(): String {
        val actual = crono.crono.value ?: return RespuestasVoz.SIN_CRONO
        val resultado = actual.resultado
        return when {
            resultado != null -> {
                RespuestasVoz.comoVoy(resultado.totalMs, resultado.deltaMs)
            }

            !actual.enMarcha -> {
                RespuestasVoz.AUN_NO_HAS_SALIDO
            }

            else -> {
                val transcurrido = (actual.transcurridoMs + (ahoraMs() - actual.actualizadoEnMs)).coerceAtLeast(0L)
                RespuestasVoz.comoVoy(transcurrido, actual.deltaMs.takeIf { actual.hayMejorPasada })
            }
        }
    }

    private suspend fun fijacionActual(): Fijacion? =
        withTimeoutOrNull(ESPERA_FIJACION_MS) { ubicacion.fijaciones().firstOrNull() }

    /** La vía de la posición actual, o `null` si no hay fijación a tiempo o no hay vía (o datos) allí. */
    private suspend fun viaActual(): ViaActual? = fijacionActual()?.let { vias.viaEn(it) }

    private suspend fun dondeEstoy(): String {
        val fijacion = fijacionActual()
        val via = fijacion?.let { vias.viaEn(it) }
        val km = if (fijacion != null) puntosKm.puntoKilometrico(via?.ref, fijacion.punto)?.km else null
        return RespuestasVoz.dondeEstoy(via?.nombre, via?.ref, km)
    }

    private companion object {
        const val ESPERA_FIJACION_MS = 3_000L
        const val YA_HAS_LLEGADO = "ya has llegado"
    }
}
