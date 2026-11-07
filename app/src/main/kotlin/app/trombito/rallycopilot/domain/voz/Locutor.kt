package app.trombito.rallycopilot.domain.voz

import kotlinx.coroutines.flow.StateFlow

/** Voces masculinas es_ES disponibles (decisión 2026-11-01): `davefx` y `sharvard` (hablante 0). */
enum class VozCopiloto { DAVEFX, SHARVARD }

/**
 * Velocidad de habla de la voz. [factor] es el `speed` de sherpa-onnx (en Piper/VITS, `length_scale = 1 / speed`):
 * 1,2 acorta cada frase un 17 % (una de 3 s dura 2,5 s: a 100 km/h son ~14 m menos de carretera hablada) y sigue
 * siendo clara; por encima de ~1,3 el VITS empieza a comerse sílabas.
 */
enum class VelocidadHabla(
    val factor: Float,
) {
    NORMAL(1.0f),
    RAPIDA(1.2f),
}

/** Con qué urgencia entra una frase en la cola del [Locutor]. */
enum class PrioridadVoz {
    /** Por orden de llegada, detrás de lo que ya espera. */
    NORMAL,

    /** Vacía lo que espera sin haber empezado y suena la primera (cuenta atrás, avisos de GPS). */
    URGENTE,
}

/**
 * La voz del copiloto: sintetiza y reproduce frases en español, sin red.
 *
 * - [preparar] sintetiza por adelantado (en un hilo propio) las frases que se van a necesitar y las guarda en una
 *   caché en disco; lo que ya no está en la última lista pedida se descarta.
 * - [decir] hace sonar una frase (al instante si está en caché; si no, la sintetiza antes) con el foco de audio
 *   `GAIN_TRANSIENT_MAY_DUCK`: la música baja mientras habla y vuelve al acabar. Una frase a la vez: las demás esperan
 *   su turno por orden de llegada, salvo las [PrioridadVoz.URGENTE], que pasan delante, y las que caducan.
 */
interface Locutor {
    /** `true` mientras una frase está sonando. */
    val hablando: StateFlow<Boolean>

    /** Voz con la que se sintetiza y se dice a partir de ahora; vacía la cola de [preparar] de la voz anterior. */
    fun usarVoz(v: VozCopiloto)

    /**
     * Velocidad de habla de lo que se sintetice y suene a partir de ahora (también la vista previa). Cada velocidad
     * tiene su propia caché: lo ya preparado a otra velocidad se descarta y [preparar] debe volver a pedirse.
     */
    fun usarVelocidad(v: VelocidadHabla) = Unit

    /** Pide sintetizar [textos] por adelantado con la voz actual; sustituye a la lista pedida antes. */
    fun preparar(textos: List<String>)

    /**
     * Hace sonar [texto] con la voz actual. Con [PrioridadVoz.URGENTE] vacía lo que espera sin haber empezado y va
     * primera (la frase que ya suena no se corta). Si [caducaMs] (tiempo absoluto del reloj monótono del locutor,
     * `SystemClock.elapsedRealtime()`) llega antes de que la frase empiece a sonar, se descarta.
     */
    fun decir(
        texto: String,
        prioridad: PrioridadVoz = PrioridadVoz.NORMAL,
        caducaMs: Long? = null,
    )

    /** Duración en segundos: la real si la frase está en caché; si no, una estimación por longitud. */
    fun duracionEstimadaS(texto: String): Double

    /** Dice una frase de muestra con [v] sin cambiar la voz elegida (diálogo de primer uso y Ajustes). */
    fun probar(v: VozCopiloto)

    /**
     * Ganancia propia del copiloto en porcentaje (50–150; 100 = sin cambio), aparte del volumen del móvil. Vale para
     * lo que suene a partir de ahora, también durante la conducción.
     */
    fun usarVolumen(porcentaje: Int) = Unit

    /** Dice [texto] con [v] sin cambiar la voz elegida (ejemplo de Ajustes con la voz y el volumen elegidos). */
    fun probarFrase(
        v: VozCopiloto,
        texto: String,
    ) = probar(v)

    /** Detiene todo y libera el modelo nativo; el locutor no se puede volver a usar. */
    fun cerrar()
}
