package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.velocidad.Fijacion

/** Centro y zoom de la cámara del mapa. */
data class CamaraMapa(
    val latitud: Double,
    val longitud: Double,
    val zoom: Double,
)

/** Estado inmutable de la pantalla de mapa. */
data class MapaUiState(
    /** Mientras no se ha resuelto si hay datos instalados. */
    val cargando: Boolean = true,
    val datos: EstadoDatosMapa = EstadoDatosMapa(),
    val estilos: EstilosMapa? = null,
    val capaElegida: CapaMapa = CapaMapa.VECTORIAL,
    val posicion: Fijacion? = null,
    /** Null cuando no se conoce (sin permiso, sin fijación o velocidad caducada). */
    val velocidadKmh: Int? = null,
    val via: ViaActual? = null,
    /** La cámara sigue al coche (se pierde al mover el mapa; sobrevive al giro de pantalla). */
    val siguiendo: Boolean = true,
    /** Interruptor de relieve y curvas de nivel (persistido en los ajustes de conducción). */
    val relieve: Boolean = false,
    val orientacion: Orientacion = Orientacion.RUMBO,
    val icono: IconoPosicion = IconoPosicion.FLECHA_WRC,
    /** Zoom automático (por velocidad y ajuste de Joan, suavizado); null mientras no hay velocidad conocida. */
    val zoomObjetivo: Double? = null,
) {
    val hayDatos: Boolean get() = estilos != null

    /** Hay datos instalados pero su estilo no se pudo cargar (rechazado o ilegible). */
    val datosDanados: Boolean get() = !cargando && estilos == null && datos.instalados != null

    val puedeSatelite: Boolean get() = estilos?.satelite != null

    /** La capa que se dibuja: el satélite solo si está instalado. */
    val capa: CapaMapa
        get() = if (capaElegida == CapaMapa.SATELITE && puedeSatelite) CapaMapa.SATELITE else CapaMapa.VECTORIAL

    val estiloJson: String?
        get() = if (capa == CapaMapa.SATELITE) estilos?.satelite else estilos?.vectorial

    /** Sin `maxspeed` en OSM no hay límite que mostrar. */
    val limiteKmh: Int? get() = via?.limiteKmh
}
