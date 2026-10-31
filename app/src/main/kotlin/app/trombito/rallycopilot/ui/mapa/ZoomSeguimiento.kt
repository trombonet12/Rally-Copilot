package app.trombito.rallycopilot.ui.mapa

import android.os.SystemClock
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs

/** Duración del cambio de zoom automático: menor que el periodo de las fijaciones (1 Hz) para que termine antes. */
private const val DURACION_ZOOM_MS = 900L

/** Cambio de zoom (niveles) por debajo del cual no se reanima la cámara. */
private const val UMBRAL_ZOOM = 0.02

/**
 * Zoom de la cámara mientras sigue al coche: aplica el zoom automático con animaciones cortas y detecta los gestos del
 * usuario (pellizco o arrastre) para pausarlo con el zoom que dejó.
 */
internal class ZoomSeguimiento(
    /** Zoom conocido de la cámara (el restaurado al crear la vista, el último guardado o el último aplicado). */
    var vigente: Double?,
    private val alCambiarZoomManual: (zoom: Double, ahoraMs: Long) -> Unit,
) {
    /** Último zoom automático pedido al componente de ubicación; null si hay que volver a pedirlo. */
    private var aplicado: Double? = null
    private var gestoEnCurso = false

    /** Escucha los gestos del mapa; [activo] dice si la vista está entre onStart y onStop. */
    fun escuchar(
        mapa: MapLibreMap,
        activo: () -> Boolean,
    ) {
        mapa.addOnCameraMoveStartedListener { motivo ->
            if (motivo == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                gestoEnCurso = true
                avisar(mapa, activo())
            }
        }
        mapa.addOnCameraIdleListener {
            if (gestoEnCurso) {
                gestoEnCurso = false
                avisar(mapa, activo())
            }
        }
    }

    /** El zoom ya lo puso otra transición (p. ej. la del modo de seguimiento): no hay que animarlo. */
    fun yaAplicado(zoom: Double) {
        aplicado = zoom
        vigente = zoom
    }

    /** Anima el zoom hacia [objetivo] si cambia lo bastante (con la ubicación activa y la cámara siguiendo). */
    fun aplicar(
        componente: LocationComponent,
        objetivo: Double?,
    ) {
        val previo = aplicado
        if (objetivo == null || (previo != null && abs(objetivo - previo) < UMBRAL_ZOOM)) return
        componente.zoomWhileTracking(
            objetivo,
            DURACION_ZOOM_MS,
            object : MapLibreMap.CancelableCallback {
                override fun onCancel() {
                    aplicado = null
                }

                override fun onFinish() = Unit
            },
        )
        // Después de la llamada: cancelar la animación anterior dispara su onCancel, que borra el valor.
        yaAplicado(objetivo)
    }

    /** Pausa el zoom automático con el zoom que tiene la cámara (el del usuario) y evita reanimarlo hacia atrás. */
    private fun avisar(
        mapa: MapLibreMap,
        activo: Boolean,
    ) {
        if (!activo) return
        val zoom = mapa.cameraPosition.zoom
        yaAplicado(zoom)
        alCambiarZoomManual(zoom, SystemClock.elapsedRealtime())
    }
}
