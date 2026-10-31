package app.trombito.rallycopilot.domain.notas

import app.trombito.rallycopilot.motor.notas.HojaNotas
import kotlinx.coroutines.flow.StateFlow

/** De dónde sale la hoja de notas en curso. */
enum class OrigenNotas {
    /** Hoja de la ruta calculada hacia un destino. */
    GUIADO,

    /** Hoja de la carretera prevista por delante (sin destino). */
    LIBRE,
}

/**
 * Notas de la conducción en curso: la [hoja], la [posicionM] actual a lo largo de ella (metros desde el inicio de
 * la ruta o de la predicción, en el mismo sistema que las posiciones de sus elementos) y la velocidad [vMs] del GPS
 * si la hay.
 */
data class NotasEnCurso(
    val hoja: HojaNotas,
    val posicionM: Double,
    val vMs: Double?,
    val origen: OrigenNotas,
)

/** Notas en vivo de la sesión de navegación; null si no hay conducción o aún no hay hoja que seguir. */
interface ProveedorNotas {
    val notas: StateFlow<NotasEnCurso?>
}
