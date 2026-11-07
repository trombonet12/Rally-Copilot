package app.trombito.rallycopilot

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.trombito.rallycopilot.data.datosmapa.EsperaDatosSembrados
import app.trombito.rallycopilot.data.escucha.FabricaReconocedor
import app.trombito.rallycopilot.data.voz.LiberadorModelos
import app.trombito.rallycopilot.di.ApplicationScope
import app.trombito.rallycopilot.di.DispatcherIo
import app.trombito.rallycopilot.ui.mapa.precargarBibliotecaMapa
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RallyCopilotApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var fabricaTrabajos: HiltWorkerFactory

    @Inject lateinit var liberadorModelos: LiberadorModelos

    @Inject lateinit var reconocedores: FabricaReconocedor

    @Inject lateinit var siembra: EsperaDatosSembrados

    @Inject @ApplicationScope
    lateinit var segundoPlano: CoroutineScope

    @Inject @DispatcherIo
    lateinit var dispatcherIo: CoroutineDispatcher

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(fabricaTrabajos).build()

    override fun onCreate() {
        super.onCreate()
        // Vosk se copia a disco en segundo plano en cuanto acaba la siembra de los datos (sin iniciarla: la inicia
        // quien abre el mapa); así la primera activación de «Copiloto» no espera a copiar 57 MiB.
        segundoPlano.launch(dispatcherIo) {
            siembra.listo.first { it }
            reconocedores.precopiar()
        }
        // La biblioteca nativa del mapa se carga ya, fuera del hilo principal (el primer mapa no la espera).
        segundoPlano.launch(dispatcherIo) { precargarBibliotecaMapa() }
    }

    /** Con la app en segundo plano y el sistema escaso de memoria, suelta Piper y Vosk (se recargan al usarlos). */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) liberadorModelos.liberarEnSegundoPlano()
    }
}
