package app.trombito.rallycopilot.motor.velocidad

/**
 * Decide si se ha perdido el GPS, con histéresis.
 *
 * - **Útil**: fijación con error de [PrecisionGps.FIJACION_UTIL_M] o menos (sin precisión conocida también), el mismo
 *   umbral que la sesión y la velocidad: si la sesión descarta, la banda lo ve (UX-5).
 * - **Pérdida**: pasan más de [umbralMs] sin fijación útil, tras haber tenido alguna (el primer arranque tarda en
 *   fijar). Una vez declarada, **recuperar** exige [RACHA_MINIMA] fijaciones útiles seguidas o [RACHA_MINIMA_MS] de
 *   útiles seguidas; una mala corta la racha.
 * - **Descartadas**: las fijaciones cuyo `tiempoMs` es más de [EDAD_MAX_MS] anterior al reloj (la «última conocida» que
 *   emite el GPS al registrarse, QA-2) y las que no avanzan en el tiempo (repetidas o hacia atrás).
 *
 * Causa raíz del aviso que oscilaba: una sola fijación buena (≤ 50 m) renovaba la señal y el umbral de 5 s no tenía
 * histéresis, así que con señal débil pasaba «perdida → recuperada» con cada fijación suelta y la voz cantaba cada
 * cambio; además la última conocida entraba con su marca antigua y disparaba un aviso espurio al arrancar.
 *
 * Usa el reloj monótono de las fijaciones (`Fijacion.tiempoMs`). Sin hilos: lo usa el ViewModel del mapa.
 */
class DetectorSenalGps(
    private val umbralMs: Long = UMBRAL_MS,
) {
    private var ultimoTiempoMs: Long? = null
    private var perdida = false
    private var rachaDesdeMs: Long? = null
    private var rachaCuenta = 0

    /** Tiempo (reloj monótono) de la última fijación útil; `null` si todavía no ha habido ninguna. */
    var ultimaUtilMs: Long? = null
        private set

    /** Anota [fijacion] recibida cuando el reloj marcaba [ahoraMs]. */
    fun fijacion(
        fijacion: Fijacion,
        ahoraMs: Long,
    ) {
        val t = fijacion.tiempoMs
        val antigua = t < ahoraMs - EDAD_MAX_MS
        val noAvanza = ultimoTiempoMs?.let { t <= it } == true
        if (antigua || noAvanza) return
        ultimoTiempoMs = t
        // Si nadie preguntó durante el hueco, la pérdida se declara antes de contar la fijación nueva.
        actualizarPerdida(t)
        if (PrecisionGps.esUtil(fijacion.precisionM)) anotarUtil(t) else cortarRacha()
    }

    private fun anotarUtil(t: Long) {
        ultimaUtilMs = t
        if (rachaCuenta == 0) rachaDesdeMs = t
        rachaCuenta++
        if (perdida && (rachaCuenta >= RACHA_MINIMA || t - (rachaDesdeMs ?: t) >= RACHA_MINIMA_MS)) perdida = false
    }

    private fun cortarRacha() {
        rachaCuenta = 0
        rachaDesdeMs = null
    }

    /** Si, a las [ahoraMs], se ha perdido la señal. */
    fun sinSenal(ahoraMs: Long): Boolean {
        actualizarPerdida(ahoraMs)
        return perdida
    }

    private fun actualizarPerdida(ahoraMs: Long) {
        val ultima = ultimaUtilMs ?: return
        if (!perdida && ahoraMs - ultima > umbralMs) {
            perdida = true
            cortarRacha()
        }
    }

    companion object {
        /** Sin fijación útil durante este tiempo sale la banda «Sin señal GPS» (decisión de Joan: ~10 s). */
        const val UMBRAL_MS = 10_000L

        /** Fijaciones útiles seguidas para dar la señal por recuperada. */
        const val RACHA_MINIMA = 3

        /** …o este tiempo de útiles seguidas. */
        const val RACHA_MINIMA_MS = 3_000L

        /** Una fijación con más de esta edad respecto al reloj no cuenta (es una «última conocida» antigua). */
        const val EDAD_MAX_MS = 5_000L
    }
}
