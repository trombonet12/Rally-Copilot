package app.trombito.rallycopilot.di

import android.content.Context
import app.trombito.rallycopilot.data.voz.CacheFrases
import app.trombito.rallycopilot.data.voz.FocoAudio
import app.trombito.rallycopilot.data.voz.GestorFocoAudio
import app.trombito.rallycopilot.data.voz.LocutorImpl
import app.trombito.rallycopilot.data.voz.MotorTtsSherpa
import app.trombito.rallycopilot.data.voz.ReproductorAudioTrack
import app.trombito.rallycopilot.data.voz.ReproductorPcm
import app.trombito.rallycopilot.data.voz.SintetizadorTts
import app.trombito.rallycopilot.data.voz.VocesInstaladas
import app.trombito.rallycopilot.domain.voz.Locutor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VozModule {
    @Binds
    abstract fun sintetizadorTts(motor: MotorTtsSherpa): SintetizadorTts

    @Binds
    @Singleton
    abstract fun focoAudio(gestor: GestorFocoAudio): FocoAudio

    companion object {
        @Provides
        @Singleton
        fun reproductorPcm(): ReproductorPcm = ReproductorAudioTrack()

        @Provides
        @Singleton
        @Suppress("LongParameterList") // proveedor de Hilt: cada colaborador llega inyectado
        fun locutor(
            @ApplicationContext contexto: Context,
            sintetizador: SintetizadorTts,
            reproductor: ReproductorPcm,
            foco: FocoAudio,
            voces: VocesInstaladas,
            @ApplicationScope ambito: CoroutineScope,
        ): Locutor =
            LocutorImpl(
                cache = CacheFrases(File(contexto.cacheDir, "voz")),
                sintetizador = sintetizador,
                reproductor = reproductor,
                foco = foco,
                vozDisponible = voces::efectiva,
                ambito = ambito,
                dispatcherSintesis =
                    Executors
                        .newSingleThreadExecutor { tarea -> Thread(tarea, "voz-sintesis") }
                        .asCoroutineDispatcher(),
            )
    }
}
