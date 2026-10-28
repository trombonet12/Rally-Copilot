package app.trombito.rallycopilot.di

import app.trombito.rallycopilot.data.ubicacion.FuenteUbicacionGps
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
    abstract fun fuenteUbicacion(implementacion: FuenteUbicacionGps): FuenteUbicacion

    @Binds
    abstract fun proveedorViaActual(implementacion: ProveedorViaActualOsm): ProveedorViaActual
}
