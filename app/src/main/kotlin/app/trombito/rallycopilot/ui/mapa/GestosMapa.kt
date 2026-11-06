package app.trombito.rallycopilot.ui.mapa

import org.maplibre.android.gestures.AndroidGesturesManager

/** Desplazamiento vertical (dp) de los dos dedos para empezar a inclinar; el SDK trae 16. */
internal const val UMBRAL_INCLINAR_DP = 6f

/** Inclinación máxima (grados) de la línea entre los dos dedos para contarla como «inclinar»; el SDK trae 20. */
internal const val ANGULO_MAXIMO_INCLINAR = 45f

/** Giro (grados) de los dos dedos para empezar a girar el mapa; el mapa trae 3, que ganaba siempre al inclinar. */
internal const val ANGULO_MINIMO_GIRAR = 12f

/**
 * Prioriza inclinar sobre girar con dos dedos. En MapLibre girar e inclinar son excluyentes (gana el que empieza
 * primero) e inclinar exigía mover 16 dp con los dedos casi horizontales, mientras que girar arranca con 3 grados:
 * un gesto vertical con los dedos algo torcidos se convertía en giro y el mapa «no se dejaba inclinar».
 */
internal fun ajustarGestosMapa(
    gestos: AndroidGesturesManager,
    densidad: Float,
) {
    gestos.shoveGestureDetector.pixelDeltaThreshold = UMBRAL_INCLINAR_DP * densidad
    gestos.shoveGestureDetector.maxShoveAngle = ANGULO_MAXIMO_INCLINAR
    gestos.rotateGestureDetector.angleThreshold = ANGULO_MINIMO_GIRAR
}
