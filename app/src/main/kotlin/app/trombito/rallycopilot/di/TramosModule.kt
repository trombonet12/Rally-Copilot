package app.trombito.rallycopilot.di

import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.data.db.TramosDao
import app.trombito.rallycopilot.data.tramos.CronoEnVivoImpl
import app.trombito.rallycopilot.data.tramos.ExportadorTramo
import app.trombito.rallycopilot.data.tramos.GuardadorTramoArchivo
import app.trombito.rallycopilot.data.tramos.GuardadorTramos
import app.trombito.rallycopilot.data.tramos.NombradorTramos
import app.trombito.rallycopilot.data.tramos.RepositorioTramosRoom
import app.trombito.rallycopilot.domain.datos.PrecargaDetalle
import app.trombito.rallycopilot.domain.tramos.ControlSalidaCrono
import app.trombito.rallycopilot.domain.tramos.ExportaTramo
import app.trombito.rallycopilot.domain.tramos.GuardaTramoArchivo
import app.trombito.rallycopilot.domain.tramos.GuardaTraza
import app.trombito.rallycopilot.domain.tramos.NombraTraza
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TramosModule {
    @Binds
    abstract fun proveedorCrono(implementacion: CronoEnVivoImpl): ProveedorCrono

    @Binds
    abstract fun controlSalidaCrono(implementacion: CronoEnVivoImpl): ControlSalidaCrono

    @Binds
    abstract fun guardaTraza(implementacion: GuardadorTramos): GuardaTraza

    @Binds
    abstract fun guardaTramoArchivo(implementacion: GuardadorTramoArchivo): GuardaTramoArchivo

    @Binds
    abstract fun exportaTramo(implementacion: ExportadorTramo): ExportaTramo

    @Binds
    abstract fun nombraTraza(implementacion: NombradorTramos): NombraTraza

    @Binds
    abstract fun repositorioTramos(implementacion: RepositorioTramosRoom): RepositorioTramos

    companion object {
        @Provides
        fun tramosDao(base: RallyDatabase): TramosDao = base.tramosDao()
    }
}
