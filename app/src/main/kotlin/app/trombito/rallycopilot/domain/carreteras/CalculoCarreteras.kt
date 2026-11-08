package app.trombito.rallycopilot.domain.carreteras

import app.trombito.rallycopilot.motor.analisis.ResumenCarreteras
import kotlinx.coroutines.flow.Flow

/** Lo que enseña «Mis carreteras»: el [resumen] y si había vías de OSM para asignar los recorridos ([hayVias]). */
data class MisCarreteras(
    val resumen: ResumenCarreteras,
    val hayVias: Boolean,
)

/** Estadísticas de «Mis carreteras» sobre los recorridos terminados. */
interface CalculoCarreteras {
    /** Emite el resumen al empezar y cada vez que cambia la lista de recorridos terminados. */
    fun observar(): Flow<MisCarreteras>
}
