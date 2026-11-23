package app.trombito.rallycopilot.ui.miscarreteras

import androidx.compose.runtime.Immutable
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.motor.analisis.FichaCarretera
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.SentidoVia
import app.trombito.rallycopilot.ui.mapa.CALOR_GEOJSON_VACIO
import app.trombito.rallycopilot.ui.mapa.ModoCalor
import app.trombito.rallycopilot.ui.mistramos.GrupoCarretera

/**
 * Estado de la ficha de una carretera. [ficha] es null mientras carga y, ya cargado, si ningún viaje pasa por ella
 * ([noEncontrada]). [grupos] son sus tramos de vía por sentido (con el destino al que se va), [regla] los mismos sobre
 * una regla de kilómetros y [seleccionado] el tramo tocado en ella. El mapa se pinta como en «Mis carreteras» pero solo
 * con las vías de esta carretera.
 */
@Immutable
data class FichaCarreteraUiState(
    val cargando: Boolean = true,
    val error: Boolean = false,
    val ficha: FichaCarretera? = null,
    val grupos: List<GrupoCarretera> = emptyList(),
    val regla: ReglaCarretera? = null,
    val seleccionado: String? = null,
    val modoCalor: ModoCalor = ModoCalor.USO,
    val trazos: List<TrazoCarretera> = emptyList(),
    val geoJson: String = CALOR_GEOJSON_VACIO,
    val esquinas: List<PuntoGeo> = emptyList(),
    val estiloMapaJson: String? = null,
) {
    /** Cargado y sin ningún viaje por esta carretera. */
    val noEncontrada: Boolean get() = !cargando && !error && ficha == null

    /** El destino al que se va en cada sentido, para los sentidos de los que algún tramo lo sabe. */
    val destinos: Map<SentidoVia, String>
        get() =
            buildMap {
                grupos.forEach { grupo -> grupo.destino?.let { putIfAbsent(grupo.sentido, it) } }
            }

    /** Los tramos de todos los grupos, en el orden en que se enseñan. */
    val tramos: List<ResumenTramo> get() = grupos.flatMap { it.tramos }
}
