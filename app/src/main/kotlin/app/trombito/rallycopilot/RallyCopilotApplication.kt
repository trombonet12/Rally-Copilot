package app.trombito.rallycopilot

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.trombito.rallycopilot.data.datosmapa.EsperaDatosSembrados
import app.trombito.rallycopilot.data.diagnostico.ManejadorCrashes
import app.trombito.rallycopilot.data.diagnostico.ObservadorDiagnostico
import app.trombito.rallycopilot.data.escucha.FabricaReconocedor
import app.trombito.rallycopilot.data.tramos.MigradorTramosVia
import app.trombito.rallycopilot.data.voz.LiberadorModelos
import app.trombito.rallycopilot.di.ApplicationScope
import app.trombito.rallycopilot.di.DispatcherIo
import app.trombito.rallycopilot.domain.copia.CopiaAutomaticaDatos
import app.trombito.rallycopilot.domain.diagnostico.RegistroDiagnostico
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

    @Inject lateinit var copiaAutomatica: CopiaAutomaticaDatos

    @Inject lateinit var migradorTramosVia: MigradorTramosVia

    @Inject lateinit var registro: RegistroDiagnostico

    @Inject lateinit var observadorDiagnostico: ObservadorDiagnostico

    @Inject @ApplicationScope
    lateinit var segundoPlano: CoroutineScope

    @Inject @DispatcherIo
    lateinit var dispatcherIo: CoroutineDispatcher

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(fabricaTrabajos).build()

    override fun onCreate() {
        super.onCreate()
        // Registro de diagnóstico (para `adb pull`): crashes, arranque y eventos de la conducción, sin coordenadas.
        ManejadorCrashes.instalar(registro)
        registro.info("App", "arranque version=${versionApp()}")
        observadorDiagnostico.iniciar()
        // Vosk se copia a disco en segundo plano en cuanto acaba la siembra de los datos (sin iniciarla: la inicia
        // quien abre el mapa); así la primera activación de «Copiloto» no espera a copiar 57 MiB.
        segundoPlano.launch(dispatcherIo) {
            siembra.listo.first { it }
            reconocedores.precopiar()
        }
        // Copia automática diaria (para `adb pull`): sembrados los datos, si la última tiene más de 20 h.
        segundoPlano.launch(dispatcherIo) {
            siembra.listo.first { it }
            copiaAutomatica.copiarSiToca(System.currentTimeMillis())
        }
        // Tramos de vía (H23): sembrados los datos, trocea los viajes ya guardados (una vez, reanudable e idempotente).
        segundoPlano.launch(dispatcherIo) {
            siembra.listo.first { it }
            migradorTramosVia.migrar()
        }
        // La biblioteca nativa del mapa se carga ya, fuera del hilo principal (el primer mapa no la espera).
        segundoPlano.launch(dispatcherIo) { precargarBibliotecaMapa() }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // solo para el registro
    private fun versionApp(): String =
        try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "desconocida"
        } catch (e: Exception) {
            "desconocida"
        }

    /** Con la app en segundo plano y el sistema escaso de memoria, suelta Piper y Vosk (se recargan al usarlos). */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) liberadorModelos.liberarEnSegundoPlano()
    }
}
