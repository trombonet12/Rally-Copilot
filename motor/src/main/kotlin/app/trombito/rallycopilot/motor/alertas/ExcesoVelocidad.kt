package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.notas.FrasesNota
import app.trombito.rallycopilot.motor.voz.Frase
import app.trombito.rallycopilot.motor.voz.TipoFrase

/**
 * Estado de velocidad frente al límite. [rojo]: la velocidad supera el límite (la UI la pone en rojo). [exceso]: la
 * supera en más de un 10 %. [frase]: aviso de voz pendiente mientras dure el exceso (mismo id; el planificador ya
 * recuerda lo dicho), o `null`.
 */
data class EstadoExceso(
    val rojo: Boolean,
    val exceso: Boolean,
    val frase: Frase?,
)

/**
 * Exceso de velocidad con el reloj inyectado. Se llama con cada fijación (≈ 1 Hz).
 *
 * - [EstadoExceso.rojo] cuando `v > límite`; [EstadoExceso.exceso] cuando `v > límite · 1,10` (el 10 % exacto no
 *   cuenta).
 * - Un aviso de voz («exceso de velocidad, límite ochenta», id `exceso-<n>`, [Frase.puntoM] `null`) **una vez por tramo
 *   de mismo límite**: se rearma al cambiar de límite o tras estar [REARME_MS] (10 s) o más sin pasar del límite;
 *   bajar solo al rojo o recuperar el exceso enseguida no vuelve a avisar.
 * - Sin límite (`null`) no hay estado y el aviso queda en pausa (no rearma). Velocidades no finitas se ignoran.
 *
 * [velocidadKmh] debe ser la velocidad tal como se muestra al usuario (km/h).
 */
class ExcesoVelocidad {
    private var limiteActual: Int? = null
    private var avisosDados = 0
    private var armado = true
    private var pendiente: Frase? = null
    private var bajoDesdeMs: Long? = null

    fun actualizar(
        velocidadKmh: Double,
        limiteKmh: Int?,
        ahoraMs: Long,
    ): EstadoExceso {
        if (limiteKmh == null || !velocidadKmh.isFinite()) {
            if (limiteKmh == null) bajoDesdeMs = null
            return EstadoExceso(rojo = false, exceso = false, frase = null)
        }
        if (limiteKmh != limiteActual) {
            limiteActual = limiteKmh
            armado = true
            pendiente = null
            bajoDesdeMs = null
        }
        val v = velocidadKmh.coerceAtLeast(0.0)
        val rojo = v > limiteKmh
        // ×10 en vez de ×1,1: 80·1,1 no es 88 exacto en coma flotante y el 10 % exacto no debe contar.
        val exceso = v * DECIMAS_BASE > limiteKmh * DECIMAS_EXCESO
        seguirRearme(rojo, ahoraMs)
        if (!exceso) {
            pendiente = null
        } else if (armado) {
            armado = false
            avisosDados++
            val texto = "exceso de velocidad, límite ${FrasesNota.enPalabras(limiteKmh)}"
            pendiente = Frase("exceso-$avisosDados", texto, TipoFrase.ALERTA, null)
        }
        return EstadoExceso(rojo, exceso, pendiente)
    }

    /** Cuenta el tiempo continuo sin pasar del límite y rearma al llegar a [REARME_MS]. */
    private fun seguirRearme(
        rojo: Boolean,
        ahoraMs: Long,
    ) {
        if (rojo) {
            bajoDesdeMs = null
            return
        }
        val desde = bajoDesdeMs ?: ahoraMs.also { bajoDesdeMs = it }
        if (ahoraMs - desde >= REARME_MS) armado = true
    }

    companion object {
        /** Tiempo continuo sin pasar del límite que rearma el aviso. */
        const val REARME_MS = 10_000L
        private const val DECIMAS_BASE = 10.0
        private const val DECIMAS_EXCESO = 11.0
    }
}
