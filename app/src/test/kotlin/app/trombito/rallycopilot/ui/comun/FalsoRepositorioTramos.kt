package app.trombito.rallycopilot.ui.comun

import app.trombito.rallycopilot.domain.tramos.EstadisticasPerfil
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.OrigenPasada
import app.trombito.rallycopilot.domain.tramos.PasadaDetallada
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.domain.tramos.TramoCronometrable
import app.trombito.rallycopilot.domain.tramos.TramoGuardado
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.MarcaTiempo
import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.tramos.Tramo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Resumen de prueba con valores razonables. */
@Suppress("LongParameterList") // un parámetro con valor por defecto por cada campo del resumen
fun resumenFalso(
    id: String,
    nombre: String = "Tramo $id",
    pasadas: Int = 1,
    mejorMs: Long? = 90_000L,
    ultimoUsoMs: Long = 1_000L,
    favorito: Boolean = false,
    longitudM: Double = 5_000.0,
    nombrePropio: String? = null,
) = ResumenTramo(id, nombre, nombrePropio, longitudM, pasadas, mejorMs, ultimoUsoMs, favorito)

/** Pasada de prueba de [totalMs] sobre un tramo de [longitudM]. */
fun pasadaFalsa(
    tramoId: String,
    totalMs: Long,
    longitudM: Double = 5_000.0,
) = Pasada(tramoId, listOf(MarcaTiempo(0.0, 0L), MarcaTiempo(longitudM, totalMs)), totalMs)

/** Tramo de prueba: una línea corta cerca de Escorca. */
fun tramoFalso(
    id: String,
    longitudM: Double = 5_000.0,
) = Tramo(id, listOf(PuntoGeo(39.80, 2.80), PuntoGeo(39.81, 2.82), PuntoGeo(39.83, 2.81)), longitudM)

/** Pasada detallada de prueba; [inicioMs] es epoch. */
fun detalladaFalsa(
    id: Long,
    tramoId: String,
    totalMs: Long,
    inicioMs: Long,
    esMejor: Boolean = false,
) = PasadaDetallada(id, inicioMs, pasadaFalsa(tramoId, totalMs), emptyList(), esMejor)

/** Repositorio de tramos en memoria que registra lo que se le pide. */
class FalsoRepositorioTramos(
    resumenes: List<ResumenTramo> = emptyList(),
    pasadas: Map<String, List<PasadaDetallada>> = emptyMap(),
    trazas: Map<String, Tramo> = emptyMap(),
    perfil: EstadisticasPerfil = EstadisticasPerfil(0, 0.0, null, null, 0, 0),
) : RepositorioTramos {
    val resumenesMutable = MutableStateFlow(resumenes)
    val perfilMutable = MutableStateFlow(perfil)
    val pasadasPorTramo = pasadas.toMutableMap()
    val tramos = trazas.toMutableMap()

    val borrados = mutableListOf<String>()
    val favoritos = mutableListOf<Pair<String, Boolean>>()
    val renombrados = mutableListOf<Pair<String, String?>>()

    /** Si no es null, `borrar` lo lanza (una base que falla). */
    var falloAlBorrar: Throwable? = null

    override fun observar(): Flow<List<TramoGuardado>> = MutableStateFlow(emptyList())

    override fun observarResumenes(filtro: FiltroTramos): Flow<List<ResumenTramo>> =
        resumenesMutable.map { lista ->
            lista.filter {
                when (filtro) {
                    FiltroTramos.TODOS -> true
                    FiltroTramos.REPETIDOS -> it.pasadas >= 2
                    FiltroTramos.FAVORITOS -> it.favorito
                }
            }
        }

    override fun observarPerfil(): Flow<EstadisticasPerfil> = perfilMutable

    override fun observarMejorPasada(tramoId: String): Flow<Pasada?> = MutableStateFlow(null)

    override suspend fun cronometrables(): List<TramoCronometrable> = emptyList()

    override suspend fun mejorPasada(tramoId: String): Pasada? = null

    override suspend fun tramo(tramoId: String): Tramo? = tramos[tramoId]

    override suspend fun pasadasDetalladas(tramoId: String): List<PasadaDetallada> = pasadasPorTramo[tramoId].orEmpty()

    override suspend fun mejorPasadaDetallada(tramoId: String): PasadaDetallada? =
        pasadasPorTramo[tramoId]?.firstOrNull { it.esMejor }

    override suspend fun crear(
        tramo: Tramo,
        nombre: String?,
        nombreAutomatico: String?,
        creadoEnMs: Long,
        primera: Pasada,
        origen: OrigenPasada,
    ) = Unit

    override suspend fun guardarPasada(
        pasada: Pasada,
        origen: OrigenPasada,
    ): Long = 0L

    override suspend fun renombrar(
        tramoId: String,
        nombre: String?,
    ) {
        renombrados += tramoId to nombre
        resumenesMutable.value =
            resumenesMutable.value.map {
                if (it.id == tramoId) it.copy(nombrePropio = nombre, nombre = nombre ?: "Automático ${it.id}") else it
            }
    }

    override suspend fun marcarFavorito(
        tramoId: String,
        favorito: Boolean,
    ) {
        favoritos += tramoId to favorito
        resumenesMutable.value =
            resumenesMutable.value.map { if (it.id == tramoId) it.copy(favorito = favorito) else it }
    }

    val cortesGuardados = mutableListOf<Triple<String, List<Double>, List<Double>>>()

    override suspend fun guardarCortes(
        tramoId: String,
        sectores: List<Double>,
        microsectores: List<Double>,
    ) {
        cortesGuardados += Triple(tramoId, sectores, microsectores)
        tramos[tramoId]?.let { tramos[tramoId] = it.copy(sectores = sectores, microsectores = microsectores) }
    }

    override suspend fun borrar(tramoId: String) {
        falloAlBorrar?.let { throw it }
        borrados += tramoId
        resumenesMutable.value = resumenesMutable.value.filterNot { it.id == tramoId }
    }
}
