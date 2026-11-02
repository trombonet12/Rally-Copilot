package app.trombito.rallycopilot.domain.escucha

import app.trombito.rallycopilot.motor.edicion.AccionEdicion
import app.trombito.rallycopilot.motor.edicion.Marca
import app.trombito.rallycopilot.motor.edicion.NotasEditadas
import app.trombito.rallycopilot.motor.edicion.Orden
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.Tramo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Una edición de notas pedida por voz mientras se conduce ([Orden.Nota], [Orden.Quitar] u [Orden.Cuidado]) que aún
 * no se ha aplicado. [posicionM] son metros sobre la traza del [tramoId] (o sobre la hoja de notas si no hay tramo
 * activo); [posicion] es la coordenada si se conoce.
 */
data class EdicionPendiente(
    val orden: Orden,
    val posicionM: Double,
    val tramoId: String?,
    val posicion: PuntoGeo?,
    val tiempoMs: Long,
)

/** Edición sin tramo (dictada sin tramo activo): sitúa por [posicion] y espera a anclarse al tramo que se cree. */
data class EdicionSinTramo(
    val accion: AccionEdicion,
    val posicionM: Double,
    val posicion: PuntoGeo?,
    val marcaTiempoMs: Long,
)

/** La [AccionEdicion] que hace una orden de voz de edición; null si la orden no edita notas. */
fun Orden.comoAccion(posicionM: Double): AccionEdicion? =
    when (this) {
        is Orden.Nota -> AccionEdicion.Sustituir(aNota(posicionM))
        Orden.Quitar -> AccionEdicion.Quitar
        Orden.Cuidado -> AccionEdicion.Cuidado
        else -> null
    }

/**
 * Dónde se guardan las ediciones de notas, ligadas al tramo (siempre prevalecen sobre las automáticas, ver
 * `FusionNotas`). Las dictadas sin tramo activo quedan [pendientes] hasta [anclarPendientes].
 */
interface RepositorioEdiciones {
    /** Guarda una edición dictada: ligada a su tramo si lo trae; si no, pendiente (con su coordenada). */
    suspend fun publicar(edicion: EdicionPendiente)

    /** Guarda una edición ya anclada a un tramo (el editor de notas). */
    suspend fun guardar(edicion: NotasEditadas)

    /** Guarda una edición sin tramo (hecha sin tramo activo, desde el editor del mapa). */
    suspend fun guardarSinTramo(edicion: EdicionSinTramo)

    /** Ediciones del tramo por orden de [NotasEditadas.marcaTiempoMs], cada vez que cambian. */
    fun observarDeTramo(tramoId: String): Flow<List<NotasEditadas>>

    /** Ediciones sin tramo, cada vez que cambian. */
    fun observarPendientes(): Flow<List<EdicionSinTramo>>

    /** Deja las notas del tramo como las generó el copiloto (borra sus ediciones). */
    suspend fun borrarDeTramo(tramoId: String)

    /**
     * Cierre de la conducción: las pendientes que caen a menos de [DISTANCIA_ANCLAJE_M] de la traza de [tramo] pasan a
     * ser suyas (con los metros sobre su traza); las demás se descartan. Sin tramo (null) se descartan todas.
     */
    suspend fun anclarPendientes(tramo: Tramo?)

    companion object {
        /** Distancia máxima (m) de una edición pendiente a la traza del tramo para anclarse a él. */
        const val DISTANCIA_ANCLAJE_M = 40.0
    }
}

/** Dónde se guardan las [Marca]s de «marca aquí». [lat] y [lon] van juntas o ninguna. */
interface RepositorioMarcas {
    suspend fun guardar(
        marca: Marca,
        posicion: PuntoGeo?,
    )

    /** Las marcas del tramo con su coordenada (si se conoce), por posición. */
    suspend fun deTramo(tramoId: String): List<MarcaConPosicion>
}

/** Una [Marca] con la coordenada en que se dejó, si se conoce. */
data class MarcaConPosicion(
    val marca: Marca,
    val posicion: PuntoGeo?,
)

/**
 * Lo que las órdenes de voz necesitan saber de la conducción en curso; `null` si no hay conducción. Salen de las notas
 * y del cronómetro en vivo.
 */
data class ContextoConduccion(
    val posicionM: Double,
    val tramoId: String?,
    val posicion: PuntoGeo?,
)

fun interface ProveedorContextoConduccion {
    fun actual(): ContextoConduccion?
}

/** Ejecuta las órdenes de voz sobre la conducción y confirma por voz. */
interface ControlOrdenes {
    /** `true` mientras se escucha una orden (para animar el botón de micrófono). */
    val escuchando: StateFlow<Boolean>

    fun tienePermiso(): Boolean

    /** Escucha una orden, la ejecuta y confirma por voz; no hace nada si ya se está escuchando. */
    fun escucharOrden()
}
