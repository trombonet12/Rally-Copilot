package app.trombito.rallycopilot.di

import app.trombito.rallycopilot.data.ruta.EnrutadorValhalla
import app.trombito.rallycopilot.data.ruta.FabricaMotorRutas
import app.trombito.rallycopilot.data.ruta.FabricaMotorValhalla
import app.trombito.rallycopilot.domain.ruta.Enrutador
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RutaModule {
    @Binds
    abstract fun enrutador(implementacion: EnrutadorValhalla): Enrutador

    @Binds
    abstract fun fabricaMotorRutas(implementacion: FabricaMotorValhalla): FabricaMotorRutas
}
