package app.trombito.rallycopilot.domain.resumen

import app.trombito.rallycopilot.motor.analisis.ResumenViaje
import app.trombito.rallycopilot.motor.analisis.VelocidadVia
import app.trombito.rallycopilot.motor.analisis.VelocidadVias
import kotlinx.coroutines.flow.StateFlow

/** El resumen de un viaje ya armado: [inicioMs] es epoch y [destinoNombre] el del guiado (null en libre). */
data class ViajeResumido(
    val recorridoId: Long,
    val inicioMs: Long,
    val destinoNombre: String?,
    val resumen: ResumenViaje,
)

/** Arma el resumen de un recorrido terminado, fuera del hilo principal. */
fun interface CalculoResumenViaje {
    /** El resumen del recorrido [recorridoId]; null si no existe o no tiene puntos suficientes. */
    suspend fun resumenDe(recorridoId: Long): ViajeResumido?
}

/** Lo que la sesión avisa al cerrar un recorrido de más de 1 km: su id y cuándo ocurrió ([terminadoMs], epoch). */
data class ViajeTerminado(
    val recorridoId: Long,
    val terminadoMs: Long,
)

/**
 * Canal en memoria entre el cierre del recorrido (servicio de navegación) y la pantalla que enseña su resumen. Guarda
 * el último viaje terminado hasta que la UI lo [consumir]; si el proceso muere, se pierde (el resumen sigue pudiéndose
 * abrir desde la lista de viajes).
 */
interface ViajesTerminados {
    val ultimo: StateFlow<ViajeTerminado?>

    fun publicar(viaje: ViajeTerminado)

    /** La UI ya lo atendió: lo quita si sigue siendo [recorridoId] el pendiente. */
    fun consumir(recorridoId: Long)
}

/** Cómo asignar los puntos de un viaje a las vías de OSM y lo que el resto de viajes llevaba acumulado en ellas. */
class ContextoVias(
    val coincidenciaDe: VelocidadVias.CoincidenciaDe,
    val historico: Map<Long, VelocidadVia>,
)

/** Las vías y el histórico de velocidad por vía, para comparar un viaje con los demás. */
fun interface FuenteContextoVias {
    /** Contexto para el viaje [recorridoId]: el histórico sale de todos los recorridos terminados menos ese. */
    suspend fun contextoSin(recorridoId: Long): ContextoVias
}
