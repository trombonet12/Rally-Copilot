package app.trombito.rallycopilot.di

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import app.trombito.rallycopilot.data.audio.PermisoBluetooth
import app.trombito.rallycopilot.data.audio.RutaAudio
import app.trombito.rallycopilot.data.audio.RutaAudioCasco
import app.trombito.rallycopilot.data.audio.SistemaAudio
import app.trombito.rallycopilot.data.audio.SistemaAudioAndroid
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Singleton

/** Ruta de audio: voz y micrófono por el intercom del casco cuando lo hay, y por el móvil cuando no. */
@Module
@InstallIn(SingletonComponent::class)
object AudioModule {
    @Provides
    @Singleton
    fun sistemaAudio(
        @ApplicationContext contexto: Context,
    ): SistemaAudio = SistemaAudioAndroid(contexto.getSystemService(Context.AUDIO_SERVICE) as AudioManager)

    @Provides
    @Singleton
    fun rutaAudio(
        @ApplicationContext contexto: Context,
        sistema: SistemaAudio,
        ajustes: RepositorioAjustesConduccion,
        @ApplicationScope ambito: CoroutineScope,
    ): RutaAudio {
        // «Audio por el casco»: lectura síncrona de la última emisión; automático hasta que llegue el primer valor.
        val automatico = AtomicBoolean(true)
        val ruta =
            RutaAudioCasco(
                sistema = sistema,
                sdk = Build.VERSION.SDK_INT,
                permiso = { PermisoBluetooth.concedido(contexto) },
                ambito = ambito,
                reloj = { SystemClock.elapsedRealtime() },
                activado = { automatico.get() },
            )
        ajustes.ajustes
            .map { it.audioCasco }
            .distinctUntilChanged()
            .onEach {
                automatico.set(it)
                ruta.reevaluar()
            }.launchIn(ambito)
        return ruta
    }
}
