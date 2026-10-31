package app.trombito.rallycopilot.ui.navegacion

import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.Ruta

/** Lo que la barra «¿A dónde?» muestra bajo el campo de texto. */
sealed interface BusquedaDestino {
    /** Texto vacío o demasiado corto: no hay lista. */
    data object Inactiva : BusquedaDestino

    /** Resultados del índice offline; vacío = sin coincidencias. */
    data class Resultados(
        val items: List<ResultadoBusqueda>,
    ) : BusquedaDestino

    /** El texto es un enlace o unas coordenadas: ese punto es el destino. */
    data class Ubicacion(
        val destino: Destino,
        /** El enlace no traía nombre: [destino] usa las coordenadas como nombre. */
        val sinNombre: Boolean,
    ) : BusquedaDestino

    /** Enlace corto de Google/OSM: hace falta red para resolverlo. */
    data object NecesitaRed : BusquedaDestino
}

/** Resultado del cálculo de una ruta para un perfil, en la vista previa. */
sealed interface OpcionRuta {
    data object Cargando : OpcionRuta

    data class Lista(
        val ruta: Ruta,
    ) : OpcionRuta {
        val tiempoS: Double get() = ruta.tiempoS
        val distanciaM: Double get() = ruta.longitudM
    }

    /** El paquete de datos no incluye (o aún no se ha instalado) el archivo de rutas. */
    data object SinDatos : OpcionRuta

    data class Error(
        val motivo: String,
    ) : OpcionRuta
}

/** Las dos rutas candidatas hacia [destino]; [perfil] es la elegida (por defecto, la más rápida). */
data class VistaPrevia(
    val destino: Destino,
    val perfil: PerfilRuta = PerfilRuta.MAS_RAPIDA,
    val opciones: Map<PerfilRuta, OpcionRuta> = PerfilRuta.entries.associateWith { OpcionRuta.Cargando },
    /** No hay fijación GPS: no se puede calcular hasta que llegue una. */
    val sinPosicion: Boolean = false,
) {
    val rutaElegida: Ruta? get() = (opciones[perfil] as? OpcionRuta.Lista)?.ruta

    val puedeEmpezar: Boolean get() = rutaElegida != null

    /**
     * Guarda el resultado de [de]. Si la ruta elegida no existe (sin datos o con error) y otra sí, se elige esa: el
     * usuario no se queda con «Empezar» desactivado teniendo una ruta buena.
     */
    fun conOpcion(
        de: PerfilRuta,
        opcion: OpcionRuta,
    ): VistaPrevia {
        val nuevas = opciones + (de to opcion)
        val actual = nuevas[perfil]
        val fallida = actual is OpcionRuta.SinDatos || actual is OpcionRuta.Error
        val alternativa = nuevas.entries.firstOrNull { it.value is OpcionRuta.Lista }?.key
        return copy(opciones = nuevas, perfil = if (fallida && alternativa != null) alternativa else perfil)
    }
}

/** Estado inmutable de la navegación sobre el mapa (búsqueda, vista previa y conducción). */
data class NavegacionUiState(
    /** Texto del campo «¿A dónde?». */
    val texto: String = "",
    val busqueda: BusquedaDestino = BusquedaDestino.Inactiva,
    val vistaPrevia: VistaPrevia? = null,
    /** El estado de la conducción, tal cual lo publica el servicio. */
    val control: EstadoNavegacion = EstadoNavegacion.Inactiva,
) {
    /** Sin destino en marcha (parado o en modo libre): se puede buscar destino y ver rutas. */
    val enReposo: Boolean get() = control is EstadoNavegacion.Inactiva || control is EstadoNavegacion.Libre

    /** Guiando hacia un destino: hay una bandera para terminar. En modo libre no hay botón (para al salir del mapa). */
    val conduciendo: Boolean get() = control is EstadoNavegacion.Guiando

    /** La ruta que se dibuja en el mapa: la elegida en la vista previa o la del guiado. */
    val rutaEnMapa: Ruta?
        get() =
            when (control) {
                is EstadoNavegacion.Guiando -> control.ruta
                is EstadoNavegacion.Inactiva, is EstadoNavegacion.Libre -> vistaPrevia?.rutaElegida
                else -> null
            }

    /** El punto de destino que marca el mapa: el de la vista previa o el del guiado. */
    val destinoEnMapa: PuntoGeo?
        get() =
            when (control) {
                is EstadoNavegacion.Guiando -> control.destino.punto
                is EstadoNavegacion.Inactiva, is EstadoNavegacion.Libre -> vistaPrevia?.destino?.punto
                else -> null
            }

    /** Durante la vista previa el mapa encuadra la ruta en vez de seguir al coche. */
    val encuadrar: Boolean get() = enReposo && vistaPrevia != null
}
