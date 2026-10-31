package app.trombito.rallycopilot.data.navegacion

import app.trombito.rallycopilot.data.db.GuiadoPendienteDao
import app.trombito.rallycopilot.data.db.GuiadoPendienteEntity
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.GuiadoPendiente
import app.trombito.rallycopilot.domain.navegacion.RepositorioGuiadoPendiente
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Guiado pendiente en Room (una fila). [reloj] (ms epoch) sella el momento de guardar y decide la caducidad
 * ([CADUCIDAD_MS]) cada vez que llega un valor.
 */
class RepositorioGuiadoPendienteRoom(
    private val dao: GuiadoPendienteDao,
    private val dispatcher: CoroutineDispatcher,
    private val reloj: () -> Long = System::currentTimeMillis,
) : RepositorioGuiadoPendiente {
    override val pendiente: Flow<GuiadoPendiente?> =
        dao.observar().map { fila -> fila?.aDominio()?.takeIf { reloj() - it.desdeMs <= CADUCIDAD_MS } }

    override suspend fun guardar(
        destino: Destino,
        perfil: PerfilRuta,
    ) {
        withContext(dispatcher) {
            dao.guardar(
                GuiadoPendienteEntity(
                    destinoNombre = destino.nombre,
                    lat = destino.punto.lat,
                    lon = destino.punto.lon,
                    perfil = perfil.name,
                    desdeMs = reloj(),
                ),
            )
        }
    }

    override suspend fun borrar() {
        withContext(dispatcher) { dao.borrar() }
    }

    companion object {
        /** Más tiempo que esto y el guiado se considera abandonado. */
        const val CADUCIDAD_MS = 12L * 60 * 60 * 1000
    }
}

private fun GuiadoPendienteEntity.aDominio(): GuiadoPendiente? {
    val perfil = PerfilRuta.entries.firstOrNull { it.name == perfil } ?: return null
    return GuiadoPendiente(Destino(destinoNombre, PuntoGeo(lat, lon)), perfil, desdeMs)
}
