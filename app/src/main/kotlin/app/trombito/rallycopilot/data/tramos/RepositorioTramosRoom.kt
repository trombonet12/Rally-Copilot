package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.data.db.PasadaEntity
import app.trombito.rallycopilot.data.db.TramosDao
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.TramoCronometrable
import app.trombito.rallycopilot.domain.tramos.TramoGuardado
import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.tramos.Tramo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Tramos y pasadas en Room. Una fila ilegible (datos dañados) se ignora en las lecturas. */
class RepositorioTramosRoom(
    private val dao: TramosDao,
    private val dispatcher: CoroutineDispatcher,
) : RepositorioTramos {
    override fun observar(): Flow<List<TramoGuardado>> =
        combine(dao.observarTramos(), dao.observarResumenes()) { tramos, resumenes ->
            val porTramo = resumenes.associateBy { it.tramoId }
            tramos.mapNotNull { fila ->
                fila.aTramo()?.let {
                    val resumen = porTramo[fila.id]
                    TramoGuardado(it, fila.nombre, fila.creadoEn, resumen?.pasadas ?: 0, resumen?.mejorMs)
                }
            }
        }

    override fun observarMejorPasada(tramoId: String): Flow<Pasada?> =
        dao.observarMejorPasada(tramoId).map { fila -> fila?.let { leer(it) } }

    override suspend fun cronometrables(): List<TramoCronometrable> =
        withContext(dispatcher) {
            val mejores = dao.mejoresPasadas().associateBy { it.tramoId }
            dao.tramos().mapNotNull { fila ->
                fila.aTramo()?.let { t -> TramoCronometrable(t, mejores[fila.id]?.aPasada(t.cortesExactosM())) }
            }
        }

    override suspend fun mejorPasada(tramoId: String): Pasada? =
        withContext(dispatcher) { dao.mejorPasada(tramoId)?.let { leer(it) } }

    override suspend fun crear(
        tramo: Tramo,
        nombre: String?,
        creadoEnMs: Long,
        primera: Pasada,
        inicioMs: Long,
    ) {
        require(primera.tramoId == tramo.id) { "La pasada es de otro tramo" }
        withContext(dispatcher) {
            dao.crear(tramo.aEntidad(nombre, creadoEnMs), primera.aEntidad(inicioMs, tramo.cortesExactosM()))
        }
    }

    override suspend fun guardarPasada(
        pasada: Pasada,
        inicioMs: Long,
    ): Long =
        withContext(dispatcher) {
            val tramo = checkNotNull(dao.tramo(pasada.tramoId)?.aTramo()) { "El tramo de la pasada no existe" }
            dao.insertarPasada(pasada.aEntidad(inicioMs, tramo.cortesExactosM()))
        }

    override suspend fun borrar(tramoId: String) {
        withContext(dispatcher) { dao.borrar(tramoId) }
    }

    private suspend fun leer(fila: PasadaEntity): Pasada? =
        dao.tramo(fila.tramoId)?.aTramo()?.let { fila.aPasada(it.cortesExactosM()) }
}
