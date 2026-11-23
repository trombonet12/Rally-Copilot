package app.trombito.rallycopilot.domain.tramos

import app.trombito.rallycopilot.motor.tramos.TramoEnCamino
import kotlinx.coroutines.flow.Flow

/**
 * Los tramos conocidos que el camino de la conducción tiene por delante: [tramos] para pintarlos en el mapa (línea y
 * banderas; la misma lista, instancia incluida, hasta que se recalcula) y [finDeTramoM], los metros de aquí a la meta
 * más próxima por delante (null si el camino no llega a ninguna meta conocida).
 */
data class TramosPorDelante(
    val tramos: List<TramoEnCamino> = emptyList(),
    val finDeTramoM: Double? = null,
)

/**
 * Tramos guardados por los que pasa el camino de la conducción en curso: en guiado, la ruta restante; en libre, la
 * carretera prevista por delante. Vacío sin conducción o sin camino previsto.
 */
interface ProveedorTramosPorDelante {
    val porDelante: Flow<TramosPorDelante>
}
