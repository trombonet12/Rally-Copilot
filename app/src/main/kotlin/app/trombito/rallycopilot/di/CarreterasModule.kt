package app.trombito.rallycopilot.di

import app.trombito.rallycopilot.data.carreteras.CalculadorCarreteras
import app.trombito.rallycopilot.domain.carreteras.CalculoCarreteras
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CarreterasModule {
    @Binds
    abstract fun calculoCarreteras(implementacion: CalculadorCarreteras): CalculoCarreteras
}
