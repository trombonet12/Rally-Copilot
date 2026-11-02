package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.data.db.EdicionNotaEntity
import app.trombito.rallycopilot.data.db.EdicionesDao
import app.trombito.rallycopilot.data.db.MarcaEntity
import app.trombito.rallycopilot.data.db.MarcasDao
import app.trombito.rallycopilot.data.notas.RutaMedida
import app.trombito.rallycopilot.domain.escucha.ContextoConduccion
import app.trombito.rallycopilot.domain.escucha.EdicionPendiente
import app.trombito.rallycopilot.domain.escucha.EdicionSinTramo
import app.trombito.rallycopilot.domain.escucha.MarcaConPosicion
import app.trombito.rallycopilot.domain.escucha.ProveedorContextoConduccion
import app.trombito.rallycopilot.domain.escucha.RepositorioEdiciones
import app.trombito.rallycopilot.domain.escucha.RepositorioMarcas
import app.trombito.rallycopilot.domain.escucha.comoAccion
import app.trombito.rallycopilot.domain.notas.ProveedorNotas
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import app.trombito.rallycopilot.motor.edicion.AccionEdicion
import app.trombito.rallycopilot.motor.edicion.CodecEdiciones
import app.trombito.rallycopilot.motor.edicion.Marca
import app.trombito.rallycopilot.motor.edicion.NotasEditadas
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.Tramo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Ediciones de notas en Room (tabla `edicion_nota`). Las dictadas con tramo activo se guardan ya ligadas a él; las
 * dictadas sin tramo quedan con su coordenada hasta [anclarPendientes], al cerrar la conducción. Una fila con una
 * acción que ya no se sabe leer se ignora.
 */
class RepositorioEdicionesRoom(
    private val dao: EdicionesDao,
) : RepositorioEdiciones {
    override suspend fun publicar(edicion: EdicionPendiente) {
        val accion = edicion.orden.comoAccion(edicion.posicionM) ?: return
        dao.insertar(
            fila(edicion.tramoId, edicion.posicionM, edicion.posicion, accion, edicion.tiempoMs),
        )
    }

    override suspend fun guardar(edicion: NotasEditadas) {
        dao.insertar(fila(edicion.tramoId, edicion.posicionM, null, edicion.accion, edicion.marcaTiempoMs))
    }

    override suspend fun guardarSinTramo(edicion: EdicionSinTramo) {
        dao.insertar(fila(null, edicion.posicionM, edicion.posicion, edicion.accion, edicion.marcaTiempoMs))
    }

    override fun observarDeTramo(tramoId: String): Flow<List<NotasEditadas>> =
        dao.observarDeTramo(tramoId).map { filas -> filas.mapNotNull { it.aNotasEditadas() } }

    override fun observarPendientes(): Flow<List<EdicionSinTramo>> =
        dao.observarPendientes().map { filas -> filas.mapNotNull { it.aSinTramo() } }

    override suspend fun borrarDeTramo(tramoId: String) = dao.borrarDeTramo(tramoId)

    override suspend fun anclarPendientes(tramo: Tramo?) {
        val pendientes = dao.pendientes()
        if (pendientes.isEmpty()) return
        if (tramo != null) {
            val traza = RutaMedida(tramo.traza)
            for (fila in pendientes) {
                val lat = fila.lat
                val lon = fila.lon
                if (lat == null || lon == null) continue
                val proyeccion = traza.proyectar(PuntoGeo(lat, lon))
                if (proyeccion.distanciaM <= RepositorioEdiciones.DISTANCIA_ANCLAJE_M) {
                    dao.actualizar(
                        fila.copy(tramoId = tramo.id, posicionM = proyeccion.posicionM, lat = null, lon = null),
                    )
                }
            }
        }
        dao.borrarPendientes()
    }

    private fun fila(
        tramoId: String?,
        posicionM: Double,
        posicion: PuntoGeo?,
        accion: AccionEdicion,
        marcaTiempoMs: Long,
    ) = EdicionNotaEntity(
        tramoId = tramoId,
        posicionM = posicionM,
        lat = posicion?.takeIf { tramoId == null }?.lat,
        lon = posicion?.takeIf { tramoId == null }?.lon,
        accion = CodecEdiciones.tipo(accion),
        nota = CodecEdiciones.nota(accion),
        marcaTiempo = marcaTiempoMs,
    )

    private fun EdicionNotaEntity.aNotasEditadas(): NotasEditadas? {
        val id = tramoId
        val decodificada = CodecEdiciones.accion(accion, nota)
        return if (id != null && decodificada != null) NotasEditadas(id, posicionM, decodificada, marcaTiempo) else null
    }

    private fun EdicionNotaEntity.aSinTramo(): EdicionSinTramo? {
        val accion = CodecEdiciones.accion(accion, nota) ?: return null
        val punto = if (lat != null && lon != null) PuntoGeo(lat, lon) else null
        return EdicionSinTramo(accion, posicionM, punto, marcaTiempo)
    }
}

/** Marcas de «marca aquí» en Room (tabla `marca`). */
class RepositorioMarcasRoom(
    private val dao: MarcasDao,
) : RepositorioMarcas {
    override suspend fun guardar(
        marca: Marca,
        posicion: PuntoGeo?,
    ) {
        dao.insertar(
            MarcaEntity(
                posicionM = marca.posicionM,
                tiempoMs = marca.tiempoMs,
                tramoId = marca.tramoId,
                lat = posicion?.lat,
                lon = posicion?.lon,
            ),
        )
    }

    override suspend fun deTramo(tramoId: String): List<MarcaConPosicion> =
        dao.deTramo(tramoId).map {
            val punto = if (it.lat != null && it.lon != null) PuntoGeo(it.lat, it.lon) else null
            MarcaConPosicion(Marca(it.posicionM, it.tiempoMs, it.tramoId), punto)
        }
}

/**
 * El contexto de las órdenes sale de lo que ya publica la conducción: con un tramo cronometrándose, los metros
 * recorridos del tramo; en reconocimiento, la posición sobre la hoja (la traza del tramo); si no, la posición sobre
 * la hoja de notas. Sin ninguno de los dos (no se conduce o aún no hay
 * hoja) no hay contexto.
 */
class ContextoConduccionImpl(
    private val notas: ProveedorNotas,
    private val crono: ProveedorCrono,
) : ProveedorContextoConduccion {
    override fun actual(): ContextoConduccion? {
        val enCurso = notas.notas.value
        val reconocimiento = enCurso?.tramoReconocimiento
        val activo =
            if (reconocimiento != null) null else crono.crono.value?.takeIf { it.resultado == null && it.tramo != null }
        val posicionM = activo?.progresoM ?: enCurso?.posicionM ?: return null
        val posicion = enCurso?.let { it.puntoEn?.invoke(it.posicionM) }
        return ContextoConduccion(posicionM, activo?.tramo?.id ?: reconocimiento, posicion)
    }
}
