package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.data.db.MarcaEntity
import app.trombito.rallycopilot.data.db.MarcasDao
import app.trombito.rallycopilot.domain.escucha.ContextoConduccion
import app.trombito.rallycopilot.domain.escucha.EdicionPendiente
import app.trombito.rallycopilot.domain.escucha.ProveedorContextoConduccion
import app.trombito.rallycopilot.domain.escucha.RepositorioEdiciones
import app.trombito.rallycopilot.domain.escucha.RepositorioMarcas
import app.trombito.rallycopilot.domain.notas.ProveedorNotas
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import app.trombito.rallycopilot.motor.edicion.Marca
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Ediciones pedidas por voz, solo en memoria (provisional: la persistencia y la fusión son de otro componente). */
class RepositorioEdicionesMemoria : RepositorioEdiciones {
    private val _pendientes = MutableStateFlow<List<EdicionPendiente>>(emptyList())
    override val pendientes: StateFlow<List<EdicionPendiente>> = _pendientes.asStateFlow()

    override fun publicar(edicion: EdicionPendiente) {
        _pendientes.update { it + edicion }
    }

    override fun consumir(ediciones: Collection<EdicionPendiente>) {
        _pendientes.update { it - ediciones.toSet() }
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
}

/**
 * El contexto de las órdenes sale de lo que ya publica la conducción: con un tramo cronometrándose, los metros
 * recorridos del tramo; si no, la posición sobre la hoja de notas. Sin ninguno de los dos (no se conduce o aún no hay
 * hoja) no hay contexto.
 */
class ContextoConduccionImpl(
    private val notas: ProveedorNotas,
    private val crono: ProveedorCrono,
) : ProveedorContextoConduccion {
    override fun actual(): ContextoConduccion? {
        val enCurso = notas.notas.value
        val activo = crono.crono.value?.takeIf { it.resultado == null && it.tramo != null }
        val posicionM = activo?.progresoM ?: enCurso?.posicionM ?: return null
        val posicion = enCurso?.let { it.puntoEn?.invoke(it.posicionM) }
        return ContextoConduccion(posicionM, activo?.tramo?.id, posicion)
    }
}
