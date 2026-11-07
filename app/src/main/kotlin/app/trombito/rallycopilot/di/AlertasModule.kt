package app.trombito.rallycopilot.di

import android.content.Context
import app.trombito.rallycopilot.data.alertas.AlertasEnVivoImpl
import app.trombito.rallycopilot.data.alertas.CargadorRadares
import app.trombito.rallycopilot.data.alertas.FuenteRadares
import app.trombito.rallycopilot.data.alertas.RadaresParaMapa
import app.trombito.rallycopilot.data.alertas.RepositorioIncidenciasDgt
import app.trombito.rallycopilot.data.alertas.URL_DATEX_DGT
import app.trombito.rallycopilot.data.datosmapa.ClienteHttp
import app.trombito.rallycopilot.data.datosmapa.ConfigDatosMapa
import app.trombito.rallycopilot.domain.alertas.ProveedorAlertas
import app.trombito.rallycopilot.domain.alertas.ProveedorRadaresMapa
import app.trombito.rallycopilot.domain.alertas.RepositorioIncidencias
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AlertasModule {
    @Binds
    abstract fun proveedorAlertas(implementacion: AlertasEnVivoImpl): ProveedorAlertas

    @Binds
    abstract fun fuenteRadares(implementacion: CargadorRadares): FuenteRadares

    @Binds
    abstract fun proveedorRadaresMapa(implementacion: RadaresParaMapa): ProveedorRadaresMapa

    companion object {
        @Provides
        @Singleton
        fun repositorioIncidencias(
            @ApplicationContext contexto: Context,
            config: ConfigDatosMapa,
            @DispatcherIo dispatcher: CoroutineDispatcher,
        ): RepositorioIncidencias =
            RepositorioIncidenciasDgt(
                ClienteHttp.paraDgt(config),
                URL_DATEX_DGT,
                File(contexto.filesDir, "alertas"),
                System::currentTimeMillis,
                dispatcher,
            )
    }
}
