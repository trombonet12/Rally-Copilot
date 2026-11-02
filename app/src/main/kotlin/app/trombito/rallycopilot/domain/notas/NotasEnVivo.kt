package app.trombito.rallycopilot.domain.notas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
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
 * si la hay. [puntoEn] convierte una posición de la hoja en su coordenada (null si no se conoce la traza); sirve para
 * identificar la misma curva entre hojas regeneradas con otro origen. [tramoReconocimiento] es el tramo que se está
 * reconociendo (la hoja es la de su traza y sus posiciones son metros del tramo); null en el resto. [traza] es la
 * polilínea sobre la que se miden las
 * posiciones (la carretera predicha en libre; null si no se conoce); las alertas la usan para ver lo que viene por
 * delante en el mismo eje que las notas.
 */
data class NotasEnCurso(
    val hoja: HojaNotas,
    val posicionM: Double,
    val vMs: Double?,
    val origen: OrigenNotas,
    val traza: List<PuntoGeo>? = null,
    val puntoEn: ((Double) -> PuntoGeo)? = null,
    val tramoReconocimiento: String? = null,
)

/** Notas en vivo de la sesión de navegación; null si no hay conducción o aún no hay hoja que seguir. */
interface ProveedorNotas {
    val notas: StateFlow<NotasEnCurso?>
}
