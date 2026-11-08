package app.trombito.rallycopilot.ui.analisis

import app.trombito.rallycopilot.motor.analisis.AnalisisPasada
import app.trombito.rallycopilot.motor.analisis.PosicionRepeticion
import app.trombito.rallycopilot.motor.analisis.PuntoSerie

/** Una pasada con la que se puede comparar la analizada. [esMejor]: la de menor tiempo del tramo. */
data class OpcionComparada(
    val id: Long,
    val inicioMs: Long,
    val totalMs: Long,
    val esMejor: Boolean,
)

/** El mejor tiempo de un sector del tramo y [pasadaInicioMs] (epoch) de la pasada que lo aportó. */
data class SectorOptimoUi(
    val numero: Int,
    val tiempoMs: Long,
    val pasadaInicioMs: Long,
)

/**
 * Tramo óptimo teórico: [totalMs] suma los mejores sectores y [gananciaMs] (≥ 0) es lo que se ganaría frente a la
 * mejor pasada completa (null si ninguna cubre el tramo entero).
 */
data class OptimoUi(
    val totalMs: Long,
    val gananciaMs: Long?,
    val sectores: List<SectorOptimoUi>,
)

/** Por qué no hay análisis que mostrar. */
enum class ProblemaAnalisis {
    /** El tramo o la pasada ya no existen. */
    NO_ENCONTRADA,

    /** Los datos de la pasada no permiten analizarla. */
    ILEGIBLE,
}

/**
 * Estado de la pantalla de análisis de una pasada. [comparadaId] es la pasada contra la que se compara (null = sin
 * comparar) y [opciones] las que se pueden elegir (todas menos la analizada). [posicion] es la del marcador de la
 * repetición sobre el mapa y [cursorM] la distancia del cursor de los gráficos (ambos nulos hasta que se pone en
 * marcha la repetición o se toca un gráfico).
 */
data class AnalisisUiState(
    val cargando: Boolean = true,
    val problema: ProblemaAnalisis? = null,
    val tramoNombre: String? = null,
    val inicioMs: Long = 0L,
    val analisis: AnalisisPasada? = null,
    val opciones: List<OpcionComparada> = emptyList(),
    val comparadaId: Long? = null,
    val velocidadComparada: List<PuntoSerie> = emptyList(),
    val estiloMapaJson: String? = null,
    val repeticion: EstadoRepeticion = EstadoRepeticion(),
    val posicion: PosicionRepeticion? = null,
    val cursorM: Double? = null,
    /** Óptimo teórico del tramo; null con menos de dos pasadas (no habría nada que juntar). */
    val optimo: OptimoUi? = null,
) {
    /** La pasada comparada elegida, si la hay. */
    val comparada: OpcionComparada? get() = opciones.firstOrNull { it.id == comparadaId }
}

/** Callbacks de la pantalla de análisis. */
data class AccionesAnalisis(
    val onVolver: () -> Unit = {},
    /** Elige la pasada comparada; null = no comparar. */
    val onComparar: (Long?) -> Unit = {},
    val onAlternarRepeticion: () -> Unit = {},
    val onVelocidad: (VelocidadRepeticion) -> Unit = {},
    /** Mueve la repetición al instante dado (ms desde la salida). */
    val onPosicion: (Long) -> Unit = {},
    /** Toque o arrastre en un gráfico: distancia (m desde la salida) del cursor. */
    val onCursor: (Double) -> Unit = {},
)
