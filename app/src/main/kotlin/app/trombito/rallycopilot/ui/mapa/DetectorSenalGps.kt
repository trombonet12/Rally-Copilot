package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.PrioridadVoz
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.velocidad.PrecisionGps
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import kotlinx.coroutines.flow.first
import kotlin.math.cos
import kotlin.math.hypot

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
internal class DetectorSenalGps(
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

/**
 * El copiloto avisa de que se perdió el GPS (o volvió), solo conduciendo (libre o guiando), con la voz elegida y si no
 * está en silencio. Reglas (decisión de Joan en la auditoría de noviembre de 2026):
 * - «Sin señal GPS» solo si la pérdida lleva más de [VOZ_TRAS_MS] sin fijación útil (la banda sale antes, a los 10 s).
 * - «GPS recuperado» solo si antes se dijo «Sin señal GPS».
 * - Enfriamiento: no repite «Sin señal GPS» antes de [ENFRIAMIENTO_MS]; el «recuperado» de una pérdida dicha
 *   siempre sale.
 * - Las frases son urgentes y caducan a los [CADUCIDAD_MS]: si el altavoz está ocupado, no salen tarde.
 *
 * Se llama en cada tick y en cada fijación con el estado actual de la señal; decide al hablar, con lo que haya entonces
 * (modo, voz y estado de navegación), así que no queda nada atrasado. [reiniciar] borra lo dicho (permiso nuevo).
 */
internal class AvisadorSenalGps(
    private val navegacion: ControlNavegacion,
    private val ajustes: RepositorioAjustesConduccion,
    private val locutor: Locutor,
) {
    private var dichoSinSenal = false
    private var ultimoSinSenalMs: Long? = null

    /** Olvida lo dicho: el detector que lo alimenta empieza de cero. */
    fun reiniciar() {
        dichoSinSenal = false
        ultimoSinSenalMs = null
    }

    /**
     * [sinSenal] es el estado de la banda a las [ahoraMs]; [sinFijacionDesdeMs] es el tiempo de la última fijación útil
     * (desde cuándo dura la pérdida).
     */
    suspend fun actualizar(
        sinSenal: Boolean,
        ahoraMs: Long,
        sinFijacionDesdeMs: Long?,
    ) {
        if (sinSenal) {
            val desde = sinFijacionDesdeMs ?: return
            val enfriado = ultimoSinSenalMs?.let { ahoraMs - it >= ENFRIAMIENTO_MS } ?: true
            if (dichoSinSenal || !enfriado || ahoraMs - desde <= VOZ_TRAS_MS) return
            if (hablar(FRASE_SIN_SENAL, ahoraMs)) {
                dichoSinSenal = true
                ultimoSinSenalMs = ahoraMs
            }
        } else if (dichoSinSenal) {
            dichoSinSenal = false
            hablar(FRASE_SENAL_RECUPERADA, ahoraMs)
        }
    }

    /** Dice [frase] si se puede hablar ahora; devuelve si se pidió al locutor. */
    private suspend fun hablar(
        frase: String,
        ahoraMs: Long,
    ): Boolean {
        val control = navegacion.estado.value
        val conduciendo = control is EstadoNavegacion.Libre || control is EstadoNavegacion.Guiando
        val preferencias = if (conduciendo) ajustes.ajustes.first() else null
        val puede =
            preferencias != null && preferencias.voz != null && preferencias.modoCopiloto != ModoCopiloto.SILENCIO
        if (puede) locutor.decir(frase, PrioridadVoz.URGENTE, ahoraMs + CADUCIDAD_MS)
        return puede
    }

    companion object {
        const val FRASE_SIN_SENAL = "Sin señal GPS"
        const val FRASE_SENAL_RECUPERADA = "GPS recuperado"

        /** Duración de la pérdida (sin fijación útil) a partir de la cual se dice «Sin señal GPS». */
        const val VOZ_TRAS_MS = 30_000L

        /** Tiempo mínimo entre dos «Sin señal GPS» dichos (3 min). */
        const val ENFRIAMIENTO_MS = 180_000L

        /** Pasado este tiempo en la cola, la frase ya no se dice. */
        const val CADUCIDAD_MS = 5_000L
    }
}

/** Margen (m) entre el punto mostrado y la geometría de la vía para decir que van por la misma carretera. */
private const val MARGEN_VIA_M = 12.0

/**
 * Si el punto [punto] (la posición ya ajustada al camino) está sobre la vía [via] (a [margenM] o menos de su
 * trazado). Sin vía o sin geometría no hay nada que contradiga: devuelve `true`.
 */
internal fun viaCoincide(
    punto: PuntoGeo,
    via: ViaActual?,
    margenM: Double = MARGEN_VIA_M,
): Boolean {
    val trazado = via?.trazado
    if (trazado == null || trazado.size < 2) return true
    return distanciaAPolilineaM(punto, trazado) <= margenM
}

/** Distancia mínima (m) de [punto] a la polilínea [linea], en proyección plana local. */
internal fun distanciaAPolilineaM(
    punto: PuntoGeo,
    linea: List<PuntoGeo>,
): Double {
    val escalaX = PuntoGeo.RADIO_TIERRA_M * cos(Math.toRadians(punto.lat))
    val escalaY = PuntoGeo.RADIO_TIERRA_M

    fun x(p: PuntoGeo) = Math.toRadians(p.lon - punto.lon) * escalaX

    fun y(p: PuntoGeo) = Math.toRadians(p.lat - punto.lat) * escalaY
    var mejor = Double.MAX_VALUE
    for (i in 0 until linea.lastIndex) {
        val ax = x(linea[i])
        val ay = y(linea[i])
        val dx = x(linea[i + 1]) - ax
        val dy = y(linea[i + 1]) - ay
        val largo2 = dx * dx + dy * dy
        // El punto está en el origen (0, 0): la proyección sobre el segmento es -(a·d) / |d|².
        val t = if (largo2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / largo2).coerceIn(0.0, 1.0)
        mejor = minOf(mejor, hypot(ax + t * dx, ay + t * dy))
    }
    return mejor
}
