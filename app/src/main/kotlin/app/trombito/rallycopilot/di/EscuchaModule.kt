package app.trombito.rallycopilot.di

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.trombito.rallycopilot.data.db.EdicionesDao
import app.trombito.rallycopilot.data.db.MarcasDao
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.data.escucha.ContextoConduccionImpl
import app.trombito.rallycopilot.data.escucha.ControlOrdenesImpl
import app.trombito.rallycopilot.data.escucha.EjecutorOrdenes
import app.trombito.rallycopilot.data.escucha.EscuchaContinua
import app.trombito.rallycopilot.data.escucha.EscuchaImpl
import app.trombito.rallycopilot.data.escucha.FabricaAudio
import app.trombito.rallycopilot.data.escucha.FabricaAudioRecord
import app.trombito.rallycopilot.data.escucha.FabricaReconocedor
import app.trombito.rallycopilot.data.escucha.FabricaReconocedorVosk
import app.trombito.rallycopilot.data.escucha.FocoAudioExclusivo
import app.trombito.rallycopilot.data.escucha.MemoriaFrases
import app.trombito.rallycopilot.data.escucha.RepositorioEdicionesRoom
import app.trombito.rallycopilot.data.escucha.RepositorioMarcasRoom
import app.trombito.rallycopilot.data.escucha.ReproductorTic
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.escucha.ControlOrdenes
import app.trombito.rallycopilot.domain.escucha.Escucha
import app.trombito.rallycopilot.domain.escucha.ProveedorContextoConduccion
import app.trombito.rallycopilot.domain.escucha.RepositorioEdiciones
import app.trombito.rallycopilot.domain.escucha.RepositorioMarcas
import app.trombito.rallycopilot.domain.notas.ProveedorNotas
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import app.trombito.rallycopilot.domain.voz.Locutor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** Órdenes de voz: escucha con Vosk, ejecución sobre la conducción, marcas y ediciones de notas en Room. */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions") // un @Provides por pieza de la escucha, las órdenes y las ediciones
object EscuchaModule {
    @Provides
    fun marcasDao(base: RallyDatabase): MarcasDao = base.marcasDao()

    @Provides
    fun edicionesDao(base: RallyDatabase): EdicionesDao = base.edicionesDao()

    @Provides
    @Singleton
    fun fabricaAudio(): FabricaAudio = FabricaAudioRecord()

    @Provides
    @Singleton
    fun fabricaReconocedor(
        @ApplicationContext contexto: Context,
    ): FabricaReconocedor = FabricaReconocedorVosk(contexto)

    @Provides
    @Singleton
    fun escucha(
        @ApplicationContext contexto: Context,
        audio: FabricaAudio,
        reconocedores: FabricaReconocedor,
    ): Escucha =
        EscuchaImpl(
            foco = FocoAudioExclusivo(contexto),
            audio = audio,
            reconocedores = reconocedores,
            permiso = { tienePermisoMicrofono(contexto) },
            dispatcher = Dispatchers.IO,
        )

    @Provides
    @Singleton
    fun repositorioEdiciones(dao: EdicionesDao): RepositorioEdiciones = RepositorioEdicionesRoom(dao)

    @Provides
    @Singleton
    fun repositorioMarcas(dao: MarcasDao): RepositorioMarcas = RepositorioMarcasRoom(dao)

    @Provides
    @Singleton
    fun contextoConduccion(
        notas: ProveedorNotas,
        crono: ProveedorCrono,
    ): ProveedorContextoConduccion = ContextoConduccionImpl(notas, crono)

    @Provides
    @Singleton
    @Suppress("LongParameterList") // colaboradores del ejecutor de órdenes
    fun controlOrdenes(
        escucha: Escucha,
        ajustes: RepositorioAjustesConduccion,
        locutor: Locutor,
        memoria: MemoriaFrases,
        ediciones: RepositorioEdiciones,
        marcas: RepositorioMarcas,
        contexto: ProveedorContextoConduccion,
    ): ControlOrdenes =
        ControlOrdenesImpl(
            escucha = escucha,
            ejecutor =
                EjecutorOrdenes(ajustes, locutor, memoria, ediciones, marcas, contexto, System::currentTimeMillis),
            locutor = locutor,
            ambito = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )

    @Provides
    fun escuchaContinua(
        @ApplicationContext contexto: Context,
        audio: FabricaAudio,
        reconocedores: FabricaReconocedor,
        ajustes: RepositorioAjustesConduccion,
        control: ControlOrdenes,
    ): EscuchaContinua =
        EscuchaContinua(
            audio = audio,
            reconocedores = reconocedores,
            permiso = { tienePermisoMicrofono(contexto) },
            ajustes = ajustes.ajustes,
            control = control,
            dispatcher = Dispatchers.IO,
            tic = ReproductorTic()::sonar,
        )

    private fun tienePermisoMicrofono(contexto: Context): Boolean =
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
