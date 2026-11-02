package app.trombito.rallycopilot.di

import app.trombito.rallycopilot.data.notas.EdicionNotasEnVivoImpl
import app.trombito.rallycopilot.data.notas.EditorTramoImpl
import app.trombito.rallycopilot.data.notas.FuenteContextoNotas
import app.trombito.rallycopilot.data.notas.GeneradorHojaTramo
import app.trombito.rallycopilot.data.notas.GeneradorHojaTramoImpl
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.domain.edicion.EdicionNotasEnVivo
import app.trombito.rallycopilot.domain.edicion.EditorTramo
import app.trombito.rallycopilot.domain.escucha.RepositorioEdiciones
import app.trombito.rallycopilot.domain.escucha.RepositorioMarcas
import app.trombito.rallycopilot.domain.notas.ProveedorNotas
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Edición de notas y sectores: del tramo guardado (detalle) y de la conducción en curso (mapa, parado). */
@Module
@InstallIn(SingletonComponent::class)
object EdicionModule {
    @Provides
    fun generadorHojaTramo(
        contexto: FuenteContextoNotas,
        recorridos: RepositorioRecorridos,
    ): GeneradorHojaTramo = GeneradorHojaTramoImpl(contexto, recorridos)

    @Provides
    fun editorTramo(
        tramos: RepositorioTramos,
        ediciones: RepositorioEdiciones,
        marcas: RepositorioMarcas,
        generador: GeneradorHojaTramo,
    ): EditorTramo = EditorTramoImpl(tramos, ediciones, marcas, generador, System::currentTimeMillis)

    @Provides
    fun edicionNotasEnVivo(
        notas: ProveedorNotas,
        crono: ProveedorCrono,
        tramos: RepositorioTramos,
        ediciones: RepositorioEdiciones,
    ): EdicionNotasEnVivo = EdicionNotasEnVivoImpl(notas, crono, tramos, ediciones, System::currentTimeMillis)
}
