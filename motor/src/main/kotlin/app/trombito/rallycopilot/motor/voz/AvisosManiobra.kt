package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.notas.FrasesNota
import app.trombito.rallycopilot.motor.ruta.FrasesManiobra
import app.trombito.rallycopilot.motor.ruta.FrasesParada
import app.trombito.rallycopilot.motor.ruta.Maniobra
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import kotlin.math.roundToInt

/**
 * Frases de las [maniobras] de una ruta; [posicionesM] son sus posiciones en la traza (m), una por maniobra y en el
 * mismo orden. La maniobra de salida nunca se anuncia. Cada maniobra tiene dos fases con id estable
 * (`maniobra-<índice>-preaviso` y `maniobra-<índice>-inminente`):
 *
 * - preaviso («a quinientos, cruce, derecha, Ma-10»): desde `v · 15 s` (acotado a 300–1000 m) hasta el aviso
 *   inminente; la distancia se redondea a cientos y, si saldría menos de 100 m, no se canta; es el único que lleva el
 *   nombre de la vía;
 * - inminente (la frase de [FrasesManiobra] sin vía, «cruce, derecha»): desde `v · 5 s` (acotado a 40–200 m) hasta la
 *   maniobra.
 */
class AvisosManiobra(
    val maniobras: List<Maniobra>,
    private val posicionesM: List<Double>,
) {
    init {
        require(maniobras.size == posicionesM.size) {
            "Maniobras (${maniobras.size}) y posiciones (${posicionesM.size}) no coinciden"
        }
    }

    /**
     * Frases de maniobra vigentes en [posicionM] yendo a [vMs] (m/s), en orden de ruta. Las maniobras que el [filtro]
     * calla (rotondas, salidas de autopista) no dan ninguna de sus dos fases.
     */
    fun candidatas(
        posicionM: Double,
        vMs: Double,
        filtro: FiltroIndicaciones = FiltroIndicaciones(),
    ): List<Frase> {
        val v = vMs.coerceAtLeast(0.0)
        val inminenteM = ventanaInminenteM(v)
        val preavisoM = ventanaPreavisoM(v)
        return maniobras.indices.mapNotNull { i ->
            val maniobra = maniobras[i]
            val punto = posicionesM[i]
            val distancia = punto - posicionM
            when {
                maniobra.tipo == TipoManiobra.SALIDA || distancia < 0.0 || !filtro.permite(maniobra.tipo) -> {
                    null
                }

                distancia <= inminenteM -> {
                    Frase("maniobra-$i-inminente", texto(i, conVia = false), TipoFrase.MANIOBRA, punto)
                }

                distancia <= preavisoM -> {
                    preaviso(i, punto, distancia)
                }

                else -> {
                    null
                }
            }
        }
    }

    /**
     * Todos los textos que puede tomar el preaviso de las próximas [maximo] maniobras aún por delante de [posicionM]
     * (sin salida ni las que el [filtro] calla) yendo a [vMs]: de menor a mayor distancia, desde «a cien»
     * hasta lo que cabe en la ventana de preaviso. El texto de [candidatas] cambia cada ~100 m y todos salen de aquí,
     * así que se pueden sintetizar una sola vez por adelantado en vez de uno por cruce y sobre la marcha.
     */
    fun textosPreaviso(
        posicionM: Double,
        vMs: Double,
        filtro: FiltroIndicaciones = FiltroIndicaciones(),
        maximo: Int,
    ): List<String> {
        val v = vMs.coerceAtLeast(0.0)
        val desde = (ventanaInminenteM(v) / CIENTO_M).roundToInt().coerceAtLeast(1)
        val hasta = (ventanaPreavisoM(v) / CIENTO_M).roundToInt()
        return maniobras.indices
            .filter { posicionesM[it] > posicionM && maniobras[it].tipo != TipoManiobra.SALIDA }
            .filter { filtro.permite(maniobras[it].tipo) }
            .take(maximo)
            .flatMap { i -> (desde..hasta).map { textoPreaviso(i, it) } }
    }

    private fun ventanaInminenteM(v: Double) = (v * INMINENTE_S).coerceIn(INMINENTE_MIN_M, INMINENTE_MAX_M)

    private fun ventanaPreavisoM(v: Double) = (v * PREAVISO_S).coerceIn(PREAVISO_MIN_M, PREAVISO_MAX_M)

    private fun texto(
        indice: Int,
        conVia: Boolean,
    ): String {
        val maniobra = maniobras[indice]
        val intermedio = maniobra.tipo == TipoManiobra.DESTINO && indice < maniobras.lastIndex
        return if (intermedio) FrasesParada.PARADA else FrasesManiobra.frase(maniobra, conVia)
    }

    private fun preaviso(
        indice: Int,
        punto: Double,
        distanciaM: Double,
    ): Frase? {
        val cientos = (distanciaM / CIENTO_M).roundToInt()
        if (cientos < 1) return null
        return Frase("maniobra-$indice-preaviso", textoPreaviso(indice, cientos), TipoFrase.MANIOBRA, punto)
    }

    /** «a trescientos, cruce, derecha, Ma-10» para [cientos] centenares de metros. */
    private fun textoPreaviso(
        indice: Int,
        cientos: Int,
    ): String {
        val metros = cientos * CIENTO_M.toInt()
        val palabras = if (metros >= MIL_M) "mil" else FrasesNota.enPalabras(metros)
        return "a $palabras, ${texto(indice, conVia = true)}"
    }

    private companion object {
        const val PREAVISO_S = 15.0
        const val PREAVISO_MIN_M = 300.0
        const val PREAVISO_MAX_M = 1000.0
        const val INMINENTE_S = 5.0
        const val INMINENTE_MIN_M = 40.0
        const val INMINENTE_MAX_M = 200.0
        const val CIENTO_M = 100.0
        const val MIL_M = 1000
    }
}
