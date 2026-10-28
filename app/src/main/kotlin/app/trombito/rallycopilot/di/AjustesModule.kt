package app.trombito.rallycopilot.di

import app.trombito.rallycopilot.data.ajustes.RepositorioAjustesConduccionImpl
import app.trombito.rallycopilot.data.db.AjustesConduccionDao
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.data.db.RecorridosDao
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridosRoom
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AjustesModule {
    @Binds
    abstract fun repositorioAjustesConduccion(
        implementacion: RepositorioAjustesConduccionImpl,
    ): RepositorioAjustesConduccion

    companion object {
        @Provides
        fun ajustesConduccionDao(base: RallyDatabase): AjustesConduccionDao = base.ajustesConduccionDao()

        @Provides
        fun recorridosDao(base: RallyDatabase): RecorridosDao = base.recorridosDao()

        @Provides
        @Singleton
        fun repositorioRecorridos(dao: RecorridosDao): RepositorioRecorridos =
            RepositorioRecorridosRoom(dao, Dispatchers.IO)
    }
}
