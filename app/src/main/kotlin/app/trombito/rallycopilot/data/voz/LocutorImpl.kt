package app.trombito.rallycopilot.data.voz

import android.util.Log
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [Locutor] sobre un [SintetizadorTts] y una [CacheFrases].
 *
 * - **Síntesis**: siempre en [dispatcherSintesis] (un solo hilo) y de una en una (un [Mutex] justo): la cola de
 *   [preparar] y las frases urgentes de [decir] se turnan, así que [decir] espera como mucho a la frase en curso.
 * - **Cola de [preparar]**: sin duplicados, sin lo ya cacheado ni lo que se está sintetizando, y cada llamada sustituye
 *   a la lista anterior (lo que ya no se pide se descarta antes de sintetizarlo).
 * - **[decir]**: una frase a la vez, por orden de llegada. Sintetiza si no está en caché, pide el foco, suena y lo
 *   suelta. [hablando] es `true` desde que se pide el foco hasta que acaba; si se deniega, no suena y vuelve a `false`.
 */
class LocutorImpl(
    private val cache: CacheFrases,
    private val sintetizador: SintetizadorTts,
    private val reproductor: ReproductorPcm,
    private val foco: FocoAudio,
    private val ambito: CoroutineScope,
    private val dispatcherSintesis: CoroutineDispatcher,
) : Locutor {
    private class Peticion(
        val voz: VozCopiloto,
        val texto: String,
    )

    private val estaHablando = MutableStateFlow(false)
    override val hablando: StateFlow<Boolean> = estaHablando.asStateFlow()

    @Volatile private var voz = VozCopiloto.DAVEFX

    private val turnoSintesis = Mutex()
    private val turnosDeVoz = Channel<Peticion>(Channel.UNLIMITED)

    private val lockCola = Any()
    private val pendientes = LinkedHashMap<String, Peticion>()
    private var enCurso: String? = null
    private var trabajando = false

    init {
        ambito.launch {
            for (peticion in turnosDeVoz) hablar(peticion)
        }
    }

    override fun usarVoz(v: VozCopiloto) {
        voz = v
        synchronized(lockCola) { pendientes.clear() }
    }

    override fun preparar(textos: List<String>) {
        val vozActual = voz
        val nuevas =
            textos
                .filter { it.isNotBlank() }
                .distinct()
                .filterNot { cache.contiene(vozActual, it) }
        val lanzar =
            synchronized(lockCola) {
                pendientes.clear()
                for (texto in nuevas) {
                    val clave = CacheFrases.clave(vozActual, texto)
                    if (clave != enCurso) pendientes[clave] = Peticion(vozActual, texto)
                }
                (pendientes.isNotEmpty() && !trabajando).also { if (it) trabajando = true }
            }
        if (lanzar) ambito.launch { trabajarCola() }
    }

    override fun decir(texto: String) {
        if (texto.isNotBlank()) turnosDeVoz.trySend(Peticion(voz, texto))
    }

    override fun duracionEstimadaS(texto: String): Double {
        val vozActual = voz
        val bytes = cache.bytes(vozActual, texto)
        return if (bytes != null) {
            bytes / BYTES_POR_MUESTRA / sintetizador.frecuenciaHz(vozActual).toDouble()
        } else {
            texto.length * SEGUNDOS_POR_CARACTER
        }
    }

    override fun probar(v: VozCopiloto) {
        turnosDeVoz.trySend(Peticion(v, FRASE_PRUEBA))
    }

    override fun cerrar() {
        ambito.cancel()
        turnosDeVoz.close()
        synchronized(lockCola) { pendientes.clear() }
        foco.soltar()
        // liberar() espera a que acabe la frase en curso: fuera del hilo que llama.
        Thread({ sintetizador.liberar() }, "voz-liberar").start()
    }

    private suspend fun trabajarCola() {
        while (true) {
            val peticion = siguiente() ?: return
            try {
                if (!cache.contiene(peticion.voz, peticion.texto)) sintetizar(peticion.voz, peticion.texto)
            } finally {
                synchronized(lockCola) { enCurso = null }
            }
        }
    }

    private fun siguiente(): Peticion? =
        synchronized(lockCola) {
            val primera = pendientes.entries.firstOrNull()
            if (primera == null) {
                trabajando = false
                null
            } else {
                pendientes.remove(primera.key)
                enCurso = primera.key
                primera.value
            }
        }

    /** Devuelve el PCM de la frase (de la caché o recién sintetizado) o `null` si no se pudo sintetizar. */
    private suspend fun sintetizar(
        voz: VozCopiloto,
        texto: String,
    ): ShortArray? =
        withContext(dispatcherSintesis) {
            turnoSintesis.withLock {
                cache.leer(voz, texto) ?: try {
                    sintetizador.sintetizar(voz, texto).muestras.also { cache.guardar(voz, texto, it) }
                } catch (e: ErrorSintesis) {
                    Log.w(TAG, "No se pudo sintetizar una frase de ${texto.length} caracteres", e)
                    null
                }
            }
        }

    private suspend fun hablar(peticion: Peticion) {
        val muestras = cache.leer(peticion.voz, peticion.texto) ?: sintetizar(peticion.voz, peticion.texto) ?: return
        estaHablando.value = true
        try {
            if (foco.pedir()) reproductor.reproducir(muestras, sintetizador.frecuenciaHz(peticion.voz))
        } catch (e: ErrorSintesis) {
            Log.w(TAG, "No se pudo reproducir la frase", e)
        } finally {
            foco.soltar()
            estaHablando.value = false
        }
    }

    private companion object {
        const val TAG = "Locutor"
        const val SEGUNDOS_POR_CARACTER = 0.07
        const val BYTES_POR_MUESTRA = 2
        const val FRASE_PRUEBA = "Atención: izquierda cuatro, cien metros, y después derecha seis."
    }
}
