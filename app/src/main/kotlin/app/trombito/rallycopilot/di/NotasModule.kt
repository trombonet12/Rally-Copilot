package app.trombito.rallycopilot.di

import app.trombito.rallycopilot.data.notas.ContextoNotasRepositorio
import app.trombito.rallycopilot.data.notas.FuenteContextoNotas
import app.trombito.rallycopilot.data.notas.NotasEnVivoImpl
import app.trombito.rallycopilot.domain.notas.ProveedorNotas
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NotasModule {
    @Binds
    abstract fun proveedorNotas(implementacion: NotasEnVivoImpl): ProveedorNotas

    @Binds
    abstract fun fuenteContextoNotas(implementacion: ContextoNotasRepositorio): FuenteContextoNotas
}
