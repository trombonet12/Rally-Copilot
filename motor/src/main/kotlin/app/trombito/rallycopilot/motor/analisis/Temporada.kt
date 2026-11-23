package app.trombito.rallycopilot.motor.analisis

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

private const val M_POR_KM = 1000.0

private fun fecha(
    ms: Long,
    zona: ZoneId,
): LocalDate = Instant.ofEpochMilli(ms).atZone(zona).toLocalDate()

private fun lunesDe(dia: LocalDate): LocalDate = dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private fun limpio(texto: String?): String? = texto?.trim()?.takeIf { it.isNotEmpty() }

private fun inicioMs(
    mes: YearMonth,
    zona: ZoneId,
): Long =
    mes
        .atDay(1)
        .atStartOfDay(zona)
        .toInstant()
        .toEpochMilli()

/** Un viaje de la temporada: su [fechaMs] de inicio (epoch ms), [distanciaM], [curvas] por grado y vías [usos]. */
class RecorridoTemporada(
    val fechaMs: Long,
    val distanciaM: Double,
    val curvas: CurvasPorGrado,
    val usos: List<UsoVia>,
) {
    companion object {
        /** Recorrido con el análisis de [AgregadoCarreteras.analizar]; [distanciaM] por defecto la del análisis. */
        fun de(
            fechaMs: Long,
            analisis: AnalisisRecorrido,
            distanciaM: Double = analisis.metros,
        ) = RecorridoTemporada(fechaMs, distanciaM, analisis.curvas, analisis.usos)
    }
}

/** Kilómetros de una semana ISO: [lunes] es su primer día, [anio] el año ISO y [semana] su número (1..53). */
data class KmSemana(
    val anio: Int,
    val semana: Int,
    val lunes: LocalDate,
    val km: Double,
    val viajes: Int,
)

/** Kilómetros y viajes de un [mes]. */
data class KmMes(
    val mes: YearMonth,
    val km: Double,
    val viajes: Int,
)

/** Racha de semanas ISO seguidas con al menos un viaje: la [actual] (viva si hay viaje esta semana o la pasada). */
data class Racha(
    val actual: Int,
    val mejor: Int,
)

/** Una pasada que mejoró el mejor tiempo previo de su tramo en [mejoraMs] (la primera de un tramo nunca cuenta). */
data class RecordBatido(
    val tramoId: String,
    val nombreTramo: String,
    val pasadaId: String,
    val fechaMs: Long,
    val tiempoMs: Long,
    val anteriorMejorMs: Long,
    val mejoraMs: Long,
)

/** Resumen de un [mes]: km, viajes, carreteras estrenadas, hitos y récords logrados en él, y nivel a fin de mes. */
data class ResumenMes(
    val mes: YearMonth,
    val km: Double,
    val viajes: Int,
    val carreterasNuevas: List<String>,
    val hitosConseguidos: List<Hito>,
    val records: List<RecordBatido>,
    val nivel: NivelProgreso,
)

/**
 * Progresión tipo juego sobre los viajes y las pasadas: km por semana ISO y por mes, racha de semanas, hitos, nivel
 * de piloto, vitrina de récords y resumen del mes. Puro y determinista: la zona horaria y el «ahora» son parámetros.
 *
 * **Nivel de piloto.** Puntos = `km + 0,25 · curvas (todas: grados, escuadras y horquillas) + 10 · carreteras
 * distintas + 10 · semanas con algún viaje`, redondeados hacia abajo. Nunca entran velocidad ni tiempos, para no
 * premiar correr en carretera abierta. Niveles ([NivelPiloto]): Debutante 0, Aficionado 100, Piloto de club 400,
 * Piloto nacional 1 200, Piloto oficial 3 500, Leyenda 10 000. Una carretera es distinta por su `ref` (o, sin ella,
 * su nombre), igual que en «Mis carreteras».
 *
 * Los hitos se miden sobre el acumulado histórico; la fecha de un hito es la del viaje que lo alcanzó (en la racha,
 * el primer viaje de la semana que completó la racha).
 */
data class Temporada(
    val kmTotales: Double,
    val viajes: Int,
    /** Desde la primera semana con viaje hasta la actual (o la del último viaje), con las semanas vacías a cero. */
    val kmPorSemana: List<KmSemana>,
    /** Solo los meses con algún viaje, en orden cronológico. */
    val kmPorMes: List<KmMes>,
    val racha: Racha,
    val hitos: List<Hito>,
    /** El hito pendiente más avanzado (el primero del catálogo en caso de empate), o null si están todos. */
    val siguienteHito: Hito?,
    val carreterasDistintas: Int,
    val puntos: Int,
    val nivel: NivelProgreso,
    /** Récords batidos, el más reciente primero. */
    val records: List<RecordBatido>,
    /** Resumen del mes de `ahoraMs`. */
    val resumenMes: ResumenMes,
) {
    companion object {
        /**
         * Calcula la temporada con [recorridos] y las [tramos] con sus pasadas, en la [zona] dada y con [ahoraMs]
         * (epoch ms) como «hoy» para la racha actual y el mes del resumen.
         */
        fun de(
            recorridos: List<RecorridoTemporada>,
            tramos: List<TramoRegistrado>,
            zona: ZoneId,
            ahoraMs: Long,
        ): Temporada {
            val ordenados = recorridos.sortedBy { it.fechaMs }
            val hoy = fecha(ahoraMs, zona)
            val lunes = ordenados.map { lunesDe(fecha(it.fechaMs, zona)) }.distinct()
            val rachas = Progreso.rachasPorSemana(lunes)
            val carreteras = carreterasDistintas(ordenados)
            val hitos = Progreso.catalogo(series(ordenados, zona, lunes, rachas, carreteras))
            val km = ordenados.sumOf { it.distanciaM } / M_POR_KM
            val puntos = Progreso.puntos(km, ordenados.sumOf { it.curvas.total }, carreteras.size, lunes.size)
            val records = records(tramos)
            return Temporada(
                kmTotales = km,
                viajes = ordenados.size,
                kmPorSemana = kmPorSemana(ordenados, zona, hoy),
                kmPorMes = kmPorMes(ordenados, zona),
                racha = Racha(rachaActual(lunes, rachas, hoy), rachas.maxOrNull() ?: 0),
                hitos = hitos,
                siguienteHito = hitos.filter { !it.conseguido }.maxByOrNull { it.progreso },
                carreterasDistintas = carreteras.size,
                puntos = puntos,
                nivel = Progreso.nivel(puntos),
                records = records,
                resumenMes = resumenMes(ordenados, zona, YearMonth.from(hoy), hitos, records),
            )
        }

        /** Racha viva a [hoy]: la que termina esta semana o, si aún no hay viaje, la que terminó la semana pasada. */
        private fun rachaActual(
            lunes: List<LocalDate>,
            rachas: List<Int>,
            hoy: LocalDate,
        ): Int {
            val estaSemana = lunesDe(hoy)
            val indice = lunes.indexOfLast { !it.isAfter(estaSemana) }
            if (indice < 0) return 0
            val semanasDesde = ChronoUnit.WEEKS.between(lunes[indice], estaSemana)
            return if (semanasDesde <= 1L) rachas[indice] else 0
        }

        private fun kmPorSemana(
            ordenados: List<RecorridoTemporada>,
            zona: ZoneId,
            hoy: LocalDate,
        ): List<KmSemana> {
            if (ordenados.isEmpty()) return emptyList()
            val porLunes = ordenados.groupBy { lunesDe(fecha(it.fechaMs, zona)) }
            val primero = porLunes.keys.min()
            val ultimo = maxOf(porLunes.keys.max(), lunesDe(hoy))
            val semanas = ChronoUnit.WEEKS.between(primero, ultimo).toInt()
            return (0..semanas).map { i ->
                val lunes = primero.plusWeeks(i.toLong())
                val deLaSemana = porLunes[lunes].orEmpty()
                KmSemana(
                    anio = lunes.get(IsoFields.WEEK_BASED_YEAR),
                    semana = lunes.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                    lunes = lunes,
                    km = deLaSemana.sumOf { it.distanciaM } / M_POR_KM,
                    viajes = deLaSemana.size,
                )
            }
        }

        private fun kmPorMes(
            ordenados: List<RecorridoTemporada>,
            zona: ZoneId,
        ): List<KmMes> =
            ordenados
                .groupBy { YearMonth.from(fecha(it.fechaMs, zona)) }
                .toSortedMap()
                .map { (mes, lista) -> KmMes(mes, lista.sumOf { it.distanciaM } / M_POR_KM, lista.size) }

        /** Carretera distinta: nombre para mostrar y fecha del primer viaje que la recorrió. */
        private class CarreteraVista(
            val nombre: String,
            val primeraMs: Long,
        )

        /** Carreteras distintas por clave (`ref`, o nombre sin ella), como en «Mis carreteras». */
        private fun carreterasDistintas(ordenados: List<RecorridoTemporada>): Map<String, CarreteraVista> {
            val vistas = LinkedHashMap<String, CarreteraVista>()
            for (recorrido in ordenados) {
                for (uso in recorrido.usos.sortedByDescending { it.metros }) {
                    val ref = limpio(uso.via.ref)
                    val nombre = limpio(uso.via.nombre)
                    val clave = ref?.let { "ref:${it.uppercase()}" } ?: nombre?.let { "nombre:${it.lowercase()}" }
                    val visible = ref ?: nombre
                    if (clave != null && visible != null) {
                        vistas.getOrPut(clave) { CarreteraVista(visible, recorrido.fechaMs) }
                    }
                }
            }
            return vistas
        }

        private fun series(
            ordenados: List<RecorridoTemporada>,
            zona: ZoneId,
            lunes: List<LocalDate>,
            rachas: List<Int>,
            carreteras: Map<String, CarreteraVista>,
        ): Map<CategoriaHito, SerieAcumulada> {
            fun acumulada(valor: (RecorridoTemporada) -> Double): SerieAcumulada {
                var suma = 0.0
                return SerieAcumulada(
                    ordenados.map {
                        suma += valor(it)
                        it.fechaMs to suma
                    },
                )
            }
            var mejor = 0
            val racha =
                lunes.indices.map { i ->
                    mejor = maxOf(mejor, rachas[i])
                    val primerViaje = ordenados.first { lunesDe(fecha(it.fechaMs, zona)) == lunes[i] }.fechaMs
                    primerViaje to mejor.toDouble()
                }
            val estrenos = carreteras.values.map { it.primeraMs }.sorted()
            return mapOf(
                CategoriaHito.KM to acumulada { it.distanciaM / M_POR_KM },
                CategoriaHito.CURVAS to acumulada { it.curvas.total.toDouble() },
                CategoriaHito.HORQUILLAS to acumulada { it.curvas.horquillas.toDouble() },
                CategoriaHito.CARRETERAS to SerieAcumulada(estrenos.mapIndexed { i, ms -> ms to (i + 1).toDouble() }),
                CategoriaHito.RACHA to SerieAcumulada(racha),
            )
        }

        /** Pasadas que mejoran estrictamente el mejor tiempo previo de su tramo; la primera de cada tramo no cuenta. */
        private fun records(tramos: List<TramoRegistrado>): List<RecordBatido> =
            tramos
                .flatMap { tramo ->
                    var mejor: Long? = null
                    tramo.pasadas.sortedBy { it.fechaMs }.mapNotNull { p ->
                        val previo = mejor
                        val tiempo = p.pasada.totalMs
                        if (previo == null || tiempo < previo) mejor = tiempo
                        if (previo != null && tiempo < previo) {
                            RecordBatido(tramo.tramoId, tramo.nombre, p.id, p.fechaMs, tiempo, previo, previo - tiempo)
                        } else {
                            null
                        }
                    }
                }.sortedByDescending { it.fechaMs }

        private fun resumenMes(
            ordenados: List<RecorridoTemporada>,
            zona: ZoneId,
            mes: YearMonth,
            hitos: List<Hito>,
            records: List<RecordBatido>,
        ): ResumenMes {
            val inicio = inicioMs(mes, zona)
            val fin = inicioMs(mes.plusMonths(1), zona)
            val enMes = { ms: Long -> ms in inicio until fin }
            val delMes = ordenados.filter { enMes(it.fechaMs) }
            val hastaFinDeMes = ordenados.filter { it.fechaMs < fin }
            val semanas = hastaFinDeMes.map { lunesDe(fecha(it.fechaMs, zona)) }.distinct().size
            val puntos =
                Progreso.puntos(
                    km = hastaFinDeMes.sumOf { it.distanciaM } / M_POR_KM,
                    curvas = hastaFinDeMes.sumOf { it.curvas.total },
                    carreteras = carreterasDistintas(hastaFinDeMes).size,
                    semanasActivas = semanas,
                )
            return ResumenMes(
                mes = mes,
                km = delMes.sumOf { it.distanciaM } / M_POR_KM,
                viajes = delMes.size,
                carreterasNuevas =
                    carreterasDistintas(ordenados)
                        .values
                        .filter { enMes(it.primeraMs) }
                        .map { it.nombre },
                hitosConseguidos = hitos.filter { it.conseguidoMs?.let(enMes) == true },
                records = records.filter { enMes(it.fechaMs) },
                nivel = Progreso.nivel(puntos),
            )
        }
    }
}
