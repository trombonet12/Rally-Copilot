package app.trombito.rallycopilot.ui.navegacion

import app.trombito.rallycopilot.domain.busqueda.Buscador
import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.domain.enlaces.ResolvedorEnlaces
import app.trombito.rallycopilot.domain.enlaces.ResultadoResolucion
import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.navegacion.GuiadoPendiente
import app.trombito.rallycopilot.domain.navegacion.OrdenParadas
import app.trombito.rallycopilot.domain.navegacion.RepositorioGuiadoPendiente
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.Ruta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

val PALMA = PuntoGeo(39.5696, 2.6502)
val SOLLER = PuntoGeo(39.7667, 2.7154)

fun rutaDePrueba(
    longitudM: Double = 18_400.0,
    tiempoS: Double = 1_380.0,
) = Ruta(
    puntos = listOf(PALMA, PuntoGeo(39.65, 2.68), SOLLER),
    maniobras = emptyList(),
    longitudM = longitudM,
    tiempoS = tiempoS,
)

/** Responde con [respuesta]; con [espera] queda suspendido hasta completarla. [canceladas] cuenta las cancelaciones. */
class FalsoResolvedor(
    var respuesta: ResultadoResolucion = ResultadoResolucion.Error,
    var espera: CompletableDeferred<Unit>? = null,
) : ResolvedorEnlaces {
    val urls = mutableListOf<String>()
    var canceladas = 0

    override suspend fun resolver(url: String): ResultadoResolucion {
        urls += url
        try {
            espera?.await()
        } catch (e: CancellationException) {
            canceladas++
            throw e
        }
        return respuesta
    }
}

class FalsoBuscador(
    var resultados: List<ResultadoBusqueda> = emptyList(),
) : Buscador {
    val consultas = mutableListOf<Pair<String, PuntoGeo?>>()

    override suspend fun buscar(
        texto: String,
        cerca: PuntoGeo?,
        limite: Int,
    ): List<ResultadoBusqueda> {
        consultas += texto to cerca
        return resultados
    }
}

/** Cada perfil responde con lo que se le ponga; con [esperar] la respuesta queda pendiente hasta completarla. */
class FalsoEnrutador : Enrutador {
    val respuestas = mutableMapOf<PerfilRuta, ResultadoRuta>()
    val esperas = mutableMapOf<PerfilRuta, CompletableDeferred<Unit>>()
    val peticiones = mutableListOf<Triple<PuntoGeo, PuntoGeo, PerfilRuta>>()

    fun esperar(perfil: PerfilRuta) = CompletableDeferred<Unit>().also { esperas[perfil] = it }

    override suspend fun ruta(
        origen: PuntoGeo,
        destino: PuntoGeo,
        perfil: PerfilRuta,
    ): ResultadoRuta {
        peticiones += Triple(origen, destino, perfil)
        esperas[perfil]?.await()
        return respuestas[perfil] ?: ResultadoRuta.SinDatos
    }

    /** Peticiones con paradas: origen, paradas, destino y perfil. */
    val peticionesConParadas = mutableListOf<List<PuntoGeo>>()

    override suspend fun rutaConParadas(
        origen: PuntoGeo,
        paradas: List<PuntoGeo>,
        destino: PuntoGeo,
        perfil: PerfilRuta,
    ): ResultadoRuta {
        if (paradas.isNotEmpty()) peticionesConParadas += paradas
        return ruta(origen, destino, perfil)
    }
}

class FalsoControlNavegacion : ControlNavegacion {
    private val mutable = MutableStateFlow<EstadoNavegacion>(EstadoNavegacion.Inactiva)
    override val estado: StateFlow<EstadoNavegacion> = mutable

    val empezadas = mutableListOf<Triple<Destino, PerfilRuta, Ruta>>()
    val paradasAlEmpezar = mutableListOf<List<Destino>>()
    val ordenes = mutableListOf<OrdenParadas>()
    var libres = 0
    var terminadas = 0

    fun publicar(nuevo: EstadoNavegacion) {
        mutable.value = nuevo
    }

    override fun empezarLibre() {
        libres++
        mutable.value = EstadoNavegacion.Libre(0L, 0.0)
    }

    val reconocidos = mutableListOf<String>()

    override fun empezarReconocimiento(tramoId: String) {
        reconocidos += tramoId
        mutable.value = EstadoNavegacion.Libre(0L, 0.0, tramoId)
    }

    override fun empezar(
        destino: Destino,
        perfil: PerfilRuta,
        ruta: Ruta,
        paradas: List<Destino>,
    ) {
        empezadas += Triple(destino, perfil, ruta)
        paradasAlEmpezar += paradas
        mutable.value =
            EstadoNavegacion.Guiando(destino, perfil, ruta, progreso = null, recalculando = false, paradas = paradas)
    }

    override fun editarParadas(orden: OrdenParadas) {
        ordenes += orden
    }

    override fun terminar() {
        terminadas++
        mutable.value = EstadoNavegacion.Inactiva
    }
}

/** Guiado pendiente en memoria; [borrados] cuenta las veces que se ha borrado. */
class FalsoGuiadoPendiente(
    inicial: GuiadoPendiente? = null,
) : RepositorioGuiadoPendiente {
    val mutable = MutableStateFlow(inicial)
    override val pendiente: Flow<GuiadoPendiente?> = mutable
    var borrados = 0

    override suspend fun guardar(
        destino: Destino,
        perfil: PerfilRuta,
        paradas: List<Destino>,
    ) {
        mutable.value = GuiadoPendiente(destino, perfil, 0L, paradas)
    }

    override suspend fun borrar() {
        borrados++
        mutable.value = null
    }
}
