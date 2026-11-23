package app.trombito.rallycopilot.domain.carreteras

import app.trombito.rallycopilot.motor.analisis.AnalisisMarcha
import app.trombito.rallycopilot.motor.analisis.ResumenCarreteras
import app.trombito.rallycopilot.motor.analisis.VelocidadVia
import kotlinx.coroutines.flow.Flow

/**
 * Lo que enseñan «Mis carreteras» y la capa de calor del mapa: el [resumen], si había vías de OSM para asignar los
 * recorridos ([hayVias]) y la [velocidades] real en marcha por vía (vacía sin vías o sin tiempos en los recorridos).
 * [ultimoViaje] es la marcha por vía del recorrido más reciente (null si no hay recorridos); ya está sumada en
 * [velocidades].
 */
data class MisCarreteras(
    val resumen: ResumenCarreteras,
    val hayVias: Boolean,
    val velocidades: List<VelocidadVia> = emptyList(),
    val ultimoViaje: AnalisisMarcha? = null,
)

/** Estadísticas de «Mis carreteras» sobre los recorridos terminados. */
interface CalculoCarreteras {
    /** Emite el resumen al empezar y cada vez que cambia la lista de recorridos terminados. */
    fun observar(): Flow<MisCarreteras>
}
