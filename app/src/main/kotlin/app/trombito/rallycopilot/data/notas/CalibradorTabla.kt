package app.trombito.rallycopilot.data.notas

import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos

/** Factor de calibración de [TablaGrados] con la velocidad real de los recorridos guardados; null si no hay datos. */
interface CalibradorTabla {
    suspend fun factor(): Double?
}

/**
 * Calibra con los [RepositorioRecorridos.MAX_CALIBRACION] recorridos terminados más recientes. El factor lo guarda el
 * almacén al cerrar cada recorrido ([RepositorioRecorridos.factorCalibracion]): al empezar a conducir se lee un valor,
 * no los puntos de 30 recorridos.
 */
class CalibradorRecorridos(
    private val recorridos: RepositorioRecorridos,
) : CalibradorTabla {
    override suspend fun factor(): Double? = recorridos.factorCalibracion()
}
