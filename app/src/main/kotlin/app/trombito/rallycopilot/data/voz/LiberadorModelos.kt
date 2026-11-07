package app.trombito.rallycopilot.data.voz

import app.trombito.rallycopilot.data.escucha.FabricaReconocedor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Suelta de la memoria los modelos de voz (Piper con onnxruntime y Vosk, del orden de 100–200 MiB de RAM nativa) cuando
 * no se necesitan: al acabar la conducción, tras un periodo de gracia ([programar]; una conducción nueva lo anula con
 * [cancelar]) y con poca memoria ([liberarYa]). Los dos se recargan solos, de forma perezosa, en el siguiente uso. Las
 * liberaciones esperan a la síntesis en curso, así que siempre van fuera del hilo principal.
 */
@Singleton
class LiberadorModelos internal constructor(
    private val sintetizador: SintetizadorTts,
    private val reconocedores: FabricaReconocedor,
    dispatcher: CoroutineDispatcher,
    private val graciaMs: Long,
) {
    @Inject
    constructor(
        sintetizador: SintetizadorTts,
        reconocedores: FabricaReconocedor,
    ) : this(sintetizador, reconocedores, Dispatchers.IO, GRACIA_MS)

    private val alcance = CoroutineScope(SupervisorJob() + dispatcher)
    private var pendiente: Job? = null

    /** Libera tras [graciaMs] (por si la última frase aún se está sintetizando o empieza otra conducción). */
    @Synchronized
    fun programar() {
        pendiente?.cancel()
        pendiente =
            alcance.launch {
                delay(graciaMs)
                liberarYa()
            }
    }

    /** Anula una liberación programada (empieza otra conducción). */
    @Synchronized
    fun cancelar() {
        pendiente?.cancel()
        pendiente = null
    }

    /** Libera ahora, en el hilo que llame (bloquea hasta que acabe la síntesis en curso). */
    fun liberarYa() {
        try {
            sintetizador.liberar()
        } finally {
            reconocedores.liberar()
        }
    }

    /** Como [liberarYa] pero fuera del hilo que llama (para `onTrimMemory`, que corre en el principal). */
    fun liberarEnSegundoPlano() {
        alcance.launch { liberarYa() }
    }

    companion object {
        /** Margen tras acabar la conducción: cubre la última frase («conducción terminada») y un reinicio rápido. */
        const val GRACIA_MS = 15_000L
    }
}
