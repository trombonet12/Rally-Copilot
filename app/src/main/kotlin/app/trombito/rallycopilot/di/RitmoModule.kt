package app.trombito.rallycopilot.di

import app.trombito.rallycopilot.data.ritmo.ProveedorPerfilRitmoImpl
import app.trombito.rallycopilot.domain.ritmo.ProveedorPerfilRitmo
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RitmoModule {
    @Binds
    abstract fun proveedorPerfilRitmo(implementacion: ProveedorPerfilRitmoImpl): ProveedorPerfilRitmo
}
