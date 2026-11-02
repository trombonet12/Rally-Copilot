package app.trombito.rallycopilot.domain.datos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.precarga.Rectangulo
import kotlinx.coroutines.flow.Flow

/** Progreso de una descarga de detalle: [hechas] de [totales] teselas; [esperando]: a la espera de red o de batería. */
data class ProgresoDetalle(
    val hechas: Int,
    val totales: Int,
    val esperando: Boolean,
)

/** Lo bajado a z18–19: [bytes] del archivo `satelite-detalle.mbtiles`, [teselas] guardadas y la descarga en curso. */
data class EstadoDetalle(
    val bytes: Long = 0,
    val teselas: Long = 0,
    val descarga: ProgresoDetalle? = null,
)

/**
 * Satélite de detalle (z18–19) del IGN en un MBTiles escribible: corredor z18 automático (solo con WiFi, cargando o
 * con batería > 30 %) de los tramos guardados y de la ruta que se va a guiar, y zonas manuales z18–19.
 */
interface PrecargaDetalle {
    val estado: Flow<EstadoDetalle>

    /** Encola la descarga automática del corredor de todos los tramos guardados y de las rutas pendientes. */
    fun precargarTramos()

    /** Guarda el corredor de [puntos] (ruta planificada) para la descarga automática y la encola. */
    fun precargarRuta(puntos: List<PuntoGeo>)

    /** Encola la descarga manual de [zona] a [zoom] (18 o 19). */
    fun descargarZona(
        zona: Rectangulo,
        zoom: Int,
    )

    fun cancelar()

    /** Borra el MBTiles de detalle y las rutas pendientes. */
    suspend fun borrar()

    /** No hace nada (tests y entornos sin descargas). */
    object Ninguna : PrecargaDetalle {
        override val estado: Flow<EstadoDetalle> = kotlinx.coroutines.flow.flowOf(EstadoDetalle())

        override fun precargarTramos() = Unit

        override fun precargarRuta(puntos: List<PuntoGeo>) = Unit

        override fun descargarZona(
            zona: Rectangulo,
            zoom: Int,
        ) = Unit

        override fun cancelar() = Unit

        override suspend fun borrar() = Unit
    }
}
