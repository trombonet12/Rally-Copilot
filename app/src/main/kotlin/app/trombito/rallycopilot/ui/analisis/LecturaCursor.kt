package app.trombito.rallycopilot.ui.analisis

import app.trombito.rallycopilot.motor.analisis.PuntoSerie

/**
 * Lo que dicen los gráficos en el punto del cursor: la [distanciaM] desde la salida, la velocidad de la pasada
 * analizada, la de la comparada y el delta acumulado (ms, negativo = más rápido que la comparada). Cada valor falta
 * si su serie está vacía (sin pasada comparada, por ejemplo).
 */
data class LecturaCursor(
    val distanciaM: Double,
    val velocidadKmh: Double?,
    val comparadaKmh: Double?,
    val deltaMs: Long?,
)

/** Lectura de las series en [cursorM]; null si el cursor no está puesto. */
fun lecturaCursor(
    cursorM: Double?,
    velocidad: List<PuntoSerie>,
    comparada: List<PuntoSerie>,
    delta: List<PuntoSerie>,
): LecturaCursor? =
    cursorM?.let {
        LecturaCursor(
            distanciaM = it,
            velocidadKmh = valorEn(velocidad, it),
            comparadaKmh = valorEn(comparada, it),
            deltaMs = valorEn(delta, it)?.let { v -> Math.round(v) },
        )
    }
