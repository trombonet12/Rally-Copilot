package app.trombito.rallycopilot.ui.tramos

import app.trombito.rallycopilot.domain.tramos.ParcialCrono
import app.trombito.rallycopilot.domain.tramos.TipoCorte
import app.trombito.rallycopilot.domain.tramos.TramoCronometrado

/** Fracción recorrida del tramo hasta la meta, acotada a 0..1. */
internal fun fraccionProgreso(
    progresoM: Double,
    metaM: Double,
): Float = if (metaM <= 0.0) 0f else (progresoM / metaM).coerceIn(0.0, 1.0).toFloat()

/** Una marca de la barra de progreso: un corte de sector o de microsector. [signo] es null si aún no se ha pasado. */
internal data class MarcaBarra(
    val fraccion: Float,
    val tipo: TipoCorte,
    val numero: Int,
    val signo: SignoDelta?,
)

/**
 * Las marcas de la barra: los cortes de sector y de microsector del [tramo] sobre la distancia hasta la meta. Las ya
 * cerradas ([parciales]) llevan el signo de su diferencia con la mejor pasada.
 */
internal fun marcasDeBarra(
    tramo: TramoCronometrado,
    parciales: List<ParcialCrono>,
): List<MarcaBarra> {
    fun marcas(
        cortes: List<Double>,
        tipo: TipoCorte,
    ) = cortes.mapIndexedNotNull { i, d ->
        val fraccion = fraccionProgreso(d, tramo.metaM)
        if (d <= 0.0 || d >= tramo.metaM) {
            null
        } else {
            val cerrado = parciales.firstOrNull { it.tipo == tipo && it.numero == i + 1 }
            MarcaBarra(fraccion, tipo, i + 1, cerrado?.diferenciaMs?.let(::signoDelta))
        }
    }
    return marcas(tramo.sectoresM, TipoCorte.SECTOR) + marcas(tramo.microsectoresM, TipoCorte.MICROSECTOR)
}
