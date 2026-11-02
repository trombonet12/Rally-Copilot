package app.trombito.rallycopilot.data.voz

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.tanh

/**
 * Ganancia propia del copiloto sobre un PCM de 16 bits. Hasta el 80 % del fondo de escala la señal solo se escala; por
 * encima se comprime suavemente (tanh) hacia el techo, de modo que subir de 100 % no recorta (no distorsiona de golpe)
 * y nunca desborda un `Short`.
 */
object GananciaPcm {
    const val MINIMA = 50
    const val NORMAL = 100
    const val MAXIMA = 150

    private const val FONDO = 32_768.0
    private const val CODO = 0.8
    private const val TECHO = 32_767

    /** [muestras] con la ganancia de [porcentaje] (50–150); con 100 devuelve la misma tabla sin copiarla. */
    fun aplicar(
        muestras: ShortArray,
        porcentaje: Int,
    ): ShortArray {
        val p = porcentaje.coerceIn(MINIMA, MAXIMA)
        if (p == NORMAL) return muestras
        val ganancia = p / NORMAL.toDouble()
        return ShortArray(muestras.size) { i -> escalar(muestras[i] / FONDO * ganancia) }
    }

    private fun escalar(valor: Double): Short {
        val magnitud = abs(valor)
        val suave = if (magnitud <= CODO) magnitud else CODO + (1 - CODO) * tanh((magnitud - CODO) / (1 - CODO))
        return (sign(valor) * suave * FONDO).roundToInt().coerceIn(-TECHO, TECHO).toShort()
    }
}
