package app.trombito.rallycopilot.di

import app.trombito.rallycopilot.data.ajustes.RepositorioAjustesConduccionImpl
import app.trombito.rallycopilot.data.db.AjustesConduccionDao
import app.trombito.rallycopilot.data.db.GuiadoPendienteDao
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.data.db.RecorridosDao
import app.trombito.rallycopilot.data.navegacion.RepositorioGuiadoPendienteRoom
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridosRoom
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.navegacion.RepositorioGuiadoPendiente
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AjustesModule {
    @Binds
    abstract fun repositorioAjustesConduccion(
        implementacion: RepositorioAjustesConduccionImpl,
    ): RepositorioAjustesConduccion

    @Binds
    abstract fun repositorioGuiadoPendiente(implementacion: RepositorioGuiadoPendienteRoom): RepositorioGuiadoPendiente

    companion object {
        @Provides
        fun ajustesConduccionDao(base: RallyDatabase): AjustesConduccionDao = base.ajustesConduccionDao()

        @Provides
        fun recorridosDao(base: RallyDatabase): RecorridosDao = base.recorridosDao()

        @Provides
        fun guiadoPendienteDao(base: RallyDatabase): GuiadoPendienteDao = base.guiadoPendienteDao()

        @Provides
        @Singleton
        fun repositorioRecorridos(
            dao: RecorridosDao,
            @DispatcherIo dispatcher: CoroutineDispatcher,
        ): RepositorioRecorridos = RepositorioRecorridosRoom(dao, dispatcher)
    }
}
