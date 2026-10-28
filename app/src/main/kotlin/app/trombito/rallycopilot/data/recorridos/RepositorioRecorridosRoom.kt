package app.trombito.rallycopilot.data.recorridos

import app.trombito.rallycopilot.data.db.RecorridoEntity
import app.trombito.rallycopilot.data.db.RecorridoPuntoEntity
import app.trombito.rallycopilot.data.db.RecorridosDao
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Recorridos en Room. [reloj] (ms epoch) solo se usa para el inicio y para cerrar un recorrido sin puntos. */
class RepositorioRecorridosRoom(
    private val dao: RecorridosDao,
    private val dispatcher: CoroutineDispatcher,
    private val reloj: () -> Long = System::currentTimeMillis,
) : RepositorioRecorridos {
    override suspend fun empezar(destinoNombre: String?): Long =
        withContext(dispatcher) {
            dao.insertar(
                RecorridoEntity(inicioMs = reloj(), finMs = null, distanciaM = 0.0, destinoNombre = destinoNombre),
            )
        }

    override suspend fun anadirPuntos(
        id: Long,
        puntos: List<PuntoRecorrido>,
    ) {
        if (puntos.isEmpty()) return
        withContext(dispatcher) {
            dao.anadirPuntos(id, puntos.map { it.aEntidad(id) })
        }
    }

    override suspend fun terminar(id: Long): Recorrido? =
        withContext(dispatcher) {
            val recorrido = dao.recorrido(id) ?: return@withContext null
            val puntos = dao.puntos(id)
            val distancia = puntos.map { PuntoGeo(it.lat, it.lon) }.longitudMetros()
            if (distancia < RepositorioRecorridos.DISTANCIA_MINIMA_M) {
                dao.borrar(id)
                return@withContext null
            }
            val fin = puntos.last().tiempoMs
            dao.cerrar(id, fin, distancia)
            recorrido.copy(finMs = fin, distanciaM = distancia).aDominio()
        }

    override fun listar(): Flow<List<Recorrido>> =
        dao.observarTerminados().map { lista ->
            lista.mapNotNull {
                it.aDominio()
            }
        }

    override suspend fun puntos(id: Long): List<PuntoRecorrido> =
        withContext(dispatcher) {
            dao.puntos(id).map { PuntoRecorrido(it.lat, it.lon, it.tiempoMs, it.velocidadMs) }
        }

    override suspend fun borrar(id: Long) {
        withContext(dispatcher) { dao.borrar(id) }
    }
}

private fun PuntoRecorrido.aEntidad(recorridoId: Long) =
    RecorridoPuntoEntity(
        recorridoId = recorridoId,
        orden = 0,
        lat = lat,
        lon = lon,
        tiempoMs = tiempoMs,
        velocidadMs = velocidadMs,
    )

private fun RecorridoEntity.aDominio(): Recorrido? =
    finMs?.let { Recorrido(id, inicioMs, it, distanciaM, destinoNombre) }
