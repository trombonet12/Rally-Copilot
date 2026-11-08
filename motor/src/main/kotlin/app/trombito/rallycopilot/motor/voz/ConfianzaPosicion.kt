package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.notas.Anticipacion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.velocidad.PrecisionGps

/**
 * Decide cuándo el copiloto debe pausar las notas porque no se fía de dónde está el coche, y cuándo reanudarlas.
 * Sin reloj propio: el tiempo es el de las fijaciones (`tMs`), así que es determinista.
 *
 * Una evaluación es **mala** si no hay posición sobre la hoja, si la precisión supera [PrecisionGps.FIJACION_UTIL_M]
 * o si el lateral a la ruta lleva más de [LATERAL_MAX_M] durante más de [LATERAL_TIEMPO_MS] seguidos. Una mala pasa
 * a [Estado.PAUSA] al instante. Con lateral alto pero aún dentro del margen de tiempo la evaluación no es ni mala ni
 * buena: no cambia el estado ni cuenta para volver. De [Estado.PAUSA] a [Estado.FIABLE] hacen falta
 * [BUENAS_PARA_VOLVER] evaluaciones buenas seguidas (histéresis); al volver se pide [Evaluacion.resincronizar].
 * Empieza en [Estado.FIABLE]. No es seguro entre hilos.
 */
class ConfianzaPosicion {
    enum class Estado { FIABLE, PAUSA }

    /**
     * Resultado de una evaluación. [resincronizar] es la posición (m sobre la hoja) a la que re-sincronizar, solo en
     * la evaluación en que se pasa de pausa a fiable: las notas que quedaron atrás se dan por dichas y se relee la
     * siguiente si ya está dentro de la anticipación ([pendientesTrasResincronizar]).
     */
    data class Evaluacion(
        val estado: Estado,
        val resincronizar: Double? = null,
    )

    private var estado = Estado.FIABLE
    private var buenasSeguidas = 0
    private var lateralDesdeMs: Long? = null

    /**
     * Evalúa una fijación: [precisionM] y [lateralM] pueden ser `null` si se desconocen (se dan por buenos);
     * [posicionM] `null` es que no hay posición sobre la hoja.
     */
    fun evaluar(
        tMs: Long,
        precisionM: Double?,
        lateralM: Double?,
        posicionM: Double?,
    ): Evaluacion {
        val lateralAlto = lateralM != null && lateralM > LATERAL_MAX_M
        if (!lateralAlto) {
            lateralDesdeMs = null
        } else if (lateralDesdeMs == null) {
            lateralDesdeMs = tMs
        }
        val desde = lateralDesdeMs
        val lateralVencido = desde != null && tMs - desde > LATERAL_TIEMPO_MS
        val mala = posicionM == null || !PrecisionGps.esUtil(precisionM) || lateralVencido
        when {
            mala -> {
                estado = Estado.PAUSA
                buenasSeguidas = 0
            }

            lateralAlto -> {
                if (estado == Estado.PAUSA) buenasSeguidas = 0
            }

            estado == Estado.PAUSA -> {
                buenasSeguidas++
                if (buenasSeguidas >= BUENAS_PARA_VOLVER) {
                    estado = Estado.FIABLE
                    buenasSeguidas = 0
                    return Evaluacion(Estado.FIABLE, resincronizar = posicionM)
                }
            }
        }
        return Evaluacion(estado)
    }

    companion object {
        /** Lateral (m) a la ruta por encima del cual la posición deja de fiarse. */
        const val LATERAL_MAX_M = 40.0

        /** Tiempo (ms) seguido con el lateral por encima de [LATERAL_MAX_M] antes de pausar. */
        const val LATERAL_TIEMPO_MS = 3_000L

        /** Evaluaciones buenas seguidas para volver de [Estado.PAUSA] a [Estado.FIABLE]. */
        const val BUENAS_PARA_VOLVER = 3
    }
}

/**
 * Elemento de [hoja] que releer tras re-sincronizar en [posicionM]: el primero por delante (`posicionM` del elemento
 * ≥ la posición) si ya está dentro de la distancia de [anticipacion] a [vMs] con el [nivel] dado; `null` si no hay
 * ninguno por delante o aún queda lejos (entonces lo cantará el selector normal en su momento).
 */
fun pendientesTrasResincronizar(
    hoja: HojaNotas,
    posicionM: Double,
    vMs: Double,
    nivel: NivelAnticipacion,
    anticipacion: Anticipacion = Anticipacion(),
): ElementoHoja? {
    val siguiente = hoja.elementos.firstOrNull { it.posicionM >= posicionM } ?: return null
    return siguiente.takeIf { it.posicionM - posicionM <= anticipacion.distanciaM(vMs, nivel) }
}
