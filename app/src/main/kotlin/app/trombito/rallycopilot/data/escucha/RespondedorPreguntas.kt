package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
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
 * El punto kilométrico y el municipio no están en los datos de vías (solo nombre y ref), así que «¿dónde estoy?» dice
 * la carretera y su nombre.
 */
class RespondedorPreguntas(
    private val estado: () -> EstadoNavegacion,
    private val crono: ProveedorCrono,
    private val vias: ProveedorViaActual,
    private val ubicacion: FuenteUbicacion,
    private val ahoraMs: () -> Long,
    private val zona: () -> ZoneId = ZoneId::systemDefault,
) {
    suspend fun responder(tipo: TipoPregunta): String =
        when (tipo) {
            TipoPregunta.CUANTO_FALTA -> cuantoFalta()
            TipoPregunta.HORA_LLEGADA -> horaLlegada()
            TipoPregunta.LIMITE -> RespuestasVoz.limite(viaActual()?.limiteKmh)
            TipoPregunta.DONDE_ESTOY -> viaActual().let { RespuestasVoz.dondeEstoy(it?.nombre, it?.ref) }
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

    /** La vía de la posición actual, o `null` si no hay fijación a tiempo o no hay vía (o datos) allí. */
    private suspend fun viaActual(): ViaActual? {
        val fijacion: Fijacion =
            withTimeoutOrNull(ESPERA_FIJACION_MS) { ubicacion.fijaciones().firstOrNull() } ?: return null
        return vias.viaEn(fijacion)
    }

    private companion object {
        const val ESPERA_FIJACION_MS = 3_000L
        const val YA_HAS_LLEGADO = "ya has llegado"
    }
}
