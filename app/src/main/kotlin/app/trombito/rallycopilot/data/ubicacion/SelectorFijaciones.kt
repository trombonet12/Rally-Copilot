package app.trombito.rallycopilot.data.ubicacion

import app.trombito.rallycopilot.motor.velocidad.Fijacion

/** De qué proveedor viene una fijación: el GNSS crudo o el rápido (fused/red). */
internal enum class OrigenFijacion { GPS, RAPIDO }

/**
 * Fusión de dos fuentes de posición, siempre con el reloj monótono `tiempoMs` de las fijaciones y sin retroceder nunca
 * en el tiempo (una fijación no posterior a la última emitida se descarta).
 *
 * - GPS crudo: pasa salvo que el rápido tenga una fijación «buena» fresca y claramente más precisa.
 * - Rápido «bueno» (precisión ≤ [precisionBuenaM] y con velocidad: es el fused de alta precisión de GMS, que arranca el
 *   GNSS con asistencia): pasa si el GPS lleva callado [ventanaMs] o si es claramente más preciso que el GPS fresco.
 * - Rápido «aproximado» (red, sin velocidad o impreciso): solo pasa si el GPS lleva callado [ventanaMs].
 *
 * Histéresis: quien mandó en la última fijación emitida conserva el mando salvo que el otro sea mejor por más de
 * [margenM] metros, para no alternar de fuente a cada fijación. Es seguro llamarla desde varios hilos.
 */
internal class SelectorFijaciones(
    private val ventanaMs: Long = VENTANA_GPS_MS,
    private val precisionBuenaM: Double = PRECISION_BUENA_M,
    private val margenM: Double = MARGEN_M,
) {
    private var ultimaEmitidaMs: Long? = null
    private var lider: OrigenFijacion? = null
    private var ultimaGps: Anotada? = null
    private var ultimoBueno: Anotada? = null

    private class Anotada(
        val tiempoMs: Long,
        val precisionM: Double,
    )

    @Synchronized
    fun aceptar(
        fijacion: Fijacion,
        origen: OrigenFijacion,
    ): Boolean {
        val bueno = origen == OrigenFijacion.RAPIDO && fijacion.esBuena()
        // El reloj de cada fuente se anota aunque la fijación no se emita: sirve para saber si sigue viva.
        val precision = fijacion.precisionM ?: Double.POSITIVE_INFINITY
        if (origen == OrigenFijacion.GPS) {
            ultimaGps = anotar(ultimaGps, fijacion.tiempoMs, precision)
        } else if (bueno) {
            ultimoBueno = anotar(ultimoBueno, fijacion.tiempoMs, precision)
        }
        val ultima = ultimaEmitidaMs
        val emitir =
            (ultima == null || fijacion.tiempoMs > ultima) && cede(origen, bueno, fijacion.tiempoMs, precision)
        if (emitir) {
            ultimaEmitidaMs = fijacion.tiempoMs
            lider = origen
        }
        return emitir
    }

    private fun anotar(
        previa: Anotada?,
        tiempoMs: Long,
        precisionM: Double,
    ): Anotada = if (previa == null || tiempoMs >= previa.tiempoMs) Anotada(tiempoMs, precisionM) else previa

    /** Si, dejando aparte el orden temporal, la fijación debe pasar según la fuente rival. */
    private fun cede(
        origen: OrigenFijacion,
        bueno: Boolean,
        tiempoMs: Long,
        precisionM: Double,
    ): Boolean {
        val gps = ultimaGps?.takeIf { tiempoMs - it.tiempoMs < ventanaMs }
        val rapido = ultimoBueno?.takeIf { tiempoMs - it.tiempoMs < ventanaMs }
        return when {
            origen == OrigenFijacion.GPS -> {
                rapido == null ||
                    mantiene(OrigenFijacion.GPS, precisionM, rapido.precisionM)
            }

            gps == null -> {
                true
            }

            !bueno -> {
                false
            }

            else -> {
                mantiene(OrigenFijacion.RAPIDO, precisionM, gps.precisionM)
            }
        }
    }

    /** Si [origen], con [propiaM] de error frente a [rivalM] de la otra fuente, se queda o toma el mando. */
    private fun mantiene(
        origen: OrigenFijacion,
        propiaM: Double,
        rivalM: Double,
    ): Boolean =
        if (lider == origen) {
            rivalM + margenM >= propiaM
        } else {
            propiaM + margenM <= rivalM
        }

    private fun Fijacion.esBuena(): Boolean {
        val error = precisionM
        return velocidadMs != null && error != null && error <= precisionBuenaM
    }

    companion object {
        const val VENTANA_GPS_MS = 3_000L

        /** Error máximo (m) para que una fijación del fused cuente como buena. */
        const val PRECISION_BUENA_M = 15.0

        /** Ventaja (m) que necesita la fuente que no manda para quitarle el mando a la que sí. */
        const val MARGEN_M = 5.0
    }
}
