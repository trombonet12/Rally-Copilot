package app.trombito.rallycopilot.data.notas

import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.domain.edicion.EdicionNotasEnVivo
import app.trombito.rallycopilot.domain.edicion.EditorTramo
import app.trombito.rallycopilot.domain.edicion.TramoEditable
import app.trombito.rallycopilot.domain.escucha.EdicionSinTramo
import app.trombito.rallycopilot.domain.escucha.RepositorioEdiciones
import app.trombito.rallycopilot.domain.escucha.RepositorioMarcas
import app.trombito.rallycopilot.domain.notas.ProveedorNotas
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.motor.edicion.AccionEdicion
import app.trombito.rallycopilot.motor.edicion.FusionNotas
import app.trombito.rallycopilot.motor.edicion.NotasEditadas
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.tramos.Tramo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Genera la hoja automática de un tramo sobre su traza (la que el copiloto leería sin ediciones). */
fun interface GeneradorHojaTramo {
    suspend fun generar(tramo: Tramo): HojaNotas
}

/** La hoja automática de un tramo con los datos de vías instalados y la calibración de los recorridos de Joan. */
class GeneradorHojaTramoImpl(
    private val contexto: FuenteContextoNotas,
    private val calibrador: CalibradorTabla,
) : GeneradorHojaTramo {
    constructor(contexto: FuenteContextoNotas, recorridos: RepositorioRecorridos) :
        this(contexto, CalibradorRecorridos(recorridos))

    override suspend fun generar(tramo: Tramo): HojaNotas {
        val datos = contexto.contexto()
        val factor = calibrador.factor()
        return withContext(Dispatchers.Default) { generarHoja(RutaMedida(tramo.traza), datos, factor) }
    }
}

/**
 * Editor de las notas de un tramo guardado: la hoja automática se genera una vez por apertura y las ediciones del tramo
 * se le aplican siempre con [FusionNotas] (se guardan las ediciones, no el resultado: regenerar no las pierde).
 */
class EditorTramoImpl(
    private val tramos: RepositorioTramos,
    private val ediciones: RepositorioEdiciones,
    private val marcas: RepositorioMarcas,
    private val generador: GeneradorHojaTramo,
    private val ahoraMs: () -> Long,
) : EditorTramo {
    override fun observar(tramoId: String): Flow<TramoEditable?> =
        flow {
            val tramo = tramos.tramo(tramoId)
            if (tramo == null) {
                emit(null)
                return@flow
            }
            val automatica = generador.generar(tramo)
            val marcasTramo = marcas.deTramo(tramoId)
            emitAll(
                ediciones.observarDeTramo(tramoId).map { editadas ->
                    TramoEditable(tramo, FusionNotas.aplicar(automatica, { it }, editadas), marcasTramo, editadas.size)
                },
            )
        }

    override suspend fun editar(
        tramoId: String,
        posicionM: Double,
        accion: AccionEdicion,
    ) = ediciones.guardar(NotasEditadas(tramoId, posicionM, accion, ahoraMs()))

    override suspend fun restaurar(tramoId: String) = ediciones.borrarDeTramo(tramoId)

    override suspend fun guardarCortes(
        tramoId: String,
        sectores: List<Double>,
        microsectores: List<Double>,
    ) = tramos.guardarCortes(tramoId, sectores, microsectores)
}

/**
 * Edición desde el mapa: convierte la nota tocada (posición en la hoja vigente) a la traza del tramo activo o
 * reconocido, a menos de [RepositorioEdiciones.DISTANCIA_ANCLAJE_M] de ella; si no hay tramo, queda pendiente con su
 * coordenada hasta que se cree el tramo de la conducción.
 */
class EdicionNotasEnVivoImpl(
    private val notas: ProveedorNotas,
    private val crono: ProveedorCrono,
    private val tramos: RepositorioTramos,
    private val ediciones: RepositorioEdiciones,
    private val ahoraMs: () -> Long,
) : EdicionNotasEnVivo {
    override suspend fun editar(
        posicionHojaM: Double,
        accion: AccionEdicion,
    ): Boolean {
        val enCurso = notas.notas.value ?: return false
        val punto = enCurso.puntoEn?.invoke(posicionHojaM)
        val tramoId =
            enCurso.tramoReconocimiento
                ?: crono.crono.value
                    ?.takeIf { it.resultado == null }
                    ?.tramo
                    ?.id
        val anclada = tramoId != null && anclarAlTramo(tramoId, punto, accion)
        if (!anclada) ediciones.guardarSinTramo(EdicionSinTramo(accion, posicionHojaM, punto, ahoraMs()))
        return true
    }

    /** Guarda la edición en los metros del tramo si [punto] cae sobre su traza; false si no. */
    private suspend fun anclarAlTramo(
        tramoId: String,
        punto: PuntoGeo?,
        accion: AccionEdicion,
    ): Boolean {
        val tramo = tramos.tramo(tramoId)
        val proyeccion = if (tramo != null && punto != null) RutaMedida(tramo.traza).proyectar(punto) else null
        if (tramo == null || proyeccion == null || proyeccion.distanciaM > RepositorioEdiciones.DISTANCIA_ANCLAJE_M) {
            return false
        }
        ediciones.guardar(NotasEditadas(tramo.id, proyeccion.posicionM, accion, ahoraMs()))
        return true
    }
}
