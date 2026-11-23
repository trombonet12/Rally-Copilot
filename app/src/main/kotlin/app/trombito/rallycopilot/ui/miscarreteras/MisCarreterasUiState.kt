package app.trombito.rallycopilot.ui.miscarreteras

import androidx.compose.runtime.Immutable
import app.trombito.rallycopilot.motor.analisis.CurvasPorGrado
import app.trombito.rallycopilot.motor.analisis.FichaCarretera
import app.trombito.rallycopilot.motor.analisis.OrdenCarreteras
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.mapa.CALOR_GEOJSON_VACIO
import app.trombito.rallycopilot.ui.mapa.ModoCalor

/**
 * Trazo de una vía conducida con las veces que se ha pasado por ella y su velocidad media en marcha (null si los
 * recorridos no traían tiempos). Es lo que pinta el mapa de calor sin mapa base.
 */
@Immutable
data class TrazoCarretera(
    val puntos: List<PuntoGeo>,
    val pasadas: Int,
    val velMediaKmh: Double? = null,
)

/**
 * Estado de «Mis carreteras». El mapa de calor se pinta con MapLibre sobre el estilo offline ([estiloMapaJson]) con el
 * [geoJson] del [modoCalor] ya construido y encuadrado en [esquinas]; sin estilo cae al dibujo sin base de [trazos].
 * [fichas] ya viene en el [orden] elegido.
 */
@Immutable
data class MisCarreterasUiState(
    val cargando: Boolean = true,
    val error: Boolean = false,
    val recorridos: Int = 0,
    val curvas: CurvasPorGrado = CurvasPorGrado.VACIO,
    val trazos: List<TrazoCarretera> = emptyList(),
    val fichas: List<FichaCarretera> = emptyList(),
    val orden: OrdenCarreteras = OrdenCarreteras.USO,
    val modoCalor: ModoCalor = ModoCalor.USO,
    val geoJson: String = CALOR_GEOJSON_VACIO,
    /** Esquina suroeste y noreste de lo conducido, para encuadrar el mapa; vacío sin vías. */
    val esquinas: List<PuntoGeo> = emptyList(),
    val estiloMapaJson: String? = null,
    /** Había datos de vías para relacionar los recorridos con carreteras. */
    val hayVias: Boolean = true,
) {
    /** Cargado y sin ningún recorrido terminado. */
    val vacio: Boolean get() = !cargando && !error && recorridos == 0
}
