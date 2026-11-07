package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.PrioridadVoz
import app.trombito.rallycopilot.motor.geo.ProyeccionPlana
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import kotlinx.coroutines.flow.first

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
    return ProyeccionPlana(punto).distanciaAPolilinea(punto, trazado) <= margenM
}
