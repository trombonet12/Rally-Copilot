package app.trombito.rallycopilot.ui.mistramos

import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.domain.tramos.TipoTramo
import app.trombito.rallycopilot.motor.geo.PuntoGeo

/** Borrado a la espera de confirmarse: la fila ya no se ve, pero el tramo sigue en la base hasta que acabe el aviso. */
data class BorradoPendiente(
    val id: String,
    val nombre: String,
)

/** Las dos pestañas de «Mis tramos»: los viajes enteros y los tramos de cada carretera. */
enum class PestanaTramos(
    val tipo: TipoTramo,
) {
    VIAJES(TipoTramo.VIAJE),
    CARRETERAS(TipoTramo.VIA),
}

/**
 * Estado de «Mis tramos». [tramos] son los de la [pestana] elegida (ya con el [filtro] aplicado). [renombrando] es el
 * tramo cuyo diálogo está abierto.
 */
data class MisTramosUiState(
    val cargando: Boolean = true,
    val filtro: FiltroTramos = FiltroTramos.TODOS,
    val pestana: PestanaTramos = PestanaTramos.VIAJES,
    val tramos: List<ResumenTramo> = emptyList(),
    val renombrando: ResumenTramo? = null,
    val borradoPendiente: BorradoPendiente? = null,
    /** Nombre del tramo cuyo borrado definitivo falló (la base dio error): se avisa y el tramo sigue en la lista. */
    val falloBorrado: String? = null,
    /** Silueta de cada tramo para su miniatura (por id); un tramo sin entrada aún se está cargando. */
    val trazas: Map<String, List<PuntoGeo>> = emptyMap(),
)
