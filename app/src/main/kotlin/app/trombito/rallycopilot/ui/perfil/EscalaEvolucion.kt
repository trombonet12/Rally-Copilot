package app.trombito.rallycopilot.ui.perfil

import app.trombito.rallycopilot.motor.analisis.PuntoEvolucion

/** Punto del gráfico en coordenadas normalizadas: [x] de 0 (primera fecha) a 1 y [y] de 0 (arriba, el mejor) a 1. */
data class PuntoEscalado(
    val pasadaId: String,
    val x: Float,
    val y: Float,
    val esRecord: Boolean,
)

/**
 * Escala del gráfico de evolución: [puntos] ya normalizados y los extremos reales para rotular los ejes
 * ([mejorMs] arriba, [peorMs] abajo; [desdeMs] y [hastaMs] son la primera y la última fecha).
 */
data class EscalaEvolucion(
    val puntos: List<PuntoEscalado>,
    val mejorMs: Long,
    val peorMs: Long,
    val desdeMs: Long,
    val hastaMs: Long,
)

private const val CENTRO = 0.5f

/**
 * Convierte la evolución de un tramo (en el orden de fechas) en coordenadas del gráfico. El tiempo mejor (menor) va
 * arriba, para que mejorar se vea como subir. El eje X es proporcional a la fecha; si todas coinciden se reparten por
 * orden y un solo punto queda en el centro. Con todos los tiempos iguales, la línea queda en el centro.
 */
fun escalarEvolucion(evolucion: List<PuntoEvolucion>): EscalaEvolucion {
    if (evolucion.isEmpty()) return EscalaEvolucion(emptyList(), 0L, 0L, 0L, 0L)
    val ordenada = evolucion.sortedBy { it.fechaMs }
    val mejor = ordenada.minOf { it.tiempoMs }
    val peor = ordenada.maxOf { it.tiempoMs }
    val desde = ordenada.first().fechaMs
    val hasta = ordenada.last().fechaMs
    val puntos =
        ordenada.mapIndexed { i, p ->
            PuntoEscalado(
                pasadaId = p.pasadaId,
                x = escalaX(i, ordenada.size, p.fechaMs, desde, hasta),
                y = if (peor == mejor) CENTRO else (p.tiempoMs - mejor).toFloat() / (peor - mejor),
                esRecord = p.esRecord,
            )
        }
    return EscalaEvolucion(puntos, mejor, peor, desde, hasta)
}

private fun escalaX(
    indice: Int,
    total: Int,
    fechaMs: Long,
    desdeMs: Long,
    hastaMs: Long,
): Float =
    when {
        total == 1 -> CENTRO
        hastaMs == desdeMs -> indice.toFloat() / (total - 1)
        else -> (fechaMs - desdeMs).toFloat() / (hastaMs - desdeMs)
    }
