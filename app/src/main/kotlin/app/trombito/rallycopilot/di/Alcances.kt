package app.trombito.rallycopilot.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Dispatcher para trabajo de disco y red (`Dispatchers.IO`); en las pruebas se sustituye por uno de prueba. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DispatcherIo

/** Dispatcher para trabajo de CPU (`Dispatchers.Default`); en las pruebas se sustituye por uno de prueba. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DispatcherDefault

/**
 * El `CoroutineScope` de vida de proceso (`SupervisorJob` + `Dispatchers.Default`), que no se cancela nunca. Solo para
 * trabajo que debe vivir lo que la app; lo que tiene un ciclo de vida propio (el servicio de navegación) crea y
 * cancela el suyo.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Dónde se nombran `Dispatchers.IO` y `Dispatchers.Default` y se crea el scope de proceso para el resto de la app. */
@Module
@InstallIn(SingletonComponent::class)
object AlcancesModule {
    @Provides
    @DispatcherIo
    fun dispatcherIo(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DispatcherDefault
    fun dispatcherDefault(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun alcanceDeProceso(
        @DispatcherDefault dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}
