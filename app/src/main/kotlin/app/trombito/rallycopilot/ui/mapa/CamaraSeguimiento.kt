package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.ajustes.Orientacion

/** Inclinación del mapa con el rumbo arriba (vista 3D de videojuego): se ve más carretera por delante. */
internal const val INCLINACION_RUMBO = 50.0

/** Con el norte arriba el mapa va plano. */
internal const val INCLINACION_NORTE = 0.0

/** Altura (fracción del alto del mapa, desde arriba) a la que queda el marcador en vertical. */
internal const val MARCADOR_VERTICAL = 0.80

/**
 * En horizontal hay menos alto: el marcador no baja tanto. El mapa va a pantalla completa y la hoja de conducción
 * (96-112 dp) lo tapa por abajo: con estos valores el coche queda justo por encima de ella.
 */
internal const val MARCADOR_HORIZONTAL = 0.70

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

/**
 * Distingue quién descarta el seguimiento. Al cambiar de modo con el componente de ubicación ya siguiendo (norte arriba
 * a rumbo arriba, o al reponer el relleno) MapLibre avisa de que el seguimiento se descartó, igual que cuando el
 * usuario mueve el mapa: sin distinguirlos, el propio cambio de orientación dejaba la cámara sin seguir, sin inclinar
 * y con el botón de recentrar. El aviso llega durante la llamada, de ahí que baste una marca síncrona.
 */
internal class DescarteSeguimiento {
    private var aplicandoApp = false

    /** El descarte que se acaba de avisar lo hizo el usuario (y no la app al aplicar el modo de cámara). */
    fun esDelUsuario(): Boolean = !aplicandoApp

    /** Ejecuta [accion] (modo, relleno e inclinación de la cámara) marcando que los descartes son de la app. */
    fun <T> aplicando(accion: () -> T): T {
        val previo = aplicandoApp
        aplicandoApp = true
        try {
            return accion()
        } finally {
            aplicandoApp = previo
        }
    }
}
