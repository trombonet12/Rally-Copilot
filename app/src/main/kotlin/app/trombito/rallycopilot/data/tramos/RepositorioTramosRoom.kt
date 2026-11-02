package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.data.db.FilaResumenTramo
import app.trombito.rallycopilot.data.db.PasadaEntity
import app.trombito.rallycopilot.data.db.TramosDao
import app.trombito.rallycopilot.domain.tramos.EstadisticasPerfil
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.OrigenPasada
import app.trombito.rallycopilot.domain.tramos.PasadaDetallada
import app.trombito.rallycopilot.domain.tramos.PuntoPasada
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
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
@Suppress("TooManyFunctions") // implementa el contrato completo del almacén de tramos
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

    override fun observarResumenes(filtro: FiltroTramos): Flow<List<ResumenTramo>> =
        dao.observarFilasResumen().map { filas ->
            filas
                .filter {
                    when (filtro) {
                        FiltroTramos.TODOS -> true
                        FiltroTramos.REPETIDOS -> it.pasadas >= PASADAS_PARA_REPETIDO
                        FiltroTramos.FAVORITOS -> it.favorito
                    }
                }.map { it.aResumen() }
        }

    override fun observarPerfil(): Flow<EstadisticasPerfil> =
        combine(dao.observarTotalesRecorridos(), dao.observarContadores()) { recorridos, contadores ->
            EstadisticasPerfil(
                recorridos = recorridos.recorridos,
                distanciaTotalM = recorridos.distanciaM,
                primerRecorridoMs = recorridos.primeroMs,
                ultimoRecorridoMs = recorridos.ultimoMs,
                tramos = contadores.tramos,
                pasadas = contadores.pasadas,
            )
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

    override suspend fun tramo(tramoId: String): Tramo? = withContext(dispatcher) { dao.tramo(tramoId)?.aTramo() }

    override suspend fun pasadasDetalladas(tramoId: String): List<PasadaDetallada> =
        withContext(dispatcher) {
            val tramo = dao.tramo(tramoId)?.aTramo() ?: return@withContext emptyList()
            val filas = dao.pasadas(tramoId)
            val mejorId = filas.minWithOrNull(compareBy({ it.totalMs }, { it.id }))?.id
            filas.mapNotNull { fila ->
                fila.aPasada(tramo.cortesExactosM())?.let { pasada ->
                    PasadaDetallada(fila.id, fila.inicio, pasada, puntosDe(fila), fila.id == mejorId)
                }
            }
        }

    override suspend fun mejorPasadaDetallada(tramoId: String): PasadaDetallada? =
        pasadasDetalladas(tramoId).firstOrNull { it.esMejor }

    override suspend fun crear(
        tramo: Tramo,
        nombre: String?,
        nombreAutomatico: String?,
        creadoEnMs: Long,
        primera: Pasada,
        origen: OrigenPasada,
    ) {
        require(primera.tramoId == tramo.id) { "La pasada es de otro tramo" }
        withContext(dispatcher) {
            dao.crear(
                tramo.aEntidad(nombre?.takeIf { it.isNotBlank() }, creadoEnMs, nombreAutomatico),
                primera.aEntidad(origen, tramo.cortesExactosM()),
            )
        }
    }

    override suspend fun guardarPasada(
        pasada: Pasada,
        origen: OrigenPasada,
    ): Long =
        withContext(dispatcher) {
            val tramo = checkNotNull(dao.tramo(pasada.tramoId)?.aTramo()) { "El tramo de la pasada no existe" }
            dao.anadirPasada(pasada.aEntidad(origen, tramo.cortesExactosM()))
        }

    override suspend fun renombrar(
        tramoId: String,
        nombre: String?,
    ) {
        val limpio = nombre?.trim()?.takeIf { it.isNotEmpty() }
        withContext(dispatcher) { check(dao.renombrar(tramoId, limpio) > 0) { "El tramo no existe" } }
    }

    override suspend fun marcarFavorito(
        tramoId: String,
        favorito: Boolean,
    ) {
        withContext(dispatcher) { check(dao.marcarFavorito(tramoId, favorito) > 0) { "El tramo no existe" } }
    }

    override suspend fun borrar(tramoId: String) {
        withContext(dispatcher) { dao.borrar(tramoId) }
    }

    private suspend fun leer(fila: PasadaEntity): Pasada? =
        dao.tramo(fila.tramoId)?.aTramo()?.let { fila.aPasada(it.cortesExactosM()) }

    /** Los puntos del recorrido que caen en el intervalo de la pasada; vacío si no se guardó de dónde salió. */
    private suspend fun puntosDe(fila: PasadaEntity): List<PuntoPasada> {
        val recorrido = fila.recorridoId
        val desde = fila.puntosDesdeMs
        val hasta = fila.puntosHastaMs
        if (recorrido == null || desde == null || hasta == null) return emptyList()
        return dao.puntosEntre(recorrido, desde, hasta).map { PuntoPasada(it.lat, it.lon, it.tiempoMs, it.velocidadMs) }
    }

    private fun FilaResumenTramo.aResumen(): ResumenTramo {
        val propio = nombre?.takeIf { it.isNotBlank() }
        return ResumenTramo(
            id = id,
            nombre = propio ?: nombreAutomatico?.takeIf { it.isNotBlank() } ?: NOMBRE_POR_DEFECTO,
            nombrePropio = propio,
            longitudM = longitudM,
            pasadas = pasadas,
            mejorTotalMs = mejorMs,
            ultimoUsoMs = ultimoUso,
            favorito = favorito,
        )
    }

    private companion object {
        const val PASADAS_PARA_REPETIDO = 2
        const val NOMBRE_POR_DEFECTO = "Tramo sin nombre"
    }
}
