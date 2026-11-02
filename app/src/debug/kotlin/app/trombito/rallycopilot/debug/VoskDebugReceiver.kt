package app.trombito.rallycopilot.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import app.trombito.rallycopilot.data.escucha.FabricaReconocedor
import app.trombito.rallycopilot.data.escucha.GramaticaOrdenes
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Solo debug. Comprueba que Vosk carga el modelo con la gramática cerrada y cuánto tarda, sin micrófono: crea el
 * reconocedor de órdenes (la primera vez copia y carga el modelo), le da un segundo de silencio y crea el de «Copiloto».
 * Escribe los tiempos en logcat (tag `VoskDebug`):
 * `adb shell am broadcast -n app.trombito.rallycopilot.dev/app.trombito.rallycopilot.debug.VoskDebugReceiver
 *  -a app.trombito.rallycopilot.dev.DEBUG_VOSK`
 */
class VoskDebugReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Acceso {
        fun reconocedores(): FabricaReconocedor
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACCION) return
        val fabrica =
            EntryPointAccessors
                .fromApplication(
                    context.applicationContext,
                    Acceso::class.java,
                ).reconocedores()
        val pendiente = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                probar(fabrica)
            } finally {
                pendiente.finish()
            }
        }
    }

    private fun probar(fabrica: FabricaReconocedor) {
        val frases = GramaticaOrdenes.frasesOrdenes().size
        val inicio = SystemClock.elapsedRealtime()
        val ordenes = fabrica.crear(GramaticaOrdenes.jsonOrdenes())
        val cargaMs = SystemClock.elapsedRealtime() - inicio
        if (ordenes == null) {
            Log.e(ETIQUETA, "FALLO: no se pudo crear el reconocedor de órdenes ($frases frases) tras $cargaMs ms")
            return
        }
        ordenes.use {
            Log.i(ETIQUETA, "OK: modelo cargado y reconocedor de órdenes ($frases frases) creado en $cargaMs ms")
            val silencio = ShortArray(MUESTRAS_POR_SEGUNDO)
            val t = SystemClock.elapsedRealtime()
            it.aceptar(silencio, silencio.size)
            val fin = it.fin()
            Log.i(ETIQUETA, "1 s de silencio procesado en ${SystemClock.elapsedRealtime() - t} ms (texto='$fin')")
        }
        val t2 = SystemClock.elapsedRealtime()
        val activacion = fabrica.crear(GramaticaOrdenes.jsonActivacion())
        Log.i(
            ETIQUETA,
            "Reconocedor de «Copiloto» ${if (activacion != null) "creado" else "NO creado"} en " +
                "${SystemClock.elapsedRealtime() - t2} ms (modelo ya en memoria)",
        )
        activacion?.close()
        Log.i(ETIQUETA, "Memoria nativa del proceso: ${Debug.getNativeHeapAllocatedSize() / BYTES_POR_MB} MB")
    }

    private companion object {
        const val ACCION = "app.trombito.rallycopilot.dev.DEBUG_VOSK"
        const val ETIQUETA = "VoskDebug"
        const val MUESTRAS_POR_SEGUNDO = 16_000
        const val BYTES_POR_MB = 1024 * 1024
    }
}
