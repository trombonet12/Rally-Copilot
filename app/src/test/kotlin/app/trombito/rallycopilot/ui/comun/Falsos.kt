package app.trombito.rallycopilot.ui.comun

import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.vias.ProveedorViaActual
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FalsoRepositorioDatosMapa(
    estadoInicial: EstadoDatosMapa = EstadoDatosMapa(),
    var estilosDisponibles: EstilosMapa? = null,
) : RepositorioDatosMapa {
    val estadoMutable = MutableStateFlow(estadoInicial)
    val ajustesMutable = MutableStateFlow(AjustesMapas())
    override val estado: Flow<EstadoDatosMapa> = estadoMutable
    override val ajustes: Flow<AjustesMapas> = ajustesMutable

    var descargas = 0
    var cancelaciones = 0
    var borrados = 0
    var avisosDescartados = 0
    var comprobaciones = 0

    override suspend fun guardarAjustes(ajustes: AjustesMapas) {
        ajustesMutable.value = ajustes
    }

    override fun comprobarActualizacion() {
        comprobaciones++
    }

    override fun descargar() {
        descargas++
    }

    override fun cancelar() {
        cancelaciones++
    }

    override suspend fun borrar() {
        borrados++
    }

    override suspend fun descartarAviso() {
        avisosDescartados++
    }

    override suspend fun estilos(): EstilosMapa? = estilosDisponibles
}

class FalsaFuenteUbicacion(
    var permiso: Boolean = true,
) : FuenteUbicacion {
    val flujo = MutableSharedFlow<Fijacion>(extraBufferCapacity = 16)

    override fun fijaciones(): Flow<Fijacion> = flujo

    override fun tienePermiso(): Boolean = permiso
}

class FalsoProveedorVia(
    var via: ViaActual? = null,
) : ProveedorViaActual {
    override suspend fun viaEn(fijacion: Fijacion): ViaActual? = via
}

fun fijacion(
    tiempoMs: Long,
    velocidadMs: Double? = null,
    lat: Double = 39.70,
    lon: Double = 2.90,
) = Fijacion(PuntoGeo(lat, lon), tiempoMs, velocidadMs, rumboGrados = 90.0, precisionM = 5.0)
