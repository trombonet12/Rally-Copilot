package app.trombito.rallycopilot.domain.escucha

import app.trombito.rallycopilot.motor.edicion.Orden
import kotlinx.coroutines.flow.StateFlow

/** Qué pasó al escuchar una orden de voz. */
sealed interface ResultadoEscucha {
    /** Se entendió [orden] a partir de lo que dijo el usuario ([texto], solo para depurar: nunca se registra). */
    data class Entendido(
        val orden: Orden,
        val texto: String,
    ) : ResultadoEscucha

    /** Se oyó algo ([texto], vacío si nada) pero no es una orden. */
    data class NoEntendido(
        val texto: String,
    ) : ResultadoEscucha

    /** Falta el permiso de micrófono. */
    data object SinPermiso : ResultadoEscucha

    /** No se pudo escuchar: sin foco de audio, sin micrófono o sin modelo. */
    data object NoDisponible : ResultadoEscucha
}

/**
 * Escucha de órdenes de voz sin red: tras pulsar, graba con foco de audio EXCLUSIVO (la música se pausa mientras
 * escucha y vuelve al acabar), reconoce con una gramática cerrada del vocabulario del copiloto y devuelve la [Orden].
 * Acaba por silencio (≈ 1,2 s tras hablar) o a los 5 s. Una escucha a la vez.
 */
interface Escucha {
    /** `true` mientras el micrófono está abierto para una orden. */
    val escuchando: StateFlow<Boolean>

    fun tienePermiso(): Boolean

    /** Escucha una orden; cancelar la corrutina corta la grabación y suelta el foco. */
    suspend fun escuchar(): ResultadoEscucha
}
