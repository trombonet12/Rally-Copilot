package app.trombito.rallycopilot.domain.perfil

import app.trombito.rallycopilot.motor.analisis.Conclusion
import kotlinx.coroutines.flow.Flow

/** El último viaje terminado: su [id] abre el resumen; [inicioMs] es epoch; [destinoNombre] es null en modo libre. */
data class UltimoViaje(
    val id: Long,
    val inicioMs: Long,
    val distanciaM: Double,
    val destinoNombre: String?,
)

/**
 * Lo que enseña el perfil del piloto: la cabecera ([viajes] terminados, [kmTotales] de todos ellos y [primerViajeMs],
 * epoch), el [ultimoViaje] y las [conclusiones] en el orden del tablero (vacías si aún no hay datos suficientes).
 * Los kilómetros salen solo de los viajes: los tramos de vía guardados no suman kilómetros.
 */
data class TableroPiloto(
    val viajes: Int = 0,
    val kmTotales: Double = 0.0,
    val primerViajeMs: Long? = null,
    val ultimoViaje: UltimoViaje? = null,
    val conclusiones: List<Conclusion> = emptyList(),
)

/** Cálculo del tablero del piloto sobre los viajes terminados y las pasadas guardadas. */
interface CalculoTablero {
    /** Emite el tablero al empezar y cada vez que cambian los viajes o las pasadas. */
    fun observar(): Flow<TableroPiloto>
}
