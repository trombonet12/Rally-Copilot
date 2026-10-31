package app.trombito.rallycopilot.data.notas

import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.MuestraVelocidad
import app.trombito.rallycopilot.motor.notas.PerfilVelocidad
import app.trombito.rallycopilot.motor.notas.TablaGrados
import kotlinx.coroutines.flow.first

/** Factor de calibración de [TablaGrados] con la velocidad real de los recorridos guardados; null si no hay datos. */
interface CalibradorTabla {
    suspend fun factor(): Double?
}

/**
 * Calibra con los [MAX_RECORRIDOS] recorridos terminados más recientes ([PerfilVelocidad.calibrar]). Pesado: llamar
 * en un dispatcher de cómputo. Si la base de datos falla, no hay factor.
 */
class CalibradorRecorridos(
    private val recorridos: RepositorioRecorridos,
) : CalibradorTabla {
    override suspend fun factor(): Double? {
        val muestras =
            recorridos.listar().first().take(MAX_RECORRIDOS).map { r ->
                recorridos.puntos(r.id).map { MuestraVelocidad(PuntoGeo(it.lat, it.lon), it.velocidadMs) }
            }
        return PerfilVelocidad.calibrar(muestras, TablaGrados()).factor
    }

    companion object {
        const val MAX_RECORRIDOS = 30
    }
}
