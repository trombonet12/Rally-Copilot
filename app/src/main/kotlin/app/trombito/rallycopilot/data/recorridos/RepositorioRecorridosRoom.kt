package app.trombito.rallycopilot.data.recorridos

import app.trombito.rallycopilot.data.db.CalibracionEntity
import app.trombito.rallycopilot.data.db.RecorridoEntity
import app.trombito.rallycopilot.data.db.RecorridoPuntoEntity
import app.trombito.rallycopilot.data.db.RecorridosDao
import app.trombito.rallycopilot.motor.geo.AcumuladorRecorrido
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.distanciaRecorrido
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Recorridos en Room. [reloj] (ms epoch) solo se usa para el inicio. Cada recorrido abierto en este proceso lleva su
 * distancia acumulada al ir llegando los puntos, así que cerrarlo no vuelve a leerlos; el de un proceso anterior
 * (huérfano) sí se lee entero. Al cerrar un recorrido se recalcula, en [alcance], el factor de calibración de las
 * notas y se guarda ([factorCalibracion]).
 */
class RepositorioRecorridosRoom(
    private val dao: RecorridosDao,
    private val dispatcher: CoroutineDispatcher,
    private val alcance: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
    private val reloj: () -> Long = System::currentTimeMillis,
) : RepositorioRecorridos {
    /** Distancia y último instante de los recorridos abiertos por este proceso. */
    private class Avance {
        val acumulador = AcumuladorRecorrido()
        var ultimoMs: Long? = null
    }

    private val abiertos = HashMap<Long, Avance>()

    /** Último cálculo de calibración lanzado al cerrar un recorrido (para esperarlo en las pruebas). */
    @Volatile
    internal var calibracionEnCurso: Job? = null

    override suspend fun empezar(destinoNombre: String?): Long =
        withContext(dispatcher) {
            val id =
                dao.insertar(
                    RecorridoEntity(inicioMs = reloj(), finMs = null, distanciaM = 0.0, destinoNombre = destinoNombre),
                )
            synchronized(abiertos) { abiertos[id] = Avance() }
            id
        }

    override suspend fun anadirPuntos(
        id: Long,
        puntos: List<PuntoRecorrido>,
    ) {
        if (puntos.isEmpty()) return
        withContext(dispatcher) {
            dao.anadirPuntos(id, puntos.map { it.aEntidad(id) })
            // Tras guardarlos: si el guardado falla, la distancia no cuenta puntos que no están.
            synchronized(abiertos) {
                abiertos[id]?.let { avance ->
                    puntos.forEach { avance.acumulador.anadir(PuntoGeo(it.lat, it.lon)) }
                    avance.ultimoMs = puntos.last().tiempoMs
                }
            }
        }
    }

    override suspend fun terminar(id: Long): Recorrido? =
        withContext(dispatcher) {
            val avance = synchronized(abiertos) { abiertos.remove(id) }
            val recorrido = dao.recorrido(id) ?: return@withContext null
            val distancia: Double
            val fin: Long?
            if (avance?.ultimoMs != null) {
                distancia = avance.acumulador.distanciaM
                fin = avance.ultimoMs
            } else {
                val puntos = dao.puntos(id)
                distancia = puntos.map { PuntoGeo(it.lat, it.lon) }.distanciaRecorrido()
                fin = puntos.lastOrNull()?.tiempoMs
            }
            if (fin == null || distancia < RepositorioRecorridos.DISTANCIA_MINIMA_M) {
                dao.borrar(id)
                return@withContext null
            }
            dao.cerrar(id, fin, distancia)
            calibracionEnCurso = alcance.launch { runCatching { calcularYGuardarCalibracion() } }
            recorrido.copy(finMs = fin, distanciaM = distancia).aDominio()
        }

    override suspend fun idsEnCurso(): List<Long> = withContext(dispatcher) { dao.idsEnCurso() }

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
        withContext(dispatcher) {
            synchronized(abiertos) { abiertos.remove(id) }
            dao.borrar(id)
        }
    }

    /**
     * El factor guardado si corresponde a los mismos recorridos que ahora; si no (se borró alguno, o el proceso murió
     * antes de guardarlo), se recalcula leyendo los puntos y se guarda.
     */
    override suspend fun factorCalibracion(): Double? =
        withContext(dispatcher) {
            val ids = dao.idsTerminadosRecientes(RepositorioRecorridos.MAX_CALIBRACION)
            val guardado = dao.calibracion()
            if (guardado != null && guardado.clave == claveDe(ids)) guardado.factor else calcularYGuardarCalibracion()
        }

    private suspend fun calcularYGuardarCalibracion(): Double? {
        val ids = dao.idsTerminadosRecientes(RepositorioRecorridos.MAX_CALIBRACION)
        val recorridos =
            ids.map { id ->
                dao.puntos(id).map { PuntoRecorrido(it.lat, it.lon, it.tiempoMs, it.velocidadMs) }
            }
        val factor = calcularFactorCalibracion(recorridos)
        dao.guardarCalibracion(CalibracionEntity(clave = claveDe(ids), factor = factor))
        return factor
    }

    private fun claveDe(ids: List<Long>) = ids.joinToString(",")
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
