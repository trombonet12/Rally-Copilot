package app.trombito.rallycopilot.data.detalle

import app.trombito.rallycopilot.domain.datos.EstadoDetalle
import app.trombito.rallycopilot.domain.datos.PrecargaDetalle
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.precarga.Rectangulo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** [PrecargaDetalle] que solo registra lo que se le pide. */
class FalsaPrecarga : PrecargaDetalle {
    val estadoMutable = MutableStateFlow(EstadoDetalle())
    override val estado: Flow<EstadoDetalle> = estadoMutable

    var tramos = 0
    val rutas = mutableListOf<List<PuntoGeo>>()
    val zonas = mutableListOf<Pair<Rectangulo, Int>>()
    var borrado = false
    var cancelado = false

    override fun precargarTramos() {
        tramos++
    }

    override fun precargarRuta(puntos: List<PuntoGeo>) {
        rutas += puntos
    }

    override fun descargarZona(
        zona: Rectangulo,
        zoom: Int,
    ) {
        zonas += zona to zoom
    }

    override fun cancelar() {
        cancelado = true
    }

    override suspend fun borrar() {
        borrado = true
    }
}
