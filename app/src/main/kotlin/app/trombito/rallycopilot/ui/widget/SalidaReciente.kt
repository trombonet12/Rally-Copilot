package app.trombito.rallycopilot.ui.widget

import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.flow.first

/** La última salida cronometrada: el tramo, lo que tardó la pasada más reciente, su mejor tiempo y dónde sale. */
data class SalidaReciente(
    val tramoId: String,
    val nombre: String,
    val tiempoMs: Long,
    val mejorMs: Long,
    val salida: PuntoGeo,
)

/**
 * El tramo usado más recientemente que tenga pasadas, con su pasada más reciente y su mejor tiempo (la misma regla que
 * Inicio); null si no hay ninguno o si el tramo ya no se puede leer.
 */
suspend fun RepositorioTramos.ultimaSalidaReciente(): SalidaReciente? {
    val tramos = observarResumenes(FiltroTramos.TODOS).first()
    return tramos.firstOrNull { it.pasadas > 0 && it.mejorTotalMs != null }?.let { salidaDe(it) }
}

private suspend fun RepositorioTramos.salidaDe(resumen: ResumenTramo): SalidaReciente? {
    val mejor = resumen.mejorTotalMs
    // Las pasadas llegan con la más reciente primero.
    val ultima = pasadasDetalladas(resumen.id).firstOrNull()
    val salida = tramo(resumen.id)?.traza?.firstOrNull()
    return if (mejor != null && ultima != null && salida != null) {
        SalidaReciente(resumen.id, resumen.nombre, ultima.totalMs, mejor, salida)
    } else {
        null
    }
}
