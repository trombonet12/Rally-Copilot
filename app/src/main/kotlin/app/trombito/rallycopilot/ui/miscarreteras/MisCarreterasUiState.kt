package app.trombito.rallycopilot.ui.miscarreteras

import androidx.compose.runtime.Immutable
import app.trombito.rallycopilot.motor.analisis.Carretera
import app.trombito.rallycopilot.motor.analisis.CurvasPorGrado
import app.trombito.rallycopilot.motor.geo.PuntoGeo

/** Trazo de una vía conducida y las veces que se ha pasado por ella. */
@Immutable
data class TrazoCarretera(
    val puntos: List<PuntoGeo>,
    val pasadas: Int,
)

/** Estado de «Mis carreteras». */
@Immutable
data class MisCarreterasUiState(
    val cargando: Boolean = true,
    val error: Boolean = false,
    val recorridos: Int = 0,
    val metrosTotales: Double = 0.0,
    val curvas: CurvasPorGrado = CurvasPorGrado.VACIO,
    val trazos: List<TrazoCarretera> = emptyList(),
    val carreteras: List<Carretera> = emptyList(),
    /** Había datos de vías para relacionar los recorridos con carreteras. */
    val hayVias: Boolean = true,
) {
    /** Cargado y sin ningún recorrido terminado. */
    val vacio: Boolean get() = !cargando && !error && recorridos == 0
}
