package app.trombito.rallycopilot.di

import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.data.db.TramosDao
import app.trombito.rallycopilot.data.tramos.CronoEnVivoImpl
import app.trombito.rallycopilot.data.tramos.RepositorioTramosRoom
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TramosModule {
    @Binds
    abstract fun proveedorCrono(implementacion: CronoEnVivoImpl): ProveedorCrono

    companion object {
        @Provides
        fun tramosDao(base: RallyDatabase): TramosDao = base.tramosDao()

        @Provides
        @Singleton
        fun repositorioTramos(dao: TramosDao): RepositorioTramos = RepositorioTramosRoom(dao, Dispatchers.IO)
    }
}
