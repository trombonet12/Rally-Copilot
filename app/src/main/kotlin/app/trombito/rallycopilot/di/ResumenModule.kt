package app.trombito.rallycopilot.di

import app.trombito.rallycopilot.data.carreteras.CalculadorCarreteras
import app.trombito.rallycopilot.data.resumen.CalculadorResumenViaje
import app.trombito.rallycopilot.data.resumen.ViajesTerminadosEnMemoria
import app.trombito.rallycopilot.domain.resumen.CalculoResumenViaje
import app.trombito.rallycopilot.domain.resumen.FuenteContextoVias
import app.trombito.rallycopilot.domain.resumen.ViajesTerminados
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ResumenModule {
    @Binds
    abstract fun calculoResumenViaje(implementacion: CalculadorResumenViaje): CalculoResumenViaje

    @Binds
    abstract fun viajesTerminados(implementacion: ViajesTerminadosEnMemoria): ViajesTerminados

    @Binds
    abstract fun contextoVias(implementacion: CalculadorCarreteras): FuenteContextoVias
}
