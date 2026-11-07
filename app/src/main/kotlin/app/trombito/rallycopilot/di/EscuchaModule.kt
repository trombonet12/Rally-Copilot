package app.trombito.rallycopilot.di

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.trombito.rallycopilot.data.db.EdicionesDao
import app.trombito.rallycopilot.data.db.MarcasDao
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.data.escucha.ConduccionPorVoz
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
import app.trombito.rallycopilot.data.escucha.LugaresPronunciables
import app.trombito.rallycopilot.data.escucha.MemoriaFrases
import app.trombito.rallycopilot.data.escucha.NavegacionPorVoz
import app.trombito.rallycopilot.data.escucha.RepositorioEdicionesRoom
import app.trombito.rallycopilot.data.escucha.RepositorioMarcasRoom
import app.trombito.rallycopilot.data.escucha.ReproductorTic
import app.trombito.rallycopilot.data.escucha.RespondedorPreguntas
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.busqueda.Buscador
import app.trombito.rallycopilot.domain.escucha.ControlOrdenes
import app.trombito.rallycopilot.domain.escucha.Escucha
import app.trombito.rallycopilot.domain.escucha.ProveedorContextoConduccion
import app.trombito.rallycopilot.domain.escucha.RepositorioEdiciones
import app.trombito.rallycopilot.domain.escucha.RepositorioMarcas
import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import app.trombito.rallycopilot.domain.navegacion.IniciarRutaA
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.tramos.ControlSalidaCrono
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.vias.ProveedorViaActual
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Singleton

/** Órdenes de voz: escucha con Vosk, ejecución sobre la conducción, marcas y ediciones de notas en Room. */
@Module
@InstallIn(SingletonComponent::class)
abstract class EscuchaModule {
    @Binds
    abstract fun repositorioEdiciones(repositorio: RepositorioEdicionesRoom): RepositorioEdiciones

    @Binds
    abstract fun repositorioMarcas(repositorio: RepositorioMarcasRoom): RepositorioMarcas

    @Binds
    abstract fun contextoConduccion(contexto: ContextoConduccionImpl): ProveedorContextoConduccion

    companion object {
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
            lugares: LugaresPronunciables,
            @DispatcherIo dispatcher: CoroutineDispatcher,
        ): Escucha =
            EscuchaImpl(
                foco = FocoAudioExclusivo(contexto),
                audio = audio,
                reconocedores = reconocedores,
                permiso = { tienePermisoMicrofono(contexto) },
                dispatcher = dispatcher,
                lugares = lugares::nombres,
            )

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
            navegacion: ControlNavegacion,
            crono: ProveedorCrono,
            salida: ControlSalidaCrono,
            vias: ProveedorViaActual,
            ubicacion: FuenteUbicacion,
            buscador: Buscador,
            enrutador: Enrutador,
            @ApplicationScope ambito: CoroutineScope,
        ): ControlOrdenes {
            val preguntas =
                RespondedorPreguntas(
                    estado = { navegacion.estado.value },
                    crono = crono,
                    vias = vias,
                    ubicacion = ubicacion,
                    ahoraMs = System::currentTimeMillis,
                )
            val conduccion =
                ConduccionPorVoz(
                    estado = { navegacion.estado.value },
                    terminar = navegacion::terminar,
                    crono = crono,
                    salida = salida,
                    locutor = locutor,
                    ambito = ambito,
                    puedeHablar = {
                        ajustes.ajustes.first().let { it.voz != null && it.modoCopiloto != ModoCopiloto.SILENCIO }
                    },
                )
            val navegacionPorVoz =
                NavegacionPorVoz(
                    buscador = buscador,
                    iniciar = IniciarRutaA(navegacion, enrutador, ubicacion),
                    control = navegacion,
                    ajustes = ajustes,
                    ubicacion = ubicacion,
                    ahoraMs = System::currentTimeMillis,
                )
            return ControlOrdenesImpl(
                escucha = escucha,
                ejecutor =
                    EjecutorOrdenes(
                        ajustes,
                        locutor,
                        memoria,
                        ediciones,
                        marcas,
                        contexto,
                        System::currentTimeMillis,
                        preguntas,
                        conduccion,
                        navegacionPorVoz,
                    ),
                locutor = locutor,
                ambito = ambito,
            )
        }

        @Provides
        @Suppress("LongParameterList") // proveedor de Hilt: cada colaborador llega inyectado
        fun escuchaContinua(
            @ApplicationContext contexto: Context,
            audio: FabricaAudio,
            reconocedores: FabricaReconocedor,
            ajustes: RepositorioAjustesConduccion,
            control: ControlOrdenes,
            @DispatcherIo dispatcher: CoroutineDispatcher,
        ): EscuchaContinua =
            EscuchaContinua(
                audio = audio,
                reconocedores = reconocedores,
                permiso = { tienePermisoMicrofono(contexto) },
                ajustes = ajustes.ajustes,
                control = control,
                dispatcher = dispatcher,
                tic = ReproductorTic()::sonar,
            )

        private fun tienePermisoMicrofono(contexto: Context): Boolean =
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }
}
