package app.trombito.rallycopilot.motor.analisis

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val M_POR_KM = 1_000.0

/** Último grado de las curvas lentas (1-2), de las medias (3-4) y de todos (6). */
private const val ULTIMO_GRADO_LENTA = 2
private const val ULTIMO_GRADO_MEDIA = 4
private const val ULTIMO_GRADO = 6

/** Tipo de tarjeta del tablero del piloto; el orden del enum es el orden en que salen. */
enum class TipoConclusion {
    RITMO,
    ULTIMO_VIAJE,
    CARRETERA_FAVORITA,
    CARRETERA_REVIRADA,
    CURVA_TIPO,
    KM_ESTRENADOS,
    RECORDS,
    VELOCIDAD_POR_GRADO,
}

/**
 * Tarjeta del tablero: [cifra] es el número grande («71 km/h») y [frase] la frase completa en español, que ya contiene
 * la cifra (así se puede resaltar dentro del texto o mostrar aparte).
 */
data class Conclusion(
    val tipo: TipoConclusion,
    val cifra: String,
    val frase: String,
)

/**
 * Conclusiones del perfil del piloto: frases cortas y deterministas a partir de lo ya agregado. Cada una tiene un
 * umbral mínimo y, si no lo cumple, no aparece. Puro: sin reloj ni formato de fecha del sistema.
 *
 * No premia la velocidad: la velocidad que se enseña es siempre una media (en marcha, o de paso por curva), nunca una
 * máxima.
 */
object ConclusionesPiloto {
    /** Metros en marcha para hablar del ritmo global, y del último viaje, y de lo previo a este para compararlos. */
    const val MIN_METROS_RITMO = 5_000.0
    const val MIN_METROS_ULTIMO_VIAJE = 2_000.0

    /** Viajes y pasadas mínimos para hablar de «tu carretera», y sus metros. */
    const val MIN_VIAJES_CARRETERA = 3
    const val MIN_PASADAS_CARRETERA = 2
    const val MIN_METROS_CARRETERA = 5_000.0

    /** Para «la más revirada»: metros conducidos y curvas por km mínimos. */
    const val MIN_METROS_REVIRADA = 2_000.0
    const val MIN_CURVAS_POR_KM = 2.0

    /** Curvas totales mínimas para el reparto por grado. */
    const val MIN_CURVAS_REPARTO = 20

    /** Metros de vías con una sola pasada mínimos para «estrenados». */
    const val MIN_METROS_ESTRENADOS = 5_000.0

    /** Curvas medidas mínimas de un grado para hablar de su velocidad de paso, y grados que se enseñan como mucho. */
    const val MIN_CURVAS_GRADO = 5
    const val MAX_GRADOS_VELOCIDAD = 3

    /** Diferencia (km/h, redondeada) por debajo de la cual el último viaje «va como siempre». */
    private const val DIFERENCIA_IGUAL_KMH = 1

    /**
     * Tarjetas de [perfil], [carreteras], [velocidades] (agregado de `VelocidadVias`, que ya incluye el último viaje)
     * y [velocidadPorGrado]. [ultimoViaje] es el análisis con tiempo del último recorrido (null si no hay).
     * [primerViajeMs] es la fecha (epoch ms) del primer viaje: con ella y [ahoraMs] se sabe si todo lo estrenado es de
     * este mes (en [zona]); sin ella, o si es de antes, la tarjeta de estrenados habla sin fecha.
     */
    @Suppress("LongParameterList")
    fun de(
        perfil: PerfilPiloto,
        carreteras: ResumenCarreteras,
        velocidades: List<VelocidadVia>,
        velocidadPorGrado: VelocidadPorGrado,
        ultimoViaje: AnalisisMarcha? = null,
        ahoraMs: Long = 0L,
        primerViajeMs: Long? = null,
        zona: ZoneId = ZoneOffset.UTC,
    ): List<Conclusion> {
        val global = velocidades.fold(Marcha()) { acc, v -> acc + v.total }
        return listOfNotNull(
            ritmo(global),
            ultimoViaje(global, ultimoViaje),
            carreteraFavorita(carreteras),
            carreteraRevirada(carreteras),
            curvaTipo(carreteras.curvas),
            kmEstrenados(carreteras, ahoraMs, primerViajeMs, zona),
            records(perfil),
            velocidadPorGrado(velocidadPorGrado),
        )
    }

    private fun ritmo(global: Marcha): Conclusion? {
        val vel = global.velMediaKmh?.takeIf { global.metros >= MIN_METROS_RITMO } ?: return null
        val cifra = "${vel.roundToInt()} km/h"
        return Conclusion(TipoConclusion.RITMO, cifra, "Vas a $cifra de media en marcha, sin contar las paradas.")
    }

    private fun ultimoViaje(
        global: Marcha,
        ultimoViaje: AnalisisMarcha?,
    ): Conclusion? {
        val ultimo = ultimoViaje?.usos?.fold(Marcha()) { acc, uso -> acc + uso.total }
        val resto = ultimo?.let { Marcha(global.metros - it.metros, global.tiempoMs - it.tiempoMs) }
        val velUltimo = ultimo?.takeIf { it.metros >= MIN_METROS_ULTIMO_VIAJE }?.velMediaKmh?.roundToInt()
        val velResto = resto?.takeIf { it.metros >= MIN_METROS_RITMO }?.velMediaKmh?.roundToInt()
        if (velUltimo == null || velResto == null) return null
        val diferencia = velUltimo - velResto
        val cifra = "$velUltimo km/h"
        val comparacion =
            when {
                abs(diferencia) <= DIFERENCIA_IGUAL_KMH -> "como siempre ($velResto)"
                diferencia > 0 -> "$diferencia por encima de tu ritmo habitual ($velResto)"
                else -> "${-diferencia} por debajo de tu ritmo habitual ($velResto)"
            }
        return Conclusion(
            TipoConclusion.ULTIMO_VIAJE,
            cifra,
            "En tu último viaje fuiste a $cifra en marcha: $comparacion.",
        )
    }

    private fun carreteraFavorita(carreteras: ResumenCarreteras): Conclusion? {
        val elegida =
            carreteras.carreteras
                .takeIf { carreteras.recorridos >= MIN_VIAJES_CARRETERA }
                ?.filter { it.pasadas >= MIN_PASADAS_CARRETERA && it.metros >= MIN_METROS_CARRETERA }
                ?.maxWithOrNull(compareBy<Carretera> { it.pasadas }.thenBy { it.metros })
        val etiqueta = elegida?.let { it.ref ?: it.nombre }
        if (elegida == null || etiqueta == null) return null
        val articulo = if (elegida.ref != null) "la " else ""
        val viajes =
            if (elegida.pasadas == carreteras.recorridos) {
                "en todos tus ${carreteras.recorridos} viajes"
            } else {
                "en ${elegida.pasadas} de tus ${carreteras.recorridos} viajes"
            }
        return Conclusion(
            TipoConclusion.CARRETERA_FAVORITA,
            etiqueta,
            "Tu carretera es $articulo$etiqueta: has pasado $viajes y " +
                "llevas ${FormatoConclusiones.km(elegida.metros)} km en ella.",
        )
    }

    private fun carreteraRevirada(carreteras: ResumenCarreteras): Conclusion? {
        val mejor =
            carreteras.carreteras
                .filter { it.metros >= MIN_METROS_REVIRADA }
                .map { it to it.curvas / (it.metros / M_POR_KM) }
                .filter { it.second >= MIN_CURVAS_POR_KM }
                .maxByOrNull { it.second }
        val etiqueta = mejor?.first?.let { it.ref ?: it.nombre }
        if (mejor == null || etiqueta == null) return null
        val valor = FormatoConclusiones.decimalCorto(mejor.second)
        return Conclusion(
            TipoConclusion.CARRETERA_REVIRADA,
            "$valor curvas/km",
            "La más revirada que has hecho: $etiqueta, $valor curvas por kilómetro.",
        )
    }

    private class Categoria(
        val singular: String,
        val plural: String,
        val grados: String,
        val cuenta: Int,
    )

    private fun curvaTipo(curvas: CurvasPorGrado): Conclusion? {
        val total = curvas.total
        if (total < MIN_CURVAS_REPARTO) return null

        fun de(grados: IntRange) = grados.sumOf { curvas.deGrado(it) }
        val categorias =
            listOf(
                Categoria("rápida", "rápidas", " (grados 5 y 6)", de(ULTIMO_GRADO_MEDIA + 1..ULTIMO_GRADO)),
                Categoria(
                    "media",
                    "medias",
                    " (3 y 4)",
                    de(ULTIMO_GRADO_LENTA + 1..ULTIMO_GRADO_MEDIA) + curvas.escuadras,
                ),
                Categoria("lenta", "lentas", " (1 y 2)", de(1..ULTIMO_GRADO_LENTA)),
                Categoria("horquilla", "horquillas", "", curvas.horquillas),
            )
        val repartoDe10 = FormatoConclusiones.repartoDeDiez(categorias.map { it.cuenta }, total)
        val presentes = categorias.indices.filter { repartoDe10[it] > 0 }
        val partes =
            presentes.mapIndexed { orden, i ->
                val n = repartoDe10[i]
                val nombre = if (n == 1) categorias[i].singular else categorias[i].plural
                val verbo = if (orden == 0) (if (n == 1) "es " else "son ") else ""
                "$n $verbo$nombre${categorias[i].grados}"
            }
        val ultima = partes.last()
        val lista = if (partes.size == 1) ultima else partes.dropLast(1).joinToString(", ") + " y " + ultima
        return Conclusion(
            TipoConclusion.CURVA_TIPO,
            "${repartoDe10[presentes.maxBy { repartoDe10[it] }]} de 10",
            "De cada 10 curvas, $lista.",
        )
    }

    private fun kmEstrenados(
        carreteras: ResumenCarreteras,
        ahoraMs: Long,
        primerViajeMs: Long?,
        zona: ZoneId,
    ): Conclusion? {
        val metros = carreteras.vias.filter { it.pasadas == 1 }.sumOf { it.metros }
        if (metros < MIN_METROS_ESTRENADOS) return null
        val kilometros = "${FormatoConclusiones.km(metros)} km"
        val esteMes = primerViajeMs != null && FormatoConclusiones.mismoMes(primerViajeMs, ahoraMs, zona)
        val frase =
            if (esteMes) {
                "Este mes has estrenado $kilometros de carretera."
            } else {
                "Tienes $kilometros de carretera estrenada: solo has pasado una vez por ellos."
            }
        return Conclusion(TipoConclusion.KM_ESTRENADOS, kilometros, frase)
    }

    /** Un récord de verdad: una pasada que mejora una anterior del mismo tramo. */
    private class RecordReal(
        val fechaMs: Long,
        val nombre: String,
        val mejoraMs: Long,
    )

    private fun records(perfil: PerfilPiloto): Conclusion? {
        val reales =
            perfil.tramos.flatMap { tramo ->
                var mejorAntes = Long.MAX_VALUE
                tramo.evolucion.mapNotNull { punto ->
                    val real = punto.esRecord && mejorAntes != Long.MAX_VALUE
                    val mejora = mejorAntes - punto.tiempoMs
                    mejorAntes = minOf(mejorAntes, punto.tiempoMs)
                    if (real) RecordReal(punto.fechaMs, tramo.nombre, mejora) else null
                }
            }
        val ultimo = reales.maxByOrNull { it.fechaMs } ?: return null
        val mejora = FormatoConclusiones.duracion(ultimo.mejoraMs)
        val frase =
            if (reales.size == 1) {
                "Has mejorado tu mejor tiempo en ${ultimo.nombre}: $mejora menos que el anterior."
            } else {
                "Has mejorado tu mejor tiempo ${reales.size} veces; la última, en ${ultimo.nombre} ($mejora menos)."
            }
        return Conclusion(TipoConclusion.RECORDS, reales.size.toString(), frase)
    }

    private fun velocidadPorGrado(medida: VelocidadPorGrado): Conclusion? {
        // Índice 0 = horquillas, 1..6 = grados: de la más cerrada a la más suave.
        val candidatos =
            (0..ULTIMO_GRADO).mapNotNull { i ->
                val paso = if (i == 0) medida.horquillas else medida.deGrado(i)
                val vel = paso.velMediaKmh
                if (vel != null && paso.curvas >= MIN_CURVAS_GRADO) Triple(i, paso.curvas, vel.roundToInt()) else null
            }
        if (candidatos.isEmpty()) return null
        val elegidos =
            candidatos
                .sortedWith(compareByDescending<Triple<Int, Int, Int>> { it.second }.thenBy { it.first })
                .take(MAX_GRADOS_VELOCIDAD)
        val partes =
            elegidos.sortedBy { it.first }.mapIndexed { orden, (i, _, kmh) ->
                when {
                    orden == 0 && i == 0 -> "En las horquillas pasas a $kmh km/h de media"
                    orden == 0 && elegidos.size == 1 -> "En las curvas de grado $i pasas a $kmh km/h de media"
                    orden == 0 -> "En las de grado $i pasas a $kmh km/h de media"
                    i == 0 -> "en las horquillas, a $kmh"
                    else -> "en las de $i, a $kmh"
                }
            }
        return Conclusion(
            TipoConclusion.VELOCIDAD_POR_GRADO,
            "${elegidos.first().third} km/h",
            partes.joinToString("; ") + ".",
        )
    }
}

/** Formato de números, duraciones y fechas de las [Conclusion]; todo en español (coma decimal). */
internal object FormatoConclusiones {
    private const val MS_POR_S = 1_000.0
    private const val S_POR_MIN = 60
    private const val DIEZ = 10
    private const val UN_DECIMAL_HASTA = 10.0

    /** Kilómetros de [metros]: sin decimales desde 10, con uno (y coma) por debajo, sin «,0» sobrante. */
    fun km(metros: Double): String = decimalCorto(metros / M_POR_KM)

    /** [valor] sin decimales desde 10 y con uno (y coma) por debajo, sin «,0» sobrante. */
    fun decimalCorto(valor: Double): String =
        if (valor >= UN_DECIMAL_HASTA) {
            valor.roundToInt().toString()
        } else {
            String.format(Locale.ROOT, "%.1f", valor).removeSuffix(".0").replace('.', ',')
        }

    /** Duración corta: «2,3 s» por debajo de un minuto y «1 min 05 s» desde ahí. */
    fun duracion(ms: Long): String {
        val s = ms / MS_POR_S
        val total = s.roundToInt()
        return if (s < S_POR_MIN) {
            String.format(Locale.ROOT, "%.1f s", s).replace('.', ',')
        } else {
            "${total / S_POR_MIN} min ${(total % S_POR_MIN).toString().padStart(2, '0')} s"
        }
    }

    /** Reparte 10 entre [cuentas] en proporción (mayor resto); el empate lo gana la categoría de más a la izquierda. */
    fun repartoDeDiez(
        cuentas: List<Int>,
        total: Int,
    ): List<Int> {
        val base = cuentas.map { it * DIEZ / total }.toMutableList()
        val restos = cuentas.mapIndexed { i, c -> i to (c * DIEZ % total) }
        val orden = restos.sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first })
        for ((i, _) in orden.take(DIEZ - base.sum())) base[i]++
        return base
    }

    /** Si [aMs] y [bMs] (epoch ms) caen en el mismo mes del calendario en [zona]. */
    fun mismoMes(
        aMs: Long,
        bMs: Long,
        zona: ZoneId,
    ): Boolean = mes(aMs, zona) == mes(bMs, zona)

    private fun mes(
        epochMs: Long,
        zona: ZoneId,
    ): YearMonth = YearMonth.from(Instant.ofEpochMilli(epochMs).atZone(zona))
}
