package app.trombito.rallycopilot.di

import app.trombito.rallycopilot.data.ubicacion.PosicionEnVivo
import app.trombito.rallycopilot.data.vias.CargadorVias
import app.trombito.rallycopilot.data.vias.FuenteVias
import app.trombito.rallycopilot.data.vias.ProveedorViaActualOsm
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.vias.ProveedorViaActual
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class UbicacionModule {
    @Binds
    abstract fun fuenteUbicacion(implementacion: PosicionEnVivo): FuenteUbicacion

    @Binds
    abstract fun proveedorViaActual(implementacion: ProveedorViaActualOsm): ProveedorViaActual

    @Binds
    abstract fun fuenteVias(implementacion: CargadorVias): FuenteVias
}
