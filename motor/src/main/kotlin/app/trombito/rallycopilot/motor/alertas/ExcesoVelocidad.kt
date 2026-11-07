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
 * - Un aviso de voz corto («límite ochenta», id `exceso-<n>`, [TipoFrase.AVISO_VELOCIDAD], [Frase.puntoM] `null`: va
 *   por detrás de notas y maniobras) cuando se cumple todo esto:
 *   - **exceso sostenido**: [SOSTENIDO_MS] (4 s) seguidos con [EstadoExceso.exceso]; un pico de GPS no avisa;
 *   - **gracia** de [GRACIA_MS] (10 s) tras pasar a un límite menor: mientras frenas no se regaña;
 *   - **separación** de al menos [SEPARACION_MS] (90 s) desde el aviso anterior;
 *   - **máximo [MAX_POR_VIA] (2) por vía seguida**: sin identificador de vía, «vía seguida» es el mismo límite (el
 *     contador vuelve a cero al cambiar de límite; la separación de 90 s acota el resto).
 *   Un aviso por episodio de exceso: no se repite mientras el exceso no se corte.
 * - Sin límite (`null`) no hay estado: el episodio de exceso se corta, pero no cambia el límite ni el contador.
 *   Velocidades no finitas se ignoran.
 *
 * [velocidadKmh] debe ser la velocidad tal como se muestra al usuario (km/h).
 */
class ExcesoVelocidad {
    private var limiteActual: Int? = null
    private var excesoDesdeMs: Long? = null
    private var graciaHastaMs = Long.MIN_VALUE
    private var ultimoAvisoMs: Long? = null
    private var avisosEnVia = 0
    private var avisosDados = 0
    private var avisadoEnEpisodio = false
    private var pendiente: Frase? = null

    fun actualizar(
        velocidadKmh: Double,
        limiteKmh: Int?,
        ahoraMs: Long,
    ): EstadoExceso {
        if (limiteKmh == null || !velocidadKmh.isFinite()) {
            if (limiteKmh == null) cortarEpisodio()
            return EstadoExceso(rojo = false, exceso = false, frase = null)
        }
        if (limiteKmh != limiteActual) cambiarLimite(limiteKmh, ahoraMs)
        val v = velocidadKmh.coerceAtLeast(0.0)
        val rojo = v > limiteKmh
        // ×10 en vez de ×1,1: 80·1,1 no es 88 exacto en coma flotante y el 10 % exacto no debe contar.
        val exceso = v * DECIMAS_BASE > limiteKmh * DECIMAS_EXCESO
        when {
            !exceso -> cortarEpisodio()
            ahoraMs < graciaHastaMs -> cortarEpisodio()
            else -> seguirExceso(limiteKmh, ahoraMs)
        }
        return EstadoExceso(rojo, exceso, pendiente)
    }

    private fun cambiarLimite(
        nuevo: Int,
        ahoraMs: Long,
    ) {
        val anterior = limiteActual
        limiteActual = nuevo
        avisosEnVia = 0
        if (anterior != null && nuevo < anterior) graciaHastaMs = ahoraMs + GRACIA_MS
        cortarEpisodio()
    }

    private fun cortarEpisodio() {
        excesoDesdeMs = null
        avisadoEnEpisodio = false
        pendiente = null
    }

    private fun seguirExceso(
        limiteKmh: Int,
        ahoraMs: Long,
    ) {
        val desde = excesoDesdeMs ?: ahoraMs.also { excesoDesdeMs = it }
        val sostenido = ahoraMs - desde >= SOSTENIDO_MS
        if (!avisadoEnEpisodio && sostenido && hayCupo(ahoraMs)) {
            avisadoEnEpisodio = true
            avisosEnVia++
            avisosDados++
            ultimoAvisoMs = ahoraMs
            val texto = "límite ${FrasesNota.enPalabras(limiteKmh)}"
            pendiente = Frase("exceso-$avisosDados", texto, TipoFrase.AVISO_VELOCIDAD, null)
        }
    }

    /** Separación de [SEPARACION_MS] desde el aviso anterior y menos de [MAX_POR_VIA] avisos en esta vía. */
    private fun hayCupo(ahoraMs: Long): Boolean {
        val separado = ultimoAvisoMs?.let { ahoraMs - it >= SEPARACION_MS } ?: true
        return separado && avisosEnVia < MAX_POR_VIA
    }

    companion object {
        /** Tiempo seguido con exceso antes de avisar. */
        const val SOSTENIDO_MS = 4_000L

        /** Tras pasar a un límite menor no se avisa durante este tiempo. */
        const val GRACIA_MS = 10_000L

        /** Separación mínima entre dos avisos. */
        const val SEPARACION_MS = 90_000L

        /** Avisos máximos por vía seguida (mismo límite). */
        const val MAX_POR_VIA = 2
        private const val DECIMAS_BASE = 10.0
        private const val DECIMAS_EXCESO = 11.0
    }
}
