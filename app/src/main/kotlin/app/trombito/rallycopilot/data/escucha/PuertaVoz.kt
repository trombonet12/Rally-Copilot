package app.trombito.rallycopilot.data.escucha

import kotlin.math.sqrt

/**
 * Detector de actividad de voz por energía para la escucha continua de «Copiloto»: solo deja pasar al reconocedor los
 * trozos con voz (y un rato después, [colaMuestras], para que acabe la frase), de modo que el silencio y el ruido
 * constante del coche no se decodifican. Aprende el nivel de ruido de fondo y exige [FACTOR] veces más (con un mínimo
 * de [UMBRAL_MIN]). No es un VAD fino: solo ahorra CPU; lo que se oye lo decide el reconocedor.
 */
class PuertaVoz(
    private val colaMuestras: Int = FRECUENCIA_HZ * 8 / 10,
) {
    private var ruido = 0.0
    private var restantes = 0

    /** `true` si el trozo de [n] muestras de [muestras] debe llegar al reconocedor. */
    fun abierta(
        muestras: ShortArray,
        n: Int,
    ): Boolean {
        val nivel = rms(muestras, n)
        if (nivel > maxOf(UMBRAL_MIN, ruido * FACTOR)) {
            restantes = colaMuestras
        } else if (restantes <= 0) {
            ruido = if (ruido == 0.0) nivel else ruido * (1 - APRENDIZAJE) + nivel * APRENDIZAJE
        }
        val pasa = restantes > 0
        restantes -= n
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
        private const val APRENDIZAJE = 0.05
    }
}
