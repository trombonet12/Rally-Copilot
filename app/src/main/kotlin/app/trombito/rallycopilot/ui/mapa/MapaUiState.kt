package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.ajustes.EstiloManiobras
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
    /** Fijación cruda del GPS (la que usan la navegación y las notas). */
    val posicion: Fijacion? = null,
    /** [posicion] ajustada al camino (ruta o vía): punto proyectado y rumbo del camino. Null si no hay fijación. */
    val posicionAjustada: Fijacion? = null,
    /** Null cuando no se conoce (sin permiso, sin fijación o velocidad caducada). */
    val velocidadKmh: Int? = null,
    val via: ViaActual? = null,
    /** La cámara sigue al coche (se pierde al mover el mapa; sobrevive al giro de pantalla). */
    val siguiendo: Boolean = true,
    /** Interruptor de relieve y curvas de nivel (persistido en los ajustes de conducción). */
    val relieve: Boolean = false,
    val orientacion: Orientacion = Orientacion.RUMBO,
    val icono: IconoPosicion = IconoPosicion.FLECHA_WRC,
    /** Estilo guardado de los iconos de maniobra: desde H14c el banner usa siempre el ámbar y lo ignora. */
    val estiloManiobras: EstiloManiobras = EstiloManiobras.PLACA,
    /** Zoom automático (por velocidad y ajuste de Joan, suavizado); null mientras no hay velocidad conocida. */
    val zoomObjetivo: Double? = null,
    /** El tema de la interfaz es oscuro (lo pone la ruta desde el tema aplicado). */
    val temaOscuro: Boolean = true,
) {
    val hayDatos: Boolean get() = estilos != null

    /** Lo que pinta el mapa: la posición ajustada al camino o, sin ajuste, la cruda. */
    val posicionMostrada: Fijacion? get() = posicionAjustada ?: posicion

    /** Hay datos instalados pero su estilo no se pudo cargar (rechazado o ilegible). */
    val datosDanados: Boolean get() = !cargando && estilos == null && datos.instalados != null

    val puedeSatelite: Boolean get() = estilos?.satelite != null

    /** La capa que se dibuja: el satélite solo si está instalado. */
    val capa: CapaMapa
        get() = if (capaElegida == CapaMapa.SATELITE && puedeSatelite) CapaMapa.SATELITE else CapaMapa.VECTORIAL

    /**
     * El estilo que se dibuja: el satélite, el vectorial claro si el tema es claro y los datos lo traen, o el vectorial
     * (oscuro). Sin estilo claro en los datos el mapa se queda oscuro también con el tema claro.
     */
    val estiloJson: String?
        get() =
            when {
                capa == CapaMapa.SATELITE -> estilos?.satelite
                !temaOscuro -> estilos?.vectorialClaro ?: estilos?.vectorial
                else -> estilos?.vectorial
            }

    /** Sin `maxspeed` en OSM no hay límite que mostrar. */
    val limiteKmh: Int? get() = via?.limiteKmh
}
