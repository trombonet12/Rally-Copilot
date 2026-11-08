package app.trombito.rallycopilot.data.ruta

import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.ruta.Ruta

/** Una ruta candidata de Valhalla con sus metros «de curvas» ya medidos (`PuntuacionCurvas`). */
internal data class Candidata(
    val ruta: Ruta,
    val curvasM: Double,
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
 * Por construcción Curvas ≥ Equilibrada ≥ Rápida en metros de curvas. Si dos perfiles eligen la misma ruta, el
 * resultado es el mismo objeto [Candidata] (la vista previa lo avisa como «misma ruta»).
 */
internal object SelectorPerfiles {
    const val FACTOR_MAX_CURVAS = 1.6
    const val FACTOR_MAX_EQUILIBRADA = 1.25
    const val MINUTOS_EXTRA_MIN = 0.5
    private const val SEGUNDOS_POR_MINUTO = 60.0

    fun elegir(
        perfil: PerfilRuta,
        rapida: Candidata,
        otras: List<Candidata>,
    ): Candidata =
        when (perfil) {
            PerfilRuta.MAS_RAPIDA -> rapida
            PerfilRuta.SINUOSA -> masCurvas(rapida, otras)
            PerfilRuta.EQUILIBRADA -> mejorRelacion(rapida, otras)
        }

    private fun masCurvas(
        rapida: Candidata,
        otras: List<Candidata>,
    ): Candidata {
        val tope = FACTOR_MAX_CURVAS * rapida.ruta.tiempoS
        return (listOf(rapida) + otras)
            .filter { it.ruta.tiempoS <= tope }
            .sortedBy { it.ruta.tiempoS }
            .maxByOrNull { it.curvasM } ?: rapida
    }

    private fun mejorRelacion(
        rapida: Candidata,
        otras: List<Candidata>,
    ): Candidata {
        val tope = FACTOR_MAX_EQUILIBRADA * rapida.ruta.tiempoS
        return otras
            .filter { it.ruta.tiempoS <= tope && it.curvasM > rapida.curvasM }
            .maxByOrNull { relacion(rapida, it) } ?: rapida
    }

    private fun relacion(
        rapida: Candidata,
        c: Candidata,
    ): Double {
        val extraMin = ((c.ruta.tiempoS - rapida.ruta.tiempoS) / SEGUNDOS_POR_MINUTO).coerceAtLeast(MINUTOS_EXTRA_MIN)
        return (c.curvasM - rapida.curvasM) / extraMin
    }
}
