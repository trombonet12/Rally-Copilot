package app.trombito.rallycopilot.data.voz

import android.os.SystemClock
import android.util.Log
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.PrioridadVoz
import app.trombito.rallycopilot.domain.voz.VelocidadHabla
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
 * - **Velocidad de habla**: [usarVelocidad] cambia la de lo que se sintetice a partir de ahora; cada velocidad
 *   tiene su propia caché y la cola de [preparar] de la anterior se descarta.
 * - **Volumen**: [usarVolumen] aplica una ganancia al PCM justo antes de reproducir (la caché guarda el original), así
 *   que el cambio vale para la frase siguiente y puede subir de 100 % sin saturar ([GananciaPcm]).
 * - **[decir]**: una frase a la vez, por orden de llegada. Sintetiza si no está en caché, pide el foco, suena y lo
 *   suelta. [hablando] es `true` desde que se pide el foco hasta que acaba; si se deniega, no suena y vuelve a `false`.
 *   Una frase [PrioridadVoz.URGENTE] vacía las que esperan sin haber empezado y pasa la primera (la que ya suena no se
 *   corta); una con `caducaMs` se descarta si su plazo (del [reloj] monótono) llega antes de empezar a sonar.
 */
@Suppress("TooManyFunctions", "LongParameterList") // una función por orden del Locutor; colaboradores de voz y reloj
class LocutorImpl(
    private val cache: CacheFrases,
    private val sintetizador: SintetizadorTts,
    private val reproductor: ReproductorPcm,
    private val foco: FocoAudio,
    private val ambito: CoroutineScope,
    private val dispatcherSintesis: CoroutineDispatcher,
    /** Reloj monótono (ms) contra el que se miden las caducidades de [decir]. */
    private val reloj: () -> Long = { SystemClock.elapsedRealtime() },
    /**
     * La voz con la que se habla de verdad cuando se pide una [VozCopiloto] (`VocesInstaladas.efectiva`): una voz
     * que no está instalada (Sharvard aún sin descargar) se sustituye por Dave. Se consulta en cada frase, así que en
     * cuanto acaba la descarga se empieza a usar. La caché de frases es de la voz efectiva: no se mezclan.
     */
    private val vozDisponible: (VozCopiloto) -> VozCopiloto = { it },
) : Locutor {
    private class Peticion(
        val voz: VozCopiloto,
        val texto: String,
        val velocidad: VelocidadHabla,
        val caducaMs: Long? = null,
    )

    private val estaHablando = MutableStateFlow(false)
    override val hablando: StateFlow<Boolean> = estaHablando.asStateFlow()

    @Volatile private var voz = VozCopiloto.DAVEFX

    @Volatile private var velocidad = VelocidadHabla.NORMAL

    @Volatile private var volumenPorcentaje = GananciaPcm.NORMAL

    private val turnoSintesis = Mutex()

    /** Frases de [decir] que esperan turno; se accede siempre con [lockTurnos]. */
    private val esperaDeVoz = ArrayDeque<Peticion>()
    private val lockTurnos = Any()

    /** Despierta al hablante cuando hay algo en [esperaDeVoz]; conflada: basta una señal para vaciar la cola. */
    private val avisoDeVoz = Channel<Unit>(Channel.CONFLATED)

    private val lockCola = Any()
    private val pendientes = LinkedHashMap<String, Peticion>()
    private var enCurso: String? = null
    private var trabajando = false

    init {
        ambito.launch {
            while (avisoDeVoz.receiveCatching().isSuccess) {
                while (true) {
                    val peticion = synchronized(lockTurnos) { esperaDeVoz.removeFirstOrNull() } ?: break
                    hablar(peticion)
                }
            }
        }
    }

    override fun usarVoz(v: VozCopiloto) {
        voz = v
        synchronized(lockCola) { pendientes.clear() }
    }

    override fun usarVelocidad(v: VelocidadHabla) {
        velocidad = v
        synchronized(lockCola) { pendientes.clear() }
    }

    override fun preparar(textos: List<String>) {
        val vozActual = vozDisponible(voz)
        val velocidadActual = velocidad
        val nuevas =
            textos
                .filter { it.isNotBlank() }
                .distinct()
                .filterNot { cache.contiene(vozActual, it, velocidadActual) }
        val lanzar =
            synchronized(lockCola) {
                pendientes.clear()
                for (texto in nuevas) {
                    val clave = CacheFrases.clave(vozActual, texto, velocidadActual)
                    if (clave != enCurso) pendientes[clave] = Peticion(vozActual, texto, velocidadActual)
                }
                (pendientes.isNotEmpty() && !trabajando).also { if (it) trabajando = true }
            }
        if (lanzar) ambito.launch { trabajarCola() }
    }

    override fun decir(
        texto: String,
        prioridad: PrioridadVoz,
        caducaMs: Long?,
    ) {
        if (texto.isNotBlank()) encolar(Peticion(vozDisponible(voz), texto, velocidad, caducaMs), prioridad)
    }

    private fun encolar(
        peticion: Peticion,
        prioridad: PrioridadVoz = PrioridadVoz.NORMAL,
    ) {
        synchronized(lockTurnos) {
            if (prioridad == PrioridadVoz.URGENTE) esperaDeVoz.clear()
            esperaDeVoz.addLast(peticion)
        }
        avisoDeVoz.trySend(Unit)
    }

    override fun duracionEstimadaS(texto: String): Double {
        val vozActual = vozDisponible(voz)
        val velocidadActual = velocidad
        val bytes = cache.bytes(vozActual, texto, velocidadActual)
        return if (bytes != null) {
            bytes / BYTES_POR_MUESTRA / sintetizador.frecuenciaHz(vozActual).toDouble()
        } else {
            texto.length * SEGUNDOS_POR_CARACTER / velocidadActual.factor
        }
    }

    override fun probar(v: VozCopiloto) {
        encolar(Peticion(vozDisponible(v), FRASE_PRUEBA, velocidad))
    }

    override fun usarVolumen(porcentaje: Int) {
        volumenPorcentaje = porcentaje.coerceIn(GananciaPcm.MINIMA, GananciaPcm.MAXIMA)
    }

    override fun probarFrase(
        v: VozCopiloto,
        texto: String,
    ) {
        if (texto.isNotBlank()) encolar(Peticion(vozDisponible(v), texto, velocidad))
    }

    override fun cerrar() {
        ambito.cancel()
        avisoDeVoz.close()
        synchronized(lockTurnos) { esperaDeVoz.clear() }
        synchronized(lockCola) { pendientes.clear() }
        foco.soltar()
        // liberar() espera a que acabe la frase en curso: fuera del hilo que llama.
        Thread({ sintetizador.liberar() }, "voz-liberar").start()
    }

    private suspend fun trabajarCola() {
        while (true) {
            val peticion = siguiente() ?: return
            try {
                if (!cache.contiene(peticion.voz, peticion.texto, peticion.velocidad)) sintetizar(peticion)
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
    private suspend fun sintetizar(p: Peticion): ShortArray? =
        withContext(dispatcherSintesis) {
            turnoSintesis.withLock {
                cache.leer(p.voz, p.texto, p.velocidad) ?: try {
                    sintetizador.sintetizar(p.voz, p.texto, p.velocidad).muestras.also {
                        cache.guardar(p.voz, p.texto, it, p.velocidad)
                    }
                } catch (e: ErrorSintesis) {
                    Log.w(TAG, "No se pudo sintetizar una frase de ${p.texto.length} caracteres", e)
                    null
                }
            }
        }

    private fun caducada(peticion: Peticion): Boolean = peticion.caducaMs?.let { reloj() > it } ?: false

    /** El PCM de [peticion], o `null` si caducó (antes o mientras se sintetizaba) o no se pudo sintetizar. */
    private suspend fun muestrasVigentes(peticion: Peticion): ShortArray? {
        if (caducada(peticion)) return null
        val muestras = cache.leer(peticion.voz, peticion.texto, peticion.velocidad) ?: sintetizar(peticion)
        // Sintetizar puede tardar: si mientras tanto caducó, ya no vale.
        return muestras?.takeUnless { caducada(peticion) }
    }

    private suspend fun hablar(peticion: Peticion) {
        val muestras = muestrasVigentes(peticion) ?: return
        estaHablando.value = true
        try {
            if (foco.pedir()) {
                val conGanancia = GananciaPcm.aplicar(muestras, volumenPorcentaje)
                reproductor.reproducir(conGanancia, sintetizador.frecuenciaHz(peticion.voz))
            }
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
