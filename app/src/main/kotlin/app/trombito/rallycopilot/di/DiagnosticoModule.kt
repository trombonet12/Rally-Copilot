package app.trombito.rallycopilot.di

import android.content.Context
import android.os.SystemClock
import app.trombito.rallycopilot.data.diagnostico.ObservadorDiagnostico
import app.trombito.rallycopilot.data.diagnostico.RegistroDiagnosticoArchivo
import app.trombito.rallycopilot.data.diagnostico.carpetaExternaPrivada
import app.trombito.rallycopilot.domain.copia.CopiaAutomaticaDatos
import app.trombito.rallycopilot.domain.diagnostico.RegistroDiagnostico
import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DiagnosticoModule {
    /**
     * Respeta el interruptor «Copia automática»: apagado, no se registra nada. La copia se pide con `Lazy` porque ella
     * misma usa el registro (se anota «copia hecha»).
     */
    @Provides
    @Singleton
    fun registroDiagnostico(
        @ApplicationContext contexto: Context,
        copia: Lazy<CopiaAutomaticaDatos>,
        @DispatcherIo dispatcher: CoroutineDispatcher,
    ): RegistroDiagnostico =
        RegistroDiagnosticoArchivo(
            carpeta = { carpetaExternaPrivada(contexto) },
            activo = { copia.get().activa.value },
            dispatcher = dispatcher,
        )

    /** Las dependencias entran con `Lazy` dentro de flujos: nada se crea hasta que el observador arranca. */
    @Provides
    @Singleton
    fun observadorDiagnostico(
        navegacion: Lazy<ControlNavegacion>,
        crono: Lazy<ProveedorCrono>,
        ubicacion: Lazy<FuenteUbicacion>,
        registro: RegistroDiagnostico,
        @ApplicationScope alcance: CoroutineScope,
    ): ObservadorDiagnostico =
        ObservadorDiagnostico(
            estado = flow { emitAll(navegacion.get().estado) },
            crono = flow { emitAll(crono.get().crono) },
            posiciones = { ubicacion.get().posiciones() },
            registro = registro,
            alcance = alcance,
            relojMonotonicoMs = SystemClock::elapsedRealtime,
        )
}
