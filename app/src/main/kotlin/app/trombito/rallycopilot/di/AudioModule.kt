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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
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
        @ApplicationScope ambito: CoroutineScope,
    ): RutaAudio =
        RutaAudioCasco(
            sistema = sistema,
            sdk = Build.VERSION.SDK_INT,
            permiso = { PermisoBluetooth.concedido(contexto) },
            ambito = ambito,
            reloj = { SystemClock.elapsedRealtime() },
        )
}
