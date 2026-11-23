package app.trombito.rallycopilot.motor.analisis

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor

/**
 * Niveles de piloto, de menor a mayor. [puntosMinimos] es el umbral para alcanzar el nivel (ver [Temporada]).
 * Los puntos salen solo de kilómetros, curvas, carreteras distintas y constancia: nunca de velocidad ni de tiempos.
 */
enum class NivelPiloto(
    val nombre: String,
    val puntosMinimos: Int,
) {
    DEBUTANTE("Debutante", 0),
    AFICIONADO("Aficionado", 100),
    PILOTO_DE_CLUB("Piloto de club", 400),
    PILOTO_NACIONAL("Piloto nacional", 1_200),
    PILOTO_OFICIAL("Piloto oficial", 3_500),
    LEYENDA("Leyenda", 10_000),
}

/**
 * Nivel actual con sus [puntos], el [siguiente] nivel (null en el último), su umbral y el [progreso] 0..1 hacia él
 * (1.0 en el último nivel).
 */
data class NivelProgreso(
    val nivel: NivelPiloto,
    val puntos: Int,
    val siguiente: NivelPiloto?,
    val umbralSiguiente: Int?,
    val progreso: Double,
)

/** Familias de hitos del catálogo. */
enum class CategoriaHito { KM, CURVAS, HORQUILLAS, CARRETERAS, RACHA }

/**
 * Hito del catálogo: [umbral] de su [categoria] (km, curvas, horquillas, carreteras o semanas), [valor] actual,
 * [progreso] 0..1 y [conseguidoMs] (epoch ms del viaje que lo logró) si ya está conseguido.
 */
data class Hito(
    val id: String,
    val categoria: CategoriaHito,
    val umbral: Int,
    val titulo: String,
    val valor: Double,
    val progreso: Double,
    val conseguidoMs: Long?,
) {
    val conseguido: Boolean get() = conseguidoMs != null
}

/** Serie acumulada monótona: en cada instante (epoch ms) el valor acumulado hasta ese momento. */
internal class SerieAcumulada(
    private val puntos: List<Pair<Long, Double>>,
) {
    val total: Double get() = puntos.lastOrNull()?.second ?: 0.0

    /** Fecha del primer punto con valor ≥ [umbral], o null si no se alcanzó. */
    fun fechaDe(umbral: Int): Long? = puntos.firstOrNull { it.second >= umbral }?.first
}

/** Catálogo determinista de hitos, niveles y fórmula de puntos. Interno a [Temporada]. */
internal object Progreso {
    const val PUNTOS_POR_KM = 1.0
    const val PUNTOS_POR_CURVA = 0.25
    const val PUNTOS_POR_CARRETERA = 10.0
    const val PUNTOS_POR_SEMANA_ACTIVA = 10.0

    private val UMBRALES_KM = listOf(100, 250, 500, 1_000, 2_500, 5_000)
    private val UMBRALES_CURVAS = listOf(100, 500, 1_000, 2_500, 5_000, 10_000)
    private val UMBRALES_HORQUILLAS = listOf(10, 50, 100, 250, 500)
    private val UMBRALES_CARRETERAS = listOf(5, 10, 25, 50)
    private val UMBRALES_RACHA = listOf(2, 4, 8, 12, 26)

    private fun hito(
        categoria: CategoriaHito,
        umbral: Int,
        serie: SerieAcumulada,
    ): Hito {
        val fecha = serie.fechaDe(umbral)
        val titulo =
            when (categoria) {
                CategoriaHito.KM -> "$umbral km"
                CategoriaHito.CURVAS -> "$umbral curvas"
                CategoriaHito.HORQUILLAS -> "$umbral horquillas"
                CategoriaHito.CARRETERAS -> "$umbral carreteras distintas"
                CategoriaHito.RACHA -> "$umbral semanas seguidas"
            }
        return Hito(
            id = "${categoria.name.lowercase()}-$umbral",
            categoria = categoria,
            umbral = umbral,
            titulo = titulo,
            valor = serie.total,
            progreso = if (fecha != null) 1.0 else (serie.total / umbral).coerceIn(0.0, 1.0),
            conseguidoMs = fecha,
        )
    }

    /** Catálogo completo, en orden de categoría y umbral creciente. */
    fun catalogo(series: Map<CategoriaHito, SerieAcumulada>): List<Hito> =
        listOf(
            CategoriaHito.KM to UMBRALES_KM,
            CategoriaHito.CURVAS to UMBRALES_CURVAS,
            CategoriaHito.HORQUILLAS to UMBRALES_HORQUILLAS,
            CategoriaHito.CARRETERAS to UMBRALES_CARRETERAS,
            CategoriaHito.RACHA to UMBRALES_RACHA,
        ).flatMap { (categoria, umbrales) ->
            val serie = series.getValue(categoria)
            umbrales.map { hito(categoria, it, serie) }
        }

    fun nivel(puntos: Int): NivelProgreso {
        val niveles = NivelPiloto.entries
        val actual = niveles.last { puntos >= it.puntosMinimos }
        val siguiente = niveles.getOrNull(actual.ordinal + 1)
        val progreso =
            if (siguiente == null) {
                1.0
            } else {
                ((puntos - actual.puntosMinimos).toDouble() / (siguiente.puntosMinimos - actual.puntosMinimos))
                    .coerceIn(0.0, 1.0)
            }
        return NivelProgreso(actual, puntos, siguiente, siguiente?.puntosMinimos, progreso)
    }

    fun puntos(
        km: Double,
        curvas: Int,
        carreteras: Int,
        semanasActivas: Int,
    ): Int =
        floor(
            km * PUNTOS_POR_KM +
                curvas * PUNTOS_POR_CURVA +
                carreteras * PUNTOS_POR_CARRETERA +
                semanasActivas * PUNTOS_POR_SEMANA_ACTIVA,
        ).toInt()

    /** Para cada semana (lunes distintos y ordenados), la racha de semanas seguidas que termina en ella. */
    fun rachasPorSemana(lunes: List<LocalDate>): List<Int> {
        var racha = 0
        var previo: LocalDate? = null
        return lunes.map {
            val anterior = previo
            racha = if (anterior != null && ChronoUnit.WEEKS.between(anterior, it) == 1L) racha + 1 else 1
            previo = it
            racha
        }
    }
}
