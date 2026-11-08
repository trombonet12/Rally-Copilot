package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.escucha.ControlOrdenes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Palabra de activación «Copiloto» (activada por defecto). Mientras dura la conducción, el ajuste
 * `activarConCopiloto` esté activo y no haya casco con «Escucha con casco» en «solo al pulsar» ([cascoActivo]), escucha
 * el micrófono sin parar con una gramática mínima (`["copiloto", "[unk]"]`) y, al oír la palabra, suelta el micrófono
 * y deja que [control] escuche una orden (con foco exclusivo); después vuelve a esperar la palabra.
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
    /**
     * `true` mientras haya un intercom de casco utilizable. Con casco y `escuchaCascoSiempre` apagado no se escucha sin
     * parar (el micrófono del casco obliga a abrir el canal de llamada y la música pierde calidad): solo vale el botón
     * del micrófono. Sin casco, o con el audio por el casco apagado, la escucha sigue por el móvil.
     */
    private val cascoActivo: Flow<Boolean> = flowOf(false),
) {
    /**
     * Escucha hasta que se cancele; sigue los cambios del ajuste y del permiso. [hablando] dice si el copiloto está
     * sonando (la puerta se cierra para que no se oiga a sí mismo); [registro] (solo en depuración) recibe cada minuto
     * el porcentaje de bloques que llegaron al reconocedor.
     */
    suspend fun correr(
        hablando: () -> Boolean = { false },
        registro: ((String) -> Unit)? = null,
    ) {
        escuchaSinParar().collectLatest { activo ->
            while (activo && coroutineContext.isActive) {
                if (!permiso() || !esperarPalabra(hablando, registro)) {
                    delay(REINTENTO_MS)
                } else {
                    tic()
                    control.escucharOrden()
                    control.escuchando.first { !it }
                }
            }
        }
    }

    /** Si hay que escuchar la palabra sin parar: «Copiloto» activo y, con casco, solo si se pidió escuchar siempre. */
    private fun escuchaSinParar(): Flow<Boolean> =
        combine(ajustes, cascoActivo) { a, casco -> a.activarConCopiloto && (a.escuchaCascoSiempre || !casco) }
            .distinctUntilChanged()

    /** `true` si se oyó la palabra; `false` si el micrófono o el modelo no están disponibles. */
    private suspend fun esperarPalabra(
        hablando: () -> Boolean,
        registro: ((String) -> Unit)?,
    ): Boolean =
        withContext(dispatcher) {
            val reconocedor = reconocedores.crear(GramaticaOrdenes.jsonActivacion()) ?: return@withContext false
            reconocedor.use {
                val fuente = audio.abrir() ?: return@withContext false
                fuente.use { escucharPalabra(fuente, reconocedor, hablando, registro) }
            }
        }

    private suspend fun escucharPalabra(
        fuente: FuenteAudio,
        reconocedor: Reconocedor,
        hablando: () -> Boolean,
        registro: ((String) -> Unit)?,
    ): Boolean {
        val puerta = PuertaVoz()
        val buffer = ShortArray(MUESTRAS_POR_LECTURA)
        var previo: ShortArray? = null
        var abierta = false
        while (true) {
            coroutineContext.ensureActive()
            val n = fuente.leer(buffer)
            if (n < 0) return false
            val pasa = puerta.abierta(buffer, n, hablando())
            if (registro != null && puerta.bloques % BLOQUES_POR_REGISTRO == 0L) registro(resumen(puerta))
            // Al abrirse la puerta se le da primero el trozo anterior, por si la palabra empezó en él.
            if (pasa && oyeLaPalabra(reconocedor, buffer, n, previo.takeIf { !abierta })) return true
            abierta = pasa
            previo = buffer.copyOf(n)
        }
    }

    private fun resumen(puerta: PuertaVoz): String =
        "puerta de voz: ${puerta.decodificados} de ${puerta.bloques} bloques decodificados " +
            "(${"%.1f".format(puerta.porcentajeAbierta())} %)"

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

        /** Bloques de 100 ms entre dos líneas de registro (1 min). */
        const val BLOQUES_POR_REGISTRO = 600L

        /** Espera antes de reintentar si no hay permiso, micrófono o modelo. */
        const val REINTENTO_MS = 5_000L
    }
}
