package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.NotaVisible
import app.trombito.rallycopilot.motor.notas.TipoAviso

/** Convierte las notas visibles de la hoja en frases del copiloto. */
object CandidatasNotas {
    /** Grado más suave que el modo simplificado sigue cantando siempre (grados 1–3). */
    const val GRADO_SIMPLIFICADO_MAX = 3

    /** Una nota de grado 4 con radio mínimo de este valor (m) o menos es una curva lenta: el simplificado la canta. */
    const val RADIO_LENTA_M = 45.0

    /** Una nota de grado 6 con radio mínimo mayor que esto (m) es casi una recta: no se canta. */
    const val RADIO_CASI_RECTA_M = 500.0

    private const val GRADO_LENTA = 4
    private const val GRADO_CASI_RECTA = 6

    /** Avisos sueltos de peligro que el modo simplificado conserva (además de los que son `esCuidado`). */
    private val AVISOS_PELIGRO =
        setOf(
            TipoAviso.STOP,
            TipoAviso.CEDA,
            TipoAviso.PASO_A_NIVEL,
            TipoAviso.ESTRECHA,
            TipoAviso.SALTO,
            TipoAviso.RASANTE,
            TipoAviso.CAMBIO_RASANTE,
            TipoAviso.COMPRESION,
            TipoAviso.ROTONDA,
            TipoAviso.SEMAFORO,
        )

    /**
     * Frases [TipoFrase.NOTA] de las [visibles] que ya toca cantar ([NotaVisible.activa]). La id es la posición del
     * elemento (`nota-<posición>`), estable mientras la hoja no cambie, salvo que [idDe] dé otra (p. ej. una
     * independiente del origen de la hoja, para que regenerarla no repita lo dicho); el texto es
     * [ElementoHoja.frase], con el grado simplificado si [simplificada].
     *
     * Con [simplificada] además se filtra (decisión de Joan, 2026-11-08: «grados 1–3 y peligros»). Se canta:
     * - la nota de grado 1–3, la horquilla (sin grado) y la escuadra (cuentan como cerradas), y la de grado 4 con radio
     *   mínimo de [RADIO_LENTA_M] o menos (curva lenta aunque el ángulo sea moderado);
     * - la nota de cualquier grado con `cuidado`, con el modificador [Modificador.CIERRA] (la curva se cierra: es el
     *   único modificador que avisa de peligro; larga, muy larga y abre solo describen la forma) o con algún aviso
     *   unido de peligro ([esPeligro]);
     * - el aviso suelto de peligro: `esCuidado` (resalto, badén, cuidado), stop, ceda, paso a nivel, estrecha, salto,
     *   rasante, cambio de rasante, compresión, rotonda y semáforo.
     * El resto (curvas suaves 4–6, puentes, túneles, cruces…) no se canta.
     *
     * En cualquier modo no se canta la nota de grado 6 casi recta ([esCasiRecta]).
     */
    fun de(
        visibles: List<NotaVisible>,
        simplificada: Boolean,
        idDe: (ElementoHoja) -> String = { "nota-${it.posicionM}" },
    ): List<Frase> =
        visibles
            .filter { it.activa && seCanta(it.elemento, simplificada) }
            .map {
                val elemento = it.elemento
                val texto =
                    when (elemento) {
                        is ElementoHoja.DeNota -> elemento.frase(simplificada)
                        is ElementoHoja.DeAviso -> elemento.frase
                    }
                Frase(idDe(elemento), texto, TipoFrase.NOTA, elemento.posicionM)
            }

    /** `true` si [elemento] se canta en el modo dado (el simplificado filtra más; la casi recta nunca se canta). */
    internal fun seCanta(
        elemento: ElementoHoja,
        simplificada: Boolean,
    ): Boolean = !esCasiRecta(elemento) && (!simplificada || seCantaSimplificada(elemento))

    /**
     * Nota de grado 6 con radio mínimo de más de [RADIO_CASI_RECTA_M] m, sin texto propio, cuidado ni avisos (una nota
     * con algo que decir se canta aunque sea casi recta).
     */
    internal fun esCasiRecta(elemento: ElementoHoja): Boolean =
        elemento is ElementoHoja.DeNota &&
            elemento.nota.grado == GRADO_CASI_RECTA &&
            (elemento.nota.radioMinM ?: 0.0) > RADIO_CASI_RECTA_M &&
            elemento.nota.textoPropio == null &&
            !elemento.nota.cuidado &&
            elemento.avisosUnidos.isEmpty() &&
            elemento.sobrePuente == null &&
            elemento.sobreRasante == null

    /** `true` si [elemento] sobrevive al modo simplificado (grados 1–3, curvas lentas y peligros). */
    internal fun seCantaSimplificada(elemento: ElementoHoja): Boolean =
        when (elemento) {
            is ElementoHoja.DeAviso -> {
                esPeligro(elemento.aviso)
            }

            is ElementoHoja.DeNota -> {
                val nota = elemento.nota
                val lenta =
                    nota.grado == GRADO_LENTA && nota.radioMinM != null && nota.radioMinM <= RADIO_LENTA_M
                val cerrada =
                    nota.grado == null ||
                        nota.grado <= GRADO_SIMPLIFICADO_MAX ||
                        lenta ||
                        Modificador.ESCUADRA in nota.modificadores
                cerrada ||
                    nota.cuidado ||
                    Modificador.CIERRA in nota.modificadores ||
                    elemento.avisosUnidos.any { esPeligro(it) }
            }
        }

    /** `true` si [aviso] es de peligro `esCuidado` o de la lista de peligros (rasantes, rotonda, semáforo…). */
    internal fun esPeligro(aviso: Aviso): Boolean = aviso.tipo.esCuidado || aviso.tipo in AVISOS_PELIGRO
}
