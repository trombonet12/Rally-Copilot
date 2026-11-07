package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.notas.FrasesNota
import app.trombito.rallycopilot.motor.voz.Frase
import app.trombito.rallycopilot.motor.voz.TipoFrase
import kotlin.math.roundToInt

/** Distancias y utilidades comunes de los avisos de radares e incidencias. */
internal object VentanasAviso {
    private const val AVISO_S = 15.0
    private const val AVISO_MIN_M = 300.0
    private const val AVISO_MAX_M = 800.0
    private const val RADAR_MIN_M = 200.0
    private const val ENTRADA_S = 6.0
    private const val ENTRADA_MIN_M = 80.0
    private const val ENTRADA_MAX_M = 250.0
    private const val CIEN_M = 100.0

    /** Distancia de aviso de lo que se acerca: `v · 15 s` acotado a 300–800 m y redondeado a cientos. */
    fun avisoM(vMs: Double): Double = aCientos((vMs.coerceAtLeast(0.0) * AVISO_S).coerceIn(AVISO_MIN_M, AVISO_MAX_M))

    /** Distancia del primer aviso de un radar fijo: `v · 15 s` acotado a 200–800 m y redondeado a cientos. */
    fun avisoRadarM(vMs: Double): Double =
        aCientos((vMs.coerceAtLeast(0.0) * AVISO_S).coerceIn(RADAR_MIN_M, AVISO_MAX_M))

    /** Ventana para avisar de que se entra en una zona o tramo: `v · 6 s` acotado a 80–250 m (sin redondear). */
    fun entradaM(vMs: Double): Double = (vMs.coerceAtLeast(0.0) * ENTRADA_S).coerceIn(ENTRADA_MIN_M, ENTRADA_MAX_M)

    private fun aCientos(metros: Double): Double = (metros / CIEN_M).roundToInt() * CIEN_M

    /** «a quinientos» para la distancia que queda redondeada a cientos, o `null` si queda menos de 50 m. */
    fun aDistancia(restanteM: Double): String? {
        val cientos = (restanteM / CIEN_M).roundToInt()
        return if (cientos < 1) null else "a ${FrasesNota.enPalabras(cientos * CIEN_M.toInt())}"
    }

    fun alerta(
        id: String,
        texto: String,
        puntoM: Double?,
    ) = Frase(id, texto, TipoFrase.ALERTA, puntoM)
}
