package app.trombito.rallycopilot.domain.tramos

import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.tramos.Tramo
import kotlinx.coroutines.flow.Flow

/** Un tramo listo para cronometrar y su mejor pasada hasta ahora (null si no tiene ninguna). */
data class TramoCronometrable(
    val tramo: Tramo,
    val mejor: Pasada?,
)

/**
 * De dónde sale una pasada. [inicioMs] es el instante epoch de su salida. Si la grabó un recorrido, [recorridoId] lo
 * identifica y [puntosDesdeMs]..[puntosHastaMs] es el intervalo que ocupa en el reloj de los puntos de ese recorrido
 * (no es epoch): con él se recortan sus puntos.
 */
data class OrigenPasada(
    val inicioMs: Long,
    val recorridoId: Long? = null,
    val puntosDesdeMs: Long? = null,
    val puntosHastaMs: Long? = null,
)

/** Qué tramos muestra la lista: todos, los hechos al menos dos veces o los marcados como favoritos. */
enum class FiltroTramos { TODOS, REPETIDOS, FAVORITOS }

/**
 * Fila de «Mis tramos». [nombre] es el efectivo (el del usuario o, si no lo hay, el automático) y [nombrePropio] el
 * que puso el usuario (null si no lo ha cambiado). [subtitulo] son las carreteras del nombre automático
 * ([NombreAutomatico]), si las hay. [ultimoUsoMs] es epoch.
 */
data class ResumenTramo(
    val id: String,
    val nombre: String,
    val nombrePropio: String?,
    val longitudM: Double,
    val pasadas: Int,
    val mejorTotalMs: Long?,
    val ultimoUsoMs: Long,
    val favorito: Boolean,
    val subtitulo: String? = null,
)

/** Punto grabado de una pasada; [tiempoMs] está en el reloj del recorrido (monótono, no epoch). */
data class PuntoPasada(
    val lat: Double,
    val lon: Double,
    val tiempoMs: Long,
    val velocidadMs: Double?,
)

/**
 * Una pasada de un tramo con lo que necesita el análisis: su curva distancia-tiempo, su salida (epoch) y los
 * [puntos] grabados recortados a la pasada (vacíos si el recorrido ya no existe). [esMejor]: la de menor tiempo.
 */
data class PasadaDetallada(
    val id: Long,
    val inicioMs: Long,
    val pasada: Pasada,
    val puntos: List<PuntoPasada>,
    val esMejor: Boolean,
) {
    val totalMs: Long get() = pasada.totalMs
}

/** Lo que cuenta el perfil: recorridos terminados (distancia y fechas epoch) y tramos con sus pasadas. */
data class EstadisticasPerfil(
    val recorridos: Int,
    val distanciaTotalM: Double,
    val primerRecorridoMs: Long?,
    val ultimoRecorridoMs: Long?,
    val tramos: Int,
    val pasadas: Int,
)

/**
 * Tramos cronometrables y sus pasadas. El [Tramo.id] es estable y lo da quien lo crea; las pasadas se identifican por
 * un número que da el almacén. La mejor pasada de un tramo es la de menor tiempo total (a igualdad, la primera).
 */
@Suppress("TooManyFunctions") // el contrato del almacén de tramos: crono, lista, edición y análisis
interface RepositorioTramos {
    /**
     * Lista de «Mis tramos» según el [filtro], por último uso (el más reciente primero). Con REPETIDOS solo entran
     * los de al menos dos pasadas.
     */
    fun observarResumenes(filtro: FiltroTramos = FiltroTramos.TODOS): Flow<List<ResumenTramo>>

    /** Totales para el perfil, cada vez que cambian los recorridos, los tramos o las pasadas. */
    fun observarPerfil(): Flow<EstadisticasPerfil>

    /** La mejor pasada del tramo, cada vez que cambia; null si no hay tramo o pasadas. */
    fun observarMejorPasada(tramoId: String): Flow<Pasada?>

    /** Todos los tramos con su mejor pasada completa, para cronometrar (lectura única). */
    suspend fun cronometrables(): List<TramoCronometrable>

    suspend fun mejorPasada(tramoId: String): Pasada?

    /** El tramo guardado, o null si no existe o está dañado. */
    suspend fun tramo(tramoId: String): Tramo?

    /** Todas las pasadas del tramo con sus puntos recortados, la más reciente primero (vacío si no existe). */
    suspend fun pasadasDetalladas(tramoId: String): List<PasadaDetallada>

    /** La mejor pasada del tramo con sus puntos recortados, o null si no hay. */
    suspend fun mejorPasadaDetallada(tramoId: String): PasadaDetallada?

    /**
     * Guarda un tramo nuevo con su primera pasada, en una sola transacción. [nombre] es el del usuario (null: se usa
     * [nombreAutomatico]); el último uso del tramo es la salida de la pasada.
     */
    @Suppress("LongParameterList") // los datos del tramo nuevo: tramo, dos nombres, fecha y primera pasada
    suspend fun crear(
        tramo: Tramo,
        nombre: String?,
        nombreAutomatico: String?,
        creadoEnMs: Long,
        primera: Pasada,
        origen: OrigenPasada,
    )

    /**
     * Guarda un tramo sin pasadas (importado de un GPX/KML o dibujado en el mapa). Su último uso es [creadoEnMs]; las
     * notas se generan al conducirlo o reconocerlo.
     */
    suspend fun crearSinPasadas(
        tramo: Tramo,
        nombre: String?,
        nombreAutomatico: String?,
        creadoEnMs: Long,
    )

    /** Guarda una pasada de un tramo existente, adelanta su último uso y devuelve su número; falla si no existe. */
    suspend fun guardarPasada(
        pasada: Pasada,
        origen: OrigenPasada,
    ): Long

    /** Cambia el nombre del usuario; vacío o null vuelve al automático. Falla si el tramo no existe. */
    suspend fun renombrar(
        tramoId: String,
        nombre: String?,
    )

    /** Marca o desmarca como favorito. Falla si el tramo no existe. */
    suspend fun marcarFavorito(
        tramoId: String,
        favorito: Boolean,
    )

    /**
     * Guarda los cortes de [sectores] y [microsectores] (metros desde la salida). Falla si el tramo no existe o los
     * cortes no son válidos para él; las pasadas ya hechas se reparten con los cortes nuevos al analizarlas.
     */
    suspend fun guardarCortes(
        tramoId: String,
        sectores: List<Double>,
        microsectores: List<Double>,
    )

    /** Borra el tramo y, en cascada, sus pasadas. */
    suspend fun borrar(tramoId: String)
}
