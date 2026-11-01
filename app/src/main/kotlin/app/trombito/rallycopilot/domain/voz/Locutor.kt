package app.trombito.rallycopilot.domain.voz

import kotlinx.coroutines.flow.StateFlow

/** Voces masculinas es_ES disponibles (decisión 2026-11-01): `davefx` y `sharvard` (hablante 0). */
enum class VozCopiloto { DAVEFX, SHARVARD }

/**
 * La voz del copiloto: sintetiza y reproduce frases en español, sin red.
 *
 * - [preparar] sintetiza por adelantado (en un hilo propio) las frases que se van a necesitar y las guarda en una
 *   caché en disco; lo que ya no está en la última lista pedida se descarta.
 * - [decir] hace sonar una frase (al instante si está en caché; si no, la sintetiza antes) con el foco de audio
 *   `GAIN_TRANSIENT_MAY_DUCK`: la música baja mientras habla y vuelve al acabar. Una frase a la vez: las demás esperan
 *   su turno por orden de llegada.
 */
interface Locutor {
    /** `true` mientras una frase está sonando. */
    val hablando: StateFlow<Boolean>

    /** Voz con la que se sintetiza y se dice a partir de ahora; vacía la cola de [preparar] de la voz anterior. */
    fun usarVoz(v: VozCopiloto)

    /** Pide sintetizar [textos] por adelantado con la voz actual; sustituye a la lista pedida antes. */
    fun preparar(textos: List<String>)

    /** Hace sonar [texto] con la voz actual. */
    fun decir(texto: String)

    /** Duración en segundos: la real si la frase está en caché; si no, una estimación por longitud. */
    fun duracionEstimadaS(texto: String): Double

    /** Dice una frase de muestra con [v] sin cambiar la voz elegida (diálogo de primer uso y Ajustes). */
    fun probar(v: VozCopiloto)

    /** Detiene todo y libera el modelo nativo; el locutor no se puede volver a usar. */
    fun cerrar()
}
