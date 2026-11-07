package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.notas.FrasesNota
import app.trombito.rallycopilot.motor.velocidad.MS_POR_S
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Cifras, distancias y tiempos en palabras para la voz sintetizada («doce kilómetros» y no «12 km»). Lo usa
 * [RespuestasVoz]; son funciones puras.
 */
object FormatoHablado {
    private const val MIL = 1000
    private const val CIEN_M = 100.0
    private const val CINCUENTA_M = 50.0
    private const val DIEZ_M = 10.0
    private const val MIN_KM_ENTERO = 10
    private const val DECIMAS_POR_KM = 10
    private const val DECIMAS_MEDIO = 5
    private const val S_POR_MIN = 60
    private const val MIN_POR_HORA = 60
    private const val IGUAL_MS = 500L
    private const val SEGUNDOS_DOS_CIFRAS = 10

    /** Número entero de 0 a 9999 en palabras (los números de carretera llegan a «dos mil ciento cuarenta y uno»). */
    fun numero(n: Int): String =
        when {
            n < 0 || n >= 10 * MIL -> {
                n.toString()
            }

            n < MIL -> {
                FrasesNota.enPalabras(n)
            }

            else -> {
                val miles = n / MIL
                val resto = n % MIL
                val cabeza = if (miles == 1) "mil" else "${FrasesNota.enPalabras(miles)} mil"
                if (resto == 0) cabeza else "$cabeza ${FrasesNota.enPalabras(resto)}"
            }
        }

    /** El número delante de un nombre masculino: «un», «veintiún», «treinta y un»; el resto como siempre. */
    fun apocope(n: Int): String {
        val palabras = numero(n)
        return when {
            palabras.endsWith("veintiuno") -> palabras.removeSuffix("veintiuno") + "veintiún"
            palabras.endsWith("uno") -> palabras.removeSuffix("uno") + "un"
            else -> palabras
        }
    }

    private fun unidad(
        cantidad: Int,
        singular: String,
    ): String = if (cantidad == 1) singular else "${singular}s"

    private fun redondear(
        valor: Double,
        paso: Double,
    ): Double = (valor / paso).roundToInt() * paso

    /**
     * La distancia en metros redondeados (a 10 por debajo de 100, a 50 por debajo del kilómetro) o en kilómetros (con
     * décimas por debajo de 10); el `Boolean` es `true` si se dice en singular («falta un kilómetro y medio»).
     */
    fun distancia(distanciaM: Double): Pair<String, Boolean> {
        val enMetros = redondear(distanciaM, if (distanciaM < CIEN_M) DIEZ_M else CINCUENTA_M).coerceAtLeast(DIEZ_M)
        return if (enMetros < MIL) {
            "${FrasesNota.enPalabras(enMetros.roundToInt())} metros" to false
        } else if (enMetros / MIL >= MIN_KM_ENTERO) {
            "${apocope((distanciaM / MIL).roundToInt())} kilómetros" to false
        } else {
            kilometros((maxOf(distanciaM, enMetros) / CIEN_M).roundToInt())
        }
    }

    private fun kilometros(decimas: Int): Pair<String, Boolean> {
        val enteros = decimas / DECIMAS_POR_KM
        val resto = decimas % DECIMAS_POR_KM
        return when (resto) {
            0 -> "${apocope(enteros)} ${unidad(enteros, "kilómetro")}" to (enteros == 1)
            DECIMAS_MEDIO -> "${apocope(enteros)} ${unidad(enteros, "kilómetro")} y medio" to (enteros == 1)
            else -> "${FrasesNota.enPalabras(enteros)} coma ${FrasesNota.enPalabras(resto)} kilómetros" to false
        }
    }

    /** «quince minutos», «una hora y siete minutos», «menos de un minuto». */
    fun duracion(tiempoS: Double): String {
        val minutos = (tiempoS / S_POR_MIN).roundToInt()
        val horas = minutos / MIN_POR_HORA
        val resto = minutos % MIN_POR_HORA
        val horasDichas = if (horas == 1) "una" else numero(horas)
        return when {
            minutos < 1 -> "menos de un minuto"
            horas == 0 -> "${apocope(resto)} ${unidad(resto, "minuto")}"
            resto == 0 -> "$horasDichas ${unidad(horas, "hora")}"
            else -> "$horasDichas ${unidad(horas, "hora")} y ${apocope(resto)} ${unidad(resto, "minuto")}"
        }
    }

    /** El tiempo de un tramo: «uno veinte», «uno cero cinco», «dos minutos», «cuarenta y cinco segundos». */
    fun tiempo(tiempoMs: Long): String {
        val total = (tiempoMs / MS_POR_S).roundToInt()
        val minutos = total / S_POR_MIN
        val segundos = total % S_POR_MIN
        return when {
            minutos == 0 -> "${apocope(segundos)} ${unidad(segundos, "segundo")}"
            segundos == 0 -> "${apocope(minutos)} ${unidad(minutos, "minuto")}"
            segundos < SEGUNDOS_DOS_CIFRAS -> "${numero(minutos)} cero ${numero(segundos)}"
            else -> "${numero(minutos)} ${numero(segundos)}"
        }
    }

    /** «tres segundos por delante» (diferencia negativa = más rápido), «por detrás» o «igual que el mejor». */
    fun diferencia(deltaMs: Long): String {
        if (abs(deltaMs) < IGUAL_MS) return "igual que el mejor"
        val segundos = (abs(deltaMs) / MS_POR_S).roundToInt()
        val sentido = if (deltaMs < 0) "por delante" else "por detrás"
        return "${apocope(segundos)} ${unidad(segundos, "segundo")} $sentido"
    }
}
