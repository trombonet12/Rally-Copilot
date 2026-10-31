package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.motor.mapa.SuavizadorZoom

/** Pausa del zoom automático tras un pellizco del usuario. */
internal const val PAUSA_ZOOM_MS = 8_000L

/**
 * Zoom automático del mapa: la curva por velocidad del motor con el ajuste de Joan, suavizada. Sin velocidad conocida
 * mantiene el último zoom (null si aún no hubo ninguno). Un pellizco ([pausar]) lo congela [PAUSA_ZOOM_MS].
 */
internal class ZoomAutomatico(
    private val suavizador: SuavizadorZoom = SuavizadorZoom(),
) {
    /** [ahoraMs] es el reloj monótono de las fijaciones; [velocidadKmh] o [ahoraMs] null = no hay fijación. */
    fun actualizar(
        velocidadKmh: Int?,
        ahoraMs: Long?,
        nivel: Int,
    ): Double? =
        if (velocidadKmh == null || ahoraMs == null) {
            suavizador.zoomActual
        } else {
            suavizador.actualizar(velocidadKmh.toDouble(), nivel, ahoraMs)
        }

    fun pausar(
        zoomManual: Double,
        ahoraMs: Long,
    ) = suavizador.pausar(ahoraMs + PAUSA_ZOOM_MS, zoomManual)
}
