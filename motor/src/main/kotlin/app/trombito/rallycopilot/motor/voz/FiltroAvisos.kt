package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.alertas.AlertasRadar

/**
 * Qué familias de avisos puede decir el copiloto (todo activo es el comportamiento de siempre). Apagar una familia
 * solo calla su voz: el banner y la píldora se siguen pintando igual, y las notas y maniobras no se tocan.
 *
 * - [avisarRadares]: los avisos de radares fijos, de tramo y móviles (`radar-<id>…`).
 * - [avisarIncidencias]: los avisos de obras, cortes, accidentes y retenciones (`incidencia-<id>`).
 * - [avisarVelocidad]: el exceso de velocidad y la media del tramo ([TipoFrase.AVISO_VELOCIDAD]).
 */
data class FiltroAvisos(
    val avisarRadares: Boolean = true,
    val avisarIncidencias: Boolean = true,
    val avisarVelocidad: Boolean = true,
) {
    /** `true` si no se calla nada. */
    val todoActivo: Boolean get() = avisarRadares && avisarIncidencias && avisarVelocidad

    /** `false` si [frase] pertenece a una familia apagada; las frases de otras familias siempre se permiten. */
    fun permite(frase: Frase): Boolean =
        when {
            frase.tipo == TipoFrase.AVISO_VELOCIDAD -> avisarVelocidad
            frase.tipo != TipoFrase.ALERTA -> true
            AlertasRadar.idDeRadar(frase.id) != null -> avisarRadares
            frase.id.startsWith(PREFIJO_INCIDENCIA) -> avisarIncidencias
            else -> true
        }

    private companion object {
        /** Prefijo de las alertas de `FiltroIncidencias` (`incidencia-<id>`). */
        const val PREFIJO_INCIDENCIA = "incidencia-"
    }
}
