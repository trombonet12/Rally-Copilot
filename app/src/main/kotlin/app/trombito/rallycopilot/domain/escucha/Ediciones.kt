package app.trombito.rallycopilot.domain.escucha

import app.trombito.rallycopilot.motor.edicion.Marca
import app.trombito.rallycopilot.motor.edicion.Orden
import app.trombito.rallycopilot.motor.geo.PuntoGeo
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

/**
 * Dónde se guardan las ediciones pedidas por voz. La fusión con las notas automáticas y su persistencia son de otro
 * componente: este solo las recoge. [pendientes] son las que aún no se han aplicado, en orden de llegada.
 */
interface RepositorioEdiciones {
    val pendientes: StateFlow<List<EdicionPendiente>>

    fun publicar(edicion: EdicionPendiente)

    /** Quita de las pendientes las ya aplicadas. */
    fun consumir(ediciones: Collection<EdicionPendiente>)
}

/** Dónde se guardan las [Marca]s de «marca aquí». [lat] y [lon] van juntas o ninguna. */
interface RepositorioMarcas {
    suspend fun guardar(
        marca: Marca,
        posicion: PuntoGeo?,
    )
}

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
