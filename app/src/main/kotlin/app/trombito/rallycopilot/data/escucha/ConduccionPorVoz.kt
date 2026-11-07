package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.tramos.ControlSalidaCrono
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.PrioridadVoz
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * «Salida» y «para el crono» por voz.
 *
 * - [darSalida]: con la salida manual y la conducción lista, el copiloto cuenta atrás («cinco… uno… ya») y al final da
 *   la salida al cronómetro ([ControlSalidaCrono]); con la automática dice que lo es. Los números y el «ya» van
 *   urgentes y solo suenan si [puedeHablar].
 * - [pararCrono]: en modo libre termina la conducción; guiando pide confirmación («di sí para terminar») y queda
 *   [esperaConfirmacion] hasta la siguiente respuesta ([confirmar]) o hasta [descartarConfirmacion].
 */
@Suppress("LongParameterList") // colaboradores de la conducción: estado, fin, cronómetro, salida, voz y ámbito
class ConduccionPorVoz(
    private val estado: () -> EstadoNavegacion,
    private val terminar: () -> Unit,
    private val crono: ProveedorCrono,
    private val salida: ControlSalidaCrono,
    private val locutor: Locutor,
    private val ambito: CoroutineScope,
    private val pasoCuentaAtrasMs: Long = PASO_CUENTA_ATRAS_MS,
    /** Si el copiloto puede hablar (voz elegida y fuera de silencio); si no, la cuenta atrás no suena. */
    private val puedeHablar: suspend () -> Boolean = { true },
) {
    /** `true` tras preguntar «di sí para terminar», hasta que llega la respuesta. */
    @Volatile
    var esperaConfirmacion: Boolean = false
        private set

    private var cuentaAtras: Job? = null

    /** Da la salida; devuelve lo que hay que decir (`null` si habla la cuenta atrás). */
    fun darSalida(salidaManual: Boolean): String? =
        when {
            !salidaManual -> {
                SALIDA_AUTOMATICA
            }

            crono.crono.value?.esperaSalidaManual != true -> {
                SIN_SALIDA_PENDIENTE
            }

            cuentaAtras?.isActive == true -> {
                null
            }

            else -> {
                cuentaAtras = ambito.launch { contar() }
                null
            }
        }

    private suspend fun contar() {
        NUMEROS.forEach {
            if (puedeHablar()) locutor.decir(it, PrioridadVoz.URGENTE)
            delay(pasoCuentaAtrasMs)
        }
        // Si mientras tanto salió el coche o se acabó la conducción, ya no hay salida que dar.
        if (crono.crono.value?.esperaSalidaManual == true) {
            if (puedeHablar()) locutor.decir(YA, PrioridadVoz.URGENTE)
            salida.darSalida()
        }
    }

    /** Termina la conducción libre; con destino pide confirmación. */
    fun pararCrono(): String {
        esperaConfirmacion = false
        return when (estado()) {
            is EstadoNavegacion.Libre -> {
                terminarYDecir()
            }

            is EstadoNavegacion.Guiando -> {
                esperaConfirmacion = true
                PEDIR_CONFIRMACION
            }

            else -> {
                SIN_CONDUCCION
            }
        }
    }

    /** La respuesta a «di sí para terminar»; ignorada (`null`) si no se había preguntado. */
    fun confirmar(si: Boolean): String? {
        if (!esperaConfirmacion) return null
        esperaConfirmacion = false
        return if (si) terminarYDecir() else CANCELADO
    }

    /** Lo que se dijo en vez de una respuesta anula la pregunta pendiente. */
    fun descartarConfirmacion() {
        esperaConfirmacion = false
    }

    private fun terminarYDecir(): String {
        terminar()
        return CONDUCCION_TERMINADA
    }

    companion object {
        const val SALIDA_AUTOMATICA = "la salida es automática"
        const val SIN_SALIDA_PENDIENTE = "no hay salida pendiente"
        const val PEDIR_CONFIRMACION = "di sí para terminar"
        const val CONDUCCION_TERMINADA = "conducción terminada"
        const val CANCELADO = "cancelado"
        const val SIN_CONDUCCION = "no hay conducción"
        const val PASO_CUENTA_ATRAS_MS = 1_000L
        private const val YA = "ya"
        private val NUMEROS = listOf("cinco", "cuatro", "tres", "dos", "uno")
    }
}
