package app.trombito.rallycopilot.data.ruta

import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.ruta.ComparadorRutas
import app.trombito.rallycopilot.motor.ruta.Ruta

/**
 * Una ruta candidata de Valhalla con sus metros «de curvas» ya medidos (`PuntuacionCurvas`) y los metros que recorre
 * por vías que Joan ya ha conducido ([conocidosM]; 0 si no hay histórico).
 */
internal data class Candidata(
    val ruta: Ruta,
    val curvasM: Double,
    val conocidosM: Double = 0.0,
)

/**
 * Elige, entre las rutas candidatas de una misma petición, la de cada perfil. Puro: sin motor ni disco.
 *
 * - Rápida: la candidata de referencia.
 * - Curvas ([PerfilRuta.SINUOSA]): la de más metros de curvas entre las que duran como mucho [FACTOR_MAX_CURVAS] veces
 *   la rápida; a igualdad, la más corta de duración.
 * - Equilibrada: entre las que duran como mucho [FACTOR_MAX_EQUILIBRADA] veces la rápida y tienen más curvas que ella,
 *   la de mejor relación metros de curvas ganados / minutos extra (con un mínimo de [MINUTOS_EXTRA_MIN] min en el
 *   divisor, para que una candidata igual de rápida no dé una relación infinita). Si ninguna gana curvas, la rápida.
 *
 * Preferencia suave por vías conocidas (H24): entre las candidatas que el perfil admite (mismos topes de duración y,
 * en Equilibrada, ganar curvas a la rápida; en Curvas, no tener menos curvas que la de siempre) y que duran como mucho
 * [FACTOR_MAX_CONOCIDAS] veces la que se elegiría sin histórico, gana la de más [Candidata.conocidosM]; a igualdad
 * (o sin ninguna conocida) la de siempre. En Rápida compiten todas las candidatas dentro de ese 10 %.
 *
 * Por construcción Curvas ≥ Equilibrada ≥ Rápida en metros de curvas.
 *
 * Nunca se enseña la misma ruta dos veces (decisión de Joan, 2026-11-23): «la misma» es por geometría
 * ([ComparadorRutas]), no por igualdad de objetos. Si la elegida de Equilibrada o de Curvas repite la de otro chip
 * anterior, se reintenta con más margen de duración (Equilibrada hasta [FACTOR_MAX_CURVAS], Curvas hasta
 * [FACTOR_MAX_CURVAS_REINTENTO]) excluyendo las repetidas; en Curvas, además, solo con más curvas que la rápida y que
 * Equilibrada (si no, no sería «Curvas»). Si ninguna candidata es distinta, devuelve la de siempre (repetida) y la
 * vista previa oculta ese chip.
 */
internal object SelectorPerfiles {
    const val FACTOR_MAX_CURVAS = 1.6
    const val FACTOR_MAX_CURVAS_REINTENTO = 2.0
    const val FACTOR_MAX_EQUILIBRADA = 1.25
    const val MINUTOS_EXTRA_MIN = 0.5
    const val FACTOR_MAX_CONOCIDAS = 1.10
    private const val SEGUNDOS_POR_MINUTO = 60.0
    private val FACTORES_REINTENTO_EQUILIBRADA = listOf(FACTOR_MAX_EQUILIBRADA, FACTOR_MAX_CURVAS)
    private val FACTORES_REINTENTO_CURVAS = listOf(FACTOR_MAX_CURVAS, FACTOR_MAX_CURVAS_REINTENTO)

    fun elegir(
        perfil: PerfilRuta,
        rapida: Candidata,
        otras: List<Candidata>,
    ): Candidata {
        val mostradaRapida = rapida.conMasConocidas(otras)
        return when (perfil) {
            PerfilRuta.MAS_RAPIDA -> mostradaRapida
            PerfilRuta.EQUILIBRADA -> equilibrada(rapida, otras, mostradaRapida)
            PerfilRuta.SINUOSA -> curvas(rapida, otras, mostradaRapida, equilibrada(rapida, otras, mostradaRapida))
        }
    }

    private fun equilibrada(
        rapida: Candidata,
        otras: List<Candidata>,
        mostradaRapida: Candidata,
    ): Candidata {
        val hoy =
            (mejorRelacion(rapida, otras, FACTOR_MAX_EQUILIBRADA) ?: rapida)
                .conMasConocidas(admitidasEquilibrada(rapida, otras))
        if (!hoy.repite(mostradaRapida)) return hoy
        return FACTORES_REINTENTO_EQUILIBRADA.firstNotNullOfOrNull {
            mejorRelacion(rapida, otras, it, excluir = listOf(mostradaRapida))
        } ?: hoy
    }

    private fun curvas(
        rapida: Candidata,
        otras: List<Candidata>,
        mostradaRapida: Candidata,
        equilibrada: Candidata,
    ): Candidata {
        val hoy =
            (masCurvas(rapida, otras, FACTOR_MAX_CURVAS) ?: rapida).let { base ->
                base.conMasConocidas(admitidasCurvas(rapida, otras).filter { it.curvasM >= base.curvasM })
            }
        val ocupadas = listOf(mostradaRapida, equilibrada)
        if (ocupadas.none { hoy.repite(it) }) return hoy
        val piso =
            if (equilibrada.repite(mostradaRapida)) rapida.curvasM else maxOf(rapida.curvasM, equilibrada.curvasM)
        return FACTORES_REINTENTO_CURVAS.firstNotNullOfOrNull { masCurvas(rapida, otras, it, ocupadas, piso) } ?: hoy
    }

    private fun admitidasCurvas(
        rapida: Candidata,
        otras: List<Candidata>,
    ): List<Candidata> = (listOf(rapida) + otras).filter { it.ruta.tiempoS <= FACTOR_MAX_CURVAS * rapida.ruta.tiempoS }

    private fun admitidasEquilibrada(
        rapida: Candidata,
        otras: List<Candidata>,
    ): List<Candidata> =
        otras.filter { it.ruta.tiempoS <= FACTOR_MAX_EQUILIBRADA * rapida.ruta.tiempoS && it.curvasM > rapida.curvasM }

    /** La admitida con más metros conocidos dentro del [FACTOR_MAX_CONOCIDAS] de `this`; a empate, `this`. */
    private fun Candidata.conMasConocidas(admitidas: List<Candidata>): Candidata {
        val tope = FACTOR_MAX_CONOCIDAS * ruta.tiempoS
        return admitidas
            .filter { it.ruta.tiempoS <= tope && it.conocidosM > conocidosM }
            .sortedBy { it.ruta.tiempoS }
            .maxByOrNull { it.conocidosM } ?: this
    }

    /**
     * La de más curvas entre `rapida` y [otras] que dura como mucho [factor] veces la rápida, tiene más de [piso]
     * metros de curvas y no repite ninguna de [excluir]; a igualdad, la más corta. Null si no hay ninguna.
     */
    private fun masCurvas(
        rapida: Candidata,
        otras: List<Candidata>,
        factor: Double,
        excluir: List<Candidata> = emptyList(),
        piso: Double = Double.NEGATIVE_INFINITY,
    ): Candidata? {
        val tope = factor * rapida.ruta.tiempoS
        return (listOf(rapida) + otras)
            .filter { it.ruta.tiempoS <= tope && it.curvasM > piso && excluir.none { e -> it.repite(e) } }
            .sortedBy { it.ruta.tiempoS }
            .maxByOrNull { it.curvasM }
    }

    /**
     * De [otras], las que duran como mucho [factor] veces la rápida, ganan curvas a ella y no repiten ninguna de
     * [excluir]: la de mejor relación curvas/minutos extra. Null si no hay ninguna.
     */
    private fun mejorRelacion(
        rapida: Candidata,
        otras: List<Candidata>,
        factor: Double,
        excluir: List<Candidata> = emptyList(),
    ): Candidata? {
        val tope = factor * rapida.ruta.tiempoS
        return otras
            .filter {
                it.ruta.tiempoS <= tope && it.curvasM > rapida.curvasM && excluir.none { e -> it.repite(e) }
            }.maxByOrNull { relacion(rapida, it) }
    }

    private fun relacion(
        rapida: Candidata,
        c: Candidata,
    ): Double {
        val extraMin = ((c.ruta.tiempoS - rapida.ruta.tiempoS) / SEGUNDOS_POR_MINUTO).coerceAtLeast(MINUTOS_EXTRA_MIN)
        return (c.curvasM - rapida.curvasM) / extraMin
    }
}

/** Misma ruta por geometría ([ComparadorRutas]): lo que se vería dos veces en la vista previa. */
internal fun Candidata.repite(otra: Candidata): Boolean = this === otra || ComparadorRutas.sonIguales(ruta, otra.ruta)
