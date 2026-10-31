package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.ajustes.Orientacion

/** Inclinación del mapa con el rumbo arriba (vista 3D de videojuego): se ve más carretera por delante. */
internal const val INCLINACION_RUMBO = 50.0

/** Con el norte arriba el mapa va plano. */
internal const val INCLINACION_NORTE = 0.0

/** Altura (fracción del alto del mapa, desde arriba) a la que queda el marcador en vertical. */
internal const val MARCADOR_VERTICAL = 0.72

/** En horizontal hay menos alto: el marcador no baja tanto. */
internal const val MARCADOR_HORIZONTAL = 0.62

/** Inclinación del mapa mientras sigue al coche según la orientación elegida. */
internal fun inclinacionSeguimiento(orientacion: Orientacion): Double =
    if (orientacion == Orientacion.NORTE) INCLINACION_NORTE else INCLINACION_RUMBO

/**
 * Relleno superior (px) del mapa para que el centro de la cámara, donde queda el marcador, esté a [MARCADOR_VERTICAL]
 * (o [MARCADOR_HORIZONTAL] si el mapa es apaisado) del alto; el centro de la zona visible es
 * `relleno + (alto - relleno) / 2`. Es un relleno fijo del mapa, no una animación: las fijaciones de 1 Hz no lo
 * cancelan ni lo mueven.
 */
internal fun rellenoSuperiorSeguimiento(
    anchoPx: Int,
    altoPx: Int,
): Int {
    if (altoPx <= 0) return 0
    val marcador = if (anchoPx > altoPx) MARCADOR_HORIZONTAL else MARCADOR_VERTICAL
    return ((2 * marcador - 1) * altoPx).toInt()
}
