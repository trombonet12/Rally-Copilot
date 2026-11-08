package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.NotaVisible
import app.trombito.rallycopilot.motor.notas.TipoAviso

/** Convierte las notas visibles de la hoja en frases del copiloto. */
object CandidatasNotas {
    /** Grado más suave que el modo simplificado sigue cantando (grados 1–3). */
    const val GRADO_SIMPLIFICADO_MAX = 3

    /** Avisos sueltos de peligro que el modo simplificado conserva (además de los que son `esCuidado`). */
    private val AVISOS_PELIGRO =
        setOf(TipoAviso.STOP, TipoAviso.CEDA, TipoAviso.PASO_A_NIVEL, TipoAviso.ESTRECHA, TipoAviso.SALTO)

    /**
     * Frases [TipoFrase.NOTA] de las [visibles] que ya toca cantar ([NotaVisible.activa]). La id es la posición del
     * elemento (`nota-<posición>`), estable mientras la hoja no cambie, salvo que [idDe] dé otra (p. ej. una
     * independiente del origen de la hoja, para que regenerarla no repita lo dicho); el texto es
     * [ElementoHoja.frase], con el grado simplificado si [simplificada].
     *
     * Con [simplificada] además se filtra (decisión de Joan, 2026-11-08: «grados 1–3 y peligros»). Se canta:
     * - la nota de grado 1–3, la horquilla (sin grado) y la escuadra (cuentan como cerradas);
     * - la nota de cualquier grado con `cuidado`, con el modificador [Modificador.CIERRA] (la curva se cierra: es el
     *   único modificador que avisa de peligro; larga, muy larga y abre solo describen la forma) o con algún aviso
     *   unido de peligro ([esPeligro]);
     * - el aviso suelto de peligro: `esCuidado` (resalto, badén, cuidado), stop, ceda, paso a nivel, estrecha y salto.
     * El resto (curvas suaves 4–6, puentes, túneles, rotondas, rasantes…) no se canta.
     */
    fun de(
        visibles: List<NotaVisible>,
        simplificada: Boolean,
        idDe: (ElementoHoja) -> String = { "nota-${it.posicionM}" },
    ): List<Frase> =
        visibles
            .filter { it.activa && (!simplificada || seCantaSimplificada(it.elemento)) }
            .map {
                val elemento = it.elemento
                val texto =
                    when (elemento) {
                        is ElementoHoja.DeNota -> elemento.frase(simplificada)
                        is ElementoHoja.DeAviso -> elemento.frase
                    }
                Frase(idDe(elemento), texto, TipoFrase.NOTA, elemento.posicionM)
            }

    /** `true` si [elemento] sobrevive al modo simplificado (grados 1–3 y peligros). */
    internal fun seCantaSimplificada(elemento: ElementoHoja): Boolean =
        when (elemento) {
            is ElementoHoja.DeAviso -> {
                esPeligro(elemento.aviso)
            }

            is ElementoHoja.DeNota -> {
                val nota = elemento.nota
                val cerrada =
                    nota.grado == null ||
                        nota.grado <= GRADO_SIMPLIFICADO_MAX ||
                        Modificador.ESCUADRA in nota.modificadores
                cerrada ||
                    nota.cuidado ||
                    Modificador.CIERRA in nota.modificadores ||
                    elemento.avisosUnidos.any { esPeligro(it) }
            }
        }

    /** `true` si [aviso] es de peligro: `esCuidado`, stop, ceda, paso a nivel, estrecha o salto. */
    internal fun esPeligro(aviso: Aviso): Boolean = aviso.tipo.esCuidado || aviso.tipo in AVISOS_PELIGRO
}
