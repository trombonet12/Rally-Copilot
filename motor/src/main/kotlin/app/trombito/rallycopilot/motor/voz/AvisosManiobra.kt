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
 *   inminente; la distancia se redondea a cientos y, si saldría menos de 100 m, no se canta;
 * - inminente (la frase de [FrasesManiobra]): desde `v · 5 s` (acotado a 40–200 m) hasta la maniobra.
 */
class AvisosManiobra(
    private val maniobras: List<Maniobra>,
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
        val inminenteM = (v * INMINENTE_S).coerceIn(INMINENTE_MIN_M, INMINENTE_MAX_M)
        val preavisoM = (v * PREAVISO_S).coerceIn(PREAVISO_MIN_M, PREAVISO_MAX_M)
        return maniobras.indices.mapNotNull { i ->
            val maniobra = maniobras[i]
            val punto = posicionesM[i]
            val distancia = punto - posicionM
            when {
                maniobra.tipo == TipoManiobra.SALIDA || distancia < 0.0 || !filtro.permite(maniobra.tipo) -> {
                    null
                }

                distancia <= inminenteM -> {
                    Frase("maniobra-$i-inminente", texto(i), TipoFrase.MANIOBRA, punto)
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

    private fun texto(indice: Int): String {
        val maniobra = maniobras[indice]
        val intermedio = maniobra.tipo == TipoManiobra.DESTINO && indice < maniobras.lastIndex
        return if (intermedio) FrasesParada.PARADA else FrasesManiobra.frase(maniobra)
    }

    private fun preaviso(
        indice: Int,
        punto: Double,
        distanciaM: Double,
    ): Frase? {
        val cientos = (distanciaM / CIENTO_M).roundToInt()
        if (cientos < 1) return null
        val metros = cientos * CIENTO_M.toInt()
        val palabras = if (metros >= MIL_M) "mil" else FrasesNota.enPalabras(metros)
        val texto = "a $palabras, ${texto(indice)}"
        return Frase("maniobra-$indice-preaviso", texto, TipoFrase.MANIOBRA, punto)
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
