package app.trombito.rallycopilot.domain.navegacion

import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.EstadoRuta
import app.trombito.rallycopilot.motor.ruta.Ruta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Destino(
    val nombre: String,
    val punto: PuntoGeo,
)

/** Estado de la conducción, publicado por el servicio en primer plano y observado por la UI. */
sealed interface EstadoNavegacion {
    data object Inactiva : EstadoNavegacion

    /**
     * Modo libre (sin destino): sigue la vía y graba el recorrido. Con [reconocimientoTramoId] es una pasada de
     * reconocimiento de ese tramo: lee sus notas, sin cronometrar. [distanciaM] es el total de la conducción (lo que se
     * guarda del recorrido); [tripM] es lo recorrido desde el último [ControlNavegacion.reiniciarTrip] y, al empezar,
     * vale lo mismo que el total.
     */
    data class Libre(
        val desdeMs: Long,
        val distanciaM: Double,
        val reconocimientoTramoId: String? = null,
        val tripM: Double = distanciaM,
    ) : EstadoNavegacion

    /**
     * Guiado hacia [destino] (el final). [paradas] son las intermedias que faltan, en orden (la primera es la
     * próxima); [paradasHechas] cuántas se han alcanzado ya (la próxima es la número `paradasHechas + 1`).
     */
    data class Guiando(
        val destino: Destino,
        val perfil: PerfilRuta,
        val ruta: Ruta,
        val progreso: EstadoRuta?,
        val recalculando: Boolean,
        val paradas: List<Destino> = emptyList(),
        val paradasHechas: Int = 0,
    ) : EstadoNavegacion {
        /** La próxima parada, o null si solo queda el destino. */
        val proximaParada: Destino? get() = paradas.firstOrNull()

        /** Lo que falta (m) hasta la próxima parada; null sin paradas o mientras se recalcula la ruta. */
        val distanciaAProximaM: Double? get() =
            if (recalculando ||
                paradas.isEmpty()
            ) {
                null
            } else {
                progreso?.distanciaAProximaParadaM
            }

        /** Lo que falta (s) hasta la próxima parada; null sin paradas o mientras se recalcula la ruta. */
        val tiempoAProximaS: Double? get() =
            if (recalculando ||
                paradas.isEmpty()
            ) {
                null
            } else {
                progreso?.tiempoAProximaParadaS
            }
    }

    data class Llegada(
        val destino: Destino,
    ) : EstadoNavegacion

    data class Error(
        val motivo: String,
    ) : EstadoNavegacion
}

/** Pasada [numero] (1 o 2) del reconocimiento guiado del tramo [tramoId]. */
data class PasadaReconocimiento(
    val tramoId: String,
    val numero: Int,
) {
    companion object {
        const val SEGUNDA = 2
    }
}

private val SIN_PASADA: StateFlow<PasadaReconocimiento?> = MutableStateFlow(null)

/**
 * Arranca y para la conducción. Empezar lanza el servicio en primer plano de ubicación (solo desde la app visible: lo
 * pide la UI tras una acción del usuario). La ruta inicial ya viene calculada por la vista previa (con sus paradas, en
 * el orden en que se añadieron); los recálculos los hace el servicio al salirse de la ruta o al editar las paradas.
 *
 * Con el mapa abierto siempre hay una sesión: el modo libre es la de reposo y un destino la sustituye (y al revés), sin
 * parar el servicio. Una sesión nueva cierra el recorrido de la anterior con la regla de siempre (< 1 km se descarta).
 */
@Suppress("TooManyFunctions") // sesión libre, reconocimiento (2 pasadas), guiado y paradas
interface ControlNavegacion {
    val estado: StateFlow<EstadoNavegacion>

    /** Empieza el modo libre; sustituye a un guiado en curso y no hace nada si ya está en libre. */
    fun empezarLibre()

    /**
     * Empieza el reconocimiento del tramo [tramoId]: el copiloto lee sus notas (fusionadas con las editadas) y lo que
     * se dicta se anota en él. Sustituye a cualquier otra sesión; no hace nada si ya se reconoce ese tramo.
     */
    fun empezarReconocimiento(tramoId: String)

    /** La pasada del reconocimiento en curso; null si no se está reconociendo ningún tramo. */
    val pasadaReconocimiento: StateFlow<PasadaReconocimiento?> get() = SIN_PASADA

    /**
     * Empieza la 2.ª pasada del reconocimiento de [tramoId]: el copiloto lee las notas ya con las correcciones de la
     * 1.ª. Reinicia la sesión aunque ya se estuviera reconociendo ese tramo y también vale justo tras terminar.
     */
    fun empezarSegundaPasada(tramoId: String) = Unit

    /** Empieza el guiado; sustituye al modo libre y no hace nada si ya se está guiando. */
    fun empezar(
        destino: Destino,
        perfil: PerfilRuta,
        ruta: Ruta,
        paradas: List<Destino> = emptyList(),
    )

    /**
     * Edita las paradas pendientes del guiado en curso y recalcula la ruta desde la posición actual (mientras tanto
     * `recalculando` está activo). Sin guiado no hace nada. Los índices son los de `Guiando.paradas`.
     */
    fun editarParadas(orden: OrdenParadas)

    /** Añade [parada] al final de las pendientes, antes del destino. */
    fun anadirParada(parada: Destino) = editarParadas(OrdenParadas.Anadir(parada))

    fun quitarParada(indice: Int) = editarParadas(OrdenParadas.Quitar(indice))

    /** Cambia el destino final del guiado conservando las paradas pendientes; recalcula como las ediciones. */
    fun cambiarDestino(destino: Destino) = editarParadas(OrdenParadas.CambiarDestino(destino))

    /** Mueve la parada pendiente [desde] a la posición [hasta]. */
    fun moverParada(
        desde: Int,
        hasta: Int,
    ) = editarParadas(OrdenParadas.Mover(desde, hasta))

    fun terminar()

    /**
     * Pone a cero el trip del modo libre (como el de un roadbook): desde aquí cuenta los km del trip, sin tocar el
     * total ni lo que se graba del recorrido. Devuelve false si no hay modo libre en curso.
     */
    fun reiniciarTrip(): Boolean
}
