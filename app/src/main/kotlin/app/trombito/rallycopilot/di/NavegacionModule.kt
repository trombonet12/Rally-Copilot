package app.trombito.rallycopilot.di

import app.trombito.rallycopilot.data.navegacion.AlmacenDestinosRecientes
import app.trombito.rallycopilot.data.navegacion.ControlNavegacionImpl
import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import app.trombito.rallycopilot.domain.navegacion.DestinosRecientes
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NavegacionModule {
    @Binds
    abstract fun controlNavegacion(implementacion: ControlNavegacionImpl): ControlNavegacion

    @Binds
    abstract fun destinosRecientes(implementacion: AlmacenDestinosRecientes): DestinosRecientes
}
