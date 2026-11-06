package app.trombito.rallycopilot.ui.navegacion

import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EdicionParadas
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.Ruta
import java.util.Locale

/** Lo que la barra «¿A dónde?» muestra bajo el campo de texto. */
sealed interface BusquedaDestino {
    /** Texto vacío o demasiado corto: no hay lista. */
    data object Inactiva : BusquedaDestino

    /** Resultados del índice offline; vacío = sin coincidencias. */
    data class Resultados(
        val items: List<ResultadoBusqueda>,
        /** Distancia en línea recta (m) de cada resultado a la posición del usuario; vacía si no hay fijación. */
        val distanciasM: List<Double> = emptyList(),
    ) : BusquedaDestino

    /** El texto es un enlace o unas coordenadas: ese punto es el destino. */
    data class Ubicacion(
        val destino: Destino,
        /** El enlace no traía nombre: [destino] usa las coordenadas como nombre. */
        val sinNombre: Boolean,
    ) : BusquedaDestino

    /** Enlace corto de Google Maps: se está siguiendo su redirección por red. */
    data object Resolviendo : BusquedaDestino

    /** Enlace corto sin conexión a internet. */
    data object SinRed : BusquedaDestino

    /** El enlace corto no se pudo abrir (error, destino no permitido o sin coordenadas). */
    data object EnlaceNoAbierto : BusquedaDestino
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

/**
 * Punto elegido manteniendo pulsado el mapa. [nombre] es el del lugar o la calle bajo el dedo (lo lee el mapa de las
 * teselas, sin red); null si no hay nada con nombre, y entonces [destino] usa las coordenadas cortas.
 */
data class PuntoElegido(
    val punto: PuntoGeo,
    val nombre: String? = null,
) {
    /** El destino que se calcula al pulsar «Ir aquí» o «Añadir parada». */
    val destino: Destino get() = Destino(nombre ?: coordenadasCortas(punto), punto)
}

/** «39,5696, 2,6502»: latitud y longitud con 4 decimales (≈ 10 m), para rotular un punto sin nombre. */
fun coordenadasCortas(punto: PuntoGeo): String = String.format(Locale.ROOT, "%.4f, %.4f", punto.lat, punto.lon)

/** Un marcador numerado de parada en el mapa. */
data class MarcadorParada(
    val punto: PuntoGeo,
    val numero: Int,
)

/**
 * Las dos rutas candidatas hacia [destino] pasando por las [paradas] intermedias, en orden; [perfil] es la elegida
 * (por defecto, la más rápida).
 */
data class VistaPrevia(
    val destino: Destino,
    val perfil: PerfilRuta = PerfilRuta.MAS_RAPIDA,
    val opciones: Map<PerfilRuta, OpcionRuta> = PerfilRuta.entries.associateWith { OpcionRuta.Cargando },
    /** No hay fijación GPS: no se puede calcular hasta que llegue una. */
    val sinPosicion: Boolean = false,
    /** Paradas intermedias, en el orden del viaje. */
    val paradas: List<Destino> = emptyList(),
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

/** En qué punto está «Seguir a <destino>». */
enum class FaseSeguir {
    /** Esperando el toque. */
    LISTA,

    /** Calculando la ruta desde la posición actual. */
    CALCULANDO,

    /** Se tocó sin fijación GPS: empieza en cuanto llegue una. */
    SIN_POSICION,

    /** No se pudo calcular la ruta (sin datos o error): se puede reintentar. */
    ERROR,
}

/** Oferta «Seguir a <destino>»: el guiado que quedó a medias al morir el proceso. */
data class OfertaSeguir(
    val destino: Destino,
    val fase: FaseSeguir = FaseSeguir.LISTA,
)

/** Lo que se ha tardado y recorrido en un trayecto guiado, para la tarjeta de llegada. */
data class ResumenTrayecto(
    val tiempoS: Double,
    val distanciaM: Double,
)

/** Estado inmutable de la navegación sobre el mapa (búsqueda, vista previa y conducción). */
data class NavegacionUiState(
    /** Texto del campo «¿A dónde?». */
    val texto: String = "",
    val busqueda: BusquedaDestino = BusquedaDestino.Inactiva,
    val vistaPrevia: VistaPrevia? = null,
    /** El estado de la conducción, tal cual lo publica el servicio. */
    val control: EstadoNavegacion = EstadoNavegacion.Inactiva,
    /** Guiando, la lupa del panel abrió la búsqueda encima del mapa (con [texto] y [busqueda]). */
    val buscando: Boolean = false,
    /** «Seguir a <destino>»: solo en reposo, sin búsqueda ni vista previa abiertas. */
    val seguir: OfertaSeguir? = null,
    /** El punto elegido manteniendo pulsado el mapa (tarjeta «Ir aquí» / «Añadir parada»). */
    val puntoElegido: PuntoElegido? = null,
    /** Tiempo y kilómetros del trayecto recién acabado (solo con el aviso de llegada). */
    val resumenLlegada: ResumenTrayecto? = null,
    /** La casa fijada en los ajustes (null sin fijar): «Ir a casa» bajo la barra «¿A dónde?». */
    val casa: PuntoGeo? = null,
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

    /** Paradas intermedias que se ven: las que faltan del guiado o las de la vista previa, en orden. */
    val paradas: List<Destino>
        get() =
            when (control) {
                is EstadoNavegacion.Guiando -> control.paradas
                is EstadoNavegacion.Inactiva, is EstadoNavegacion.Libre -> vistaPrevia?.paradas.orEmpty()
                else -> emptyList()
            }

    /** Hay una ruta (en vista previa o en marcha) a la que añadir una parada. */
    val puedeAnadirParada: Boolean get() = conduciendo || (enReposo && vistaPrevia != null)

    /** No caben más paradas. */
    val paradasLlenas: Boolean get() = paradas.size >= EdicionParadas.MAX_PARADAS

    /** Marcadores numerados de las paradas: al guiar, la numeración sigue contando las ya alcanzadas. */
    val marcadoresParada: List<MarcadorParada>
        get() {
            val hechas = (control as? EstadoNavegacion.Guiando)?.paradasHechas ?: 0
            return paradas.mapIndexed { i, d -> MarcadorParada(d.punto, hechas + i + 1) }
        }

    /** Planificando: búsqueda abierta, tarjeta de un punto o vista previa de ruta, sin guiar. */
    val planificando: Boolean
        get() =
            enReposo &&
                (
                    texto.isNotEmpty() || busqueda !is BusquedaDestino.Inactiva || vistaPrevia != null ||
                        puntoElegido != null
                )

    /** Durante la vista previa el mapa encuadra la ruta en vez de seguir al coche. */
    val encuadrar: Boolean get() = enReposo && vistaPrevia != null
}
