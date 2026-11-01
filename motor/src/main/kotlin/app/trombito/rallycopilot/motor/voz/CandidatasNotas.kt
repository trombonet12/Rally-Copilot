package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.NotaVisible

/** Convierte las notas visibles de la hoja en frases del copiloto. */
object CandidatasNotas {
    /**
     * Frases [TipoFrase.NOTA] de las [visibles] que ya toca cantar ([NotaVisible.activa]). La id es la posición del
     * elemento (`nota-<posición>`), estable mientras la hoja no cambie, salvo que [idDe] dé otra (p. ej. una
     * independiente del origen de la hoja, para que regenerarla no repita lo dicho); el texto es
     * [ElementoHoja.frase], con el grado simplificado si [simplificada].
     */
    fun de(
        visibles: List<NotaVisible>,
        simplificada: Boolean,
        idDe: (ElementoHoja) -> String = { "nota-${it.posicionM}" },
    ): List<Frase> =
        visibles.filter { it.activa }.map {
            val elemento = it.elemento
            val texto =
                when (elemento) {
                    is ElementoHoja.DeNota -> elemento.frase(simplificada)
                    is ElementoHoja.DeAviso -> elemento.frase
                }
            Frase(idDe(elemento), texto, TipoFrase.NOTA, elemento.posicionM)
        }
}
