package app.trombito.rallycopilot.di

import app.trombito.rallycopilot.data.perfil.CalculadorTablero
import app.trombito.rallycopilot.data.perfil.CalculadorTemporada
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.data.vias.FuenteVias
import app.trombito.rallycopilot.domain.carreteras.CalculoCarreteras
import app.trombito.rallycopilot.domain.perfil.CalculoTablero
import app.trombito.rallycopilot.domain.perfil.CalculoTemporada
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/** El tablero y la temporada del piloto: un solo cálculo de cada uno, así su caché por recorrido se aprovecha. */
@Module
@InstallIn(SingletonComponent::class)
object PerfilModule {
    @Provides
    @Singleton
    fun calculoTablero(
        carreteras: CalculoCarreteras,
        recorridos: RepositorioRecorridos,
        tramos: RepositorioTramos,
        @DispatcherDefault dispatcher: CoroutineDispatcher,
    ): CalculoTablero = CalculadorTablero(carreteras, recorridos, tramos, dispatcher)

    @Provides
    @Singleton
    fun calculoTemporada(
        recorridos: RepositorioRecorridos,
        tramos: RepositorioTramos,
        vias: FuenteVias,
        @DispatcherDefault dispatcher: CoroutineDispatcher,
    ): CalculoTemporada = CalculadorTemporada(recorridos, tramos, vias, dispatcher)
}
