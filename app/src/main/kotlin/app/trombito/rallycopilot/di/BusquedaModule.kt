package app.trombito.rallycopilot.di

import android.content.Context
import app.trombito.rallycopilot.data.busqueda.BuscadorSqlite
import app.trombito.rallycopilot.data.datosmapa.EsperaDatosSembrados
import app.trombito.rallycopilot.domain.busqueda.Buscador
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
object BusquedaModule {
    @Provides
    @Singleton
    fun buscador(
        @ApplicationContext contexto: Context,
        siembra: EsperaDatosSembrados,
        @DispatcherIo dispatcher: CoroutineDispatcher,
    ): Buscador = BuscadorSqlite(File(contexto.filesDir, "mapa/busqueda-mallorca.sqlite"), dispatcher, siembra)
}
