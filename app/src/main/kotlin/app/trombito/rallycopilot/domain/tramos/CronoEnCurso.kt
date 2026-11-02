package app.trombito.rallycopilot.domain.tramos

import kotlinx.coroutines.flow.StateFlow

/** Tramo que se está cronometrando: lo que la UI necesita para la barra de progreso y las marcas. */
data class TramoCronometrado(
    val id: String,
    val nombre: String?,
    val longitudM: Double,
    val metaM: Double,
    val sectoresM: List<Double>,
    val microsectoresM: List<Double>,
)

/** Tipo de corte de un tramo. */
enum class TipoCorte { SECTOR, MICROSECTOR }

/**
 * Un sector o microsector ya cerrado. [tiempoMs] es el acumulado desde la salida hasta su final y [duracionMs] lo que
 * costó él solo. [diferenciaMs] compara ese sector con el mismo de la mejor pasada y [deltaAcumuladoMs] el acumulado
 * con el de la mejor (negativo = más rápido); null si no hay mejor pasada con la que comparar.
 */
data class ParcialCrono(
    val tipo: TipoCorte,
    val numero: Int,
    val distanciaM: Double,
    val tiempoMs: Long,
    val duracionMs: Long,
    val diferenciaMs: Long?,
    val deltaAcumuladoMs: Long?,
)

/**
 * Cómo acabó una pasada: [deltaMs] frente a la mejor anterior (null si no la había); [nuevoMejor] si la mejora.
 * [tramoId] y [pasadaId] identifican la pasada ya guardada para abrir su análisis desde la tarjeta de meta; null
 * mientras no se haya guardado (sin ellos la tarjeta no ofrece «Ver análisis»).
 */
data class ResultadoCrono(
    val totalMs: Long,
    val deltaMs: Long?,
    val nuevoMejor: Boolean,
    val mejorAnteriorMs: Long?,
    val tramoId: String? = null,
    val pasadaId: Long? = null,
)

/**
 * El cronómetro de la conducción en curso, para el HUD. Con [tramo] es un tramo guardado (ya sea que se arrancó en él o
 * que se entró a mitad de la conducción) y [hayMejorPasada] dice si hay un tiempo de referencia («tramo conocido»: HUD
 * completo con [deltaMs]); sin [tramo] es solo el cronómetro de la conducción desde Conducir, en [transcurridoMs].
 *
 * [transcurridoMs] se refiere al instante [actualizadoEnMs] (ms epoch): la UI puede seguir contando entre fijaciones
 * sumando `ahora - actualizadoEnMs` mientras [resultado] sea null. [progresoM] es la distancia recorrida del tramo (0
 * sin tramo), [parciales] los sectores y microsectores cerrados por orden de cierre y [ultimaMarca] el último. Al
 * cruzar la meta llega el [resultado] y el cronómetro se queda en el total hasta que se entre en otro tramo.
 */
data class CronoEnCurso(
    val tramo: TramoCronometrado?,
    val transcurridoMs: Long,
    val actualizadoEnMs: Long,
    val progresoM: Double = 0.0,
    val deltaMs: Long? = null,
    val parciales: List<ParcialCrono> = emptyList(),
    val ultimaMarca: ParcialCrono? = null,
    val hayMejorPasada: Boolean = false,
    val resultado: ResultadoCrono? = null,
)

/** Cronómetro en vivo de la sesión de navegación; null si no hay conducción en curso. */
interface ProveedorCrono {
    val crono: StateFlow<CronoEnCurso?>
}
