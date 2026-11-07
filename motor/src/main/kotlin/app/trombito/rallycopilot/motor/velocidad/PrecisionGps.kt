package app.trombito.rallycopilot.motor.velocidad

/**
 * Umbrales de precisión del GPS en un solo sitio (auditoría de noviembre de 2026, UX-5): antes la banda «Sin señal
 * GPS» daba «señal» con 50 m, la sesión descartaba con más de 30 m y la velocidad exigía 30 m, y la app podía quedarse
 * muda sin avisar.
 */
object PrecisionGps {
    /**
     * Peor error (m) con el que una fijación es útil para velocidad, ruta, notas y señal GPS (banda y voz). La usan
     * `DetectorSenalGps`, `CanalizacionGps` y `SesionNavegacion`.
     */
    const val FIJACION_UTIL_M = 30.0

    /**
     * Peor error (m) con el que una fijación sirve para detectar la salida de un tramo (`DetectorSalida`).
     */
    const val SALIDA_M = 20.0

    /** Error máximo (grados) del rumbo del receptor para fiarse de él (`FuenteUbicacionGps`). */
    const val RUMBO_MAX_GRADOS = 20.0

    /** `true` si una fijación con error [precisionM] es útil; sin precisión conocida (`null`) se da por útil. */
    fun esUtil(precisionM: Double?): Boolean = (precisionM ?: 0.0) <= FIJACION_UTIL_M
}
