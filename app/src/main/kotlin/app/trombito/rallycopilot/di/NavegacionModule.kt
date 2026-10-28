package app.trombito.rallycopilot.di

import app.trombito.rallycopilot.data.navegacion.ControlNavegacionImpl
import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NavegacionModule {
    @Binds
    abstract fun controlNavegacion(implementacion: ControlNavegacionImpl): ControlNavegacion
}
