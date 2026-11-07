package app.trombito.rallycopilot.data.escucha

import kotlin.math.sqrt

/**
 * Detector de actividad de voz por energía para la escucha continua de «Copiloto»: solo deja pasar al reconocedor los
 * trozos con voz (y un rato después, [colaMuestras], para que acabe la frase), de modo que el silencio y el ruido
 * constante del coche no se decodifican. Aprende el nivel de ruido de fondo y exige [FACTOR] veces más (con un mínimo
 * de [UMBRAL_MIN]). No es un VAD fino: solo ahorra CPU; lo que se oye lo decide el reconocedor.
 *
 * - **Sin eco**: mientras el copiloto habla (`hablando`) la puerta está cerrada, y sigue cerrada [silencioTrasHablar]
 *   muestras después (cola de la sala y del altavoz), para que no se oiga a sí mismo.
 * - **Histéresis**: abrir exige superar el umbral; una vez abierta, se mantiene con [HISTERESIS] veces el umbral, para
 *   no cortar la palabra en los huecos entre sílabas.
 * - **Contadores**: [bloques] vistos y [decodificados] (los que pasaron), para medir en depuración cuánto trabaja el
 *   reconocedor ([porcentajeAbierta]).
 */
class PuertaVoz(
    private val colaMuestras: Int = FRECUENCIA_HZ * 8 / 10,
    private val silencioTrasHablar: Int = FRECUENCIA_HZ / 2,
) {
    private var ruido = 0.0
    private var restantes = 0
    private var vetoRestante = 0
    private var abiertaAntes = false

    /** Trozos vistos desde que se creó la puerta. */
    var bloques = 0L
        private set

    /** Trozos que pasaron al reconocedor. */
    var decodificados = 0L
        private set

    /** Porcentaje (0–100) de [bloques] que se decodificaron. */
    fun porcentajeAbierta(): Double = if (bloques == 0L) 0.0 else decodificados * PORCENTAJE_TOTAL / bloques

    /**
     * `true` si el trozo de [n] muestras de [muestras] debe llegar al reconocedor. [hablando]: el copiloto está
     * sonando ahora mismo.
     */
    fun abierta(
        muestras: ShortArray,
        n: Int,
        hablando: Boolean = false,
    ): Boolean {
        bloques++
        if (hablando) vetoRestante = silencioTrasHablar
        if (hablando || vetoRestante > 0) {
            if (!hablando) vetoRestante -= n
            restantes = 0
            abiertaAntes = false
            return false
        }
        val nivel = rms(muestras, n)
        val umbral = maxOf(UMBRAL_MIN, ruido * FACTOR)
        if (nivel > if (abiertaAntes) umbral * HISTERESIS else umbral) {
            restantes = colaMuestras
        } else if (restantes <= 0) {
            ruido = if (ruido == 0.0) nivel else ruido * (1 - APRENDIZAJE) + nivel * APRENDIZAJE
        }
        val pasa = restantes > 0
        restantes -= n
        abiertaAntes = pasa
        if (pasa) decodificados++
        return pasa
    }

    private fun rms(
        muestras: ShortArray,
        n: Int,
    ): Double {
        if (n <= 0) return 0.0
        var suma = 0.0
        for (i in 0 until n) suma += muestras[i].toDouble() * muestras[i]
        return sqrt(suma / n)
    }

    companion object {
        const val UMBRAL_MIN = 400.0
        const val FACTOR = 3.0
        const val HISTERESIS = 0.6
        private const val APRENDIZAJE = 0.05
        private const val PORCENTAJE_TOTAL = 100.0
    }
}
