package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.escucha.ControlOrdenes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Palabra de activación «Copiloto» (activada por defecto). Mientras dura la conducción y el ajuste
 * `activarConCopiloto` esté activo, escucha el micrófono sin parar con una gramática mínima (`["copiloto", "[unk]"]`)
 * y, al oír la palabra, suelta el micrófono y deja que [control] escuche una orden (con foco exclusivo); después vuelve
 * a esperar la palabra.
 *
 * Coste (sin medir en móvil; ver `docs/investigacion/asr-offline.md`): el micrófono abierto todo el rato (con el
 * indicador de privacidad del sistema encendido), un servicio en primer plano de tipo micrófono y el reconocedor
 * decodificando solo cuando [PuertaVoz] detecta voz (el silencio y el ruido constante del coche no se decodifican). No
 * toma el foco de audio: la música sigue sonando hasta que se dice la palabra. Sin permiso de micrófono no hace nada.
 */
@Suppress("LongParameterList") // cada pieza de la escucha llega inyectada
class EscuchaContinua(
    private val audio: FabricaAudio,
    private val reconocedores: FabricaReconocedor,
    private val permiso: () -> Boolean,
    private val ajustes: Flow<AjustesConduccion>,
    private val control: ControlOrdenes,
    private val dispatcher: CoroutineDispatcher,
    /** Señal sonora tras oír la palabra, antes de escuchar la orden; debe volver cuando haya acabado de sonar. */
    private val tic: suspend () -> Unit = {},
) {
    /** Escucha hasta que se cancele; sigue los cambios del ajuste y del permiso. */
    suspend fun correr() {
        ajustes.map { it.activarConCopiloto }.distinctUntilChanged().collectLatest { activo ->
            while (activo && coroutineContext.isActive) {
                if (!permiso() || !esperarPalabra()) {
                    delay(REINTENTO_MS)
                } else {
                    tic()
                    control.escucharOrden()
                    control.escuchando.first { !it }
                }
            }
        }
    }

    /** `true` si se oyó la palabra; `false` si el micrófono o el modelo no están disponibles. */
    private suspend fun esperarPalabra(): Boolean =
        withContext(dispatcher) {
            val reconocedor = reconocedores.crear(GramaticaOrdenes.jsonActivacion()) ?: return@withContext false
            reconocedor.use {
                val fuente = audio.abrir() ?: return@withContext false
                fuente.use { escucharPalabra(fuente, reconocedor) }
            }
        }

    private suspend fun escucharPalabra(
        fuente: FuenteAudio,
        reconocedor: Reconocedor,
    ): Boolean {
        val puerta = PuertaVoz()
        val buffer = ShortArray(MUESTRAS_POR_LECTURA)
        var previo: ShortArray? = null
        var abierta = false
        while (true) {
            coroutineContext.ensureActive()
            val n = fuente.leer(buffer)
            if (n < 0) return false
            val pasa = puerta.abierta(buffer, n)
            // Al abrirse la puerta se le da primero el trozo anterior, por si la palabra empezó en él.
            if (pasa && oyeLaPalabra(reconocedor, buffer, n, previo.takeIf { !abierta })) return true
            abierta = pasa
            previo = buffer.copyOf(n)
        }
    }

    private fun oyeLaPalabra(
        reconocedor: Reconocedor,
        buffer: ShortArray,
        n: Int,
        anterior: ShortArray?,
    ): Boolean {
        anterior?.let { reconocedor.aceptar(it, it.size) }
        val texto = if (reconocedor.aceptar(buffer, n)) reconocedor.resultado() else reconocedor.parcial()
        return GramaticaOrdenes.PALABRA_ACTIVACION in texto
    }

    private companion object {
        const val MUESTRAS_POR_LECTURA = FRECUENCIA_HZ / 10

        /** Espera antes de reintentar si no hay permiso, micrófono o modelo. */
        const val REINTENTO_MS = 5_000L
    }
}
