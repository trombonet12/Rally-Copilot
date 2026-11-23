package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.TrazaMetros
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Hacia dónde crece el punto kilométrico (o, sin PK, el orden de dibujo de la vía) a lo largo de la marcha. */
enum class SentidoVia { CRECIENTE, DECRECIENTE, DESCONOCIDO }

/** Punto de la traza de un viaje con su distancia acumulada [distanciaM] desde la salida (metros de la traza). */
data class PuntoTraza(
    val punto: PuntoGeo,
    val distanciaM: Double,
)

/**
 * Trozo de un viaje por una misma carretera y sentido. [desdeM] y [hastaM] son metros sobre la traza del viaje.
 * [ref] es la primera referencia de la vía como la escribe OSM («Ma-15»); sin ella el tramo se agrupó por [nombreVia].
 * [pkDesde] y [pkHasta] son los kilómetros de la carretera al empezar y acabar (null si no hay hitos). [destino] es el
 * nombre del extremo hacia el que se va; null si ningún dato lo da. [trazado] son los puntos de la traza del viaje
 * dentro del tramo, para compararlo con otros por geometría cuando no hay PK. [pkEstimado]: el PK sale de la
 * distancia a lo largo del eje de la carretera desde su origen y no de hitos de OSM.
 */
data class TramoVia(
    val ref: String?,
    val nombreVia: String?,
    val desdeM: Double,
    val hastaM: Double,
    val pkDesde: Double?,
    val pkHasta: Double?,
    val sentido: SentidoVia,
    val destino: String?,
    val trazado: List<PuntoGeo> = emptyList(),
    val pkEstimado: Boolean = false,
) {
    val longitudM: Double get() = hastaM - desdeM

    /** Carretera identificada de forma estable (ref normalizada o, sin ref, nombre normalizado). */
    val idVia: String? get() = idDeVia(ref, nombreVia)

    /** Clave para comparar tramos de distintos viajes: misma vía y mismo sentido (falta aún el solape). */
    val clave: ClaveTramoVia? get() = idVia?.let { ClaveTramoVia(it, sentido) }

    /** Intervalo de PK (menor, mayor) si hay ambos extremos. */
    internal val intervaloPk: ClosedFloatingPointRange<Double>?
        get() = if (pkDesde != null && pkHasta != null) min(pkDesde, pkHasta)..max(pkDesde, pkHasta) else null
}

/** (carretera, sentido): dos tramos de viajes distintos son candidatos a ser el mismo si comparten clave. */
data class ClaveTramoVia(
    val idVia: String,
    val sentido: SentidoVia,
)

private const val PREFIJO_NOMBRE = "~"
private const val METROS_POR_KM = 1_000.0
private const val SIN_NOMBRE = "Vía sin nombre"
private const val SEPARADOR = " · "
private const val FLECHA = "→"
private const val CERCANIA_GEOMETRIA_M = 30.0
private const val UMBRAL_SOLAPE = 0.5
private val LOCALE_ES: Locale = Locale.forLanguageTag("es-ES")

/** Primera referencia de una `ref` de OSM con varias (`Ma-13;Ma-13A` → `Ma-13`); null si está vacía. */
internal fun primeraRef(ref: String?): String? = ref?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }

/** Id estable de una carretera: la ref normalizada o, si no hay, el nombre normalizado con prefijo; null si ninguno. */
internal fun idDeVia(
    ref: String?,
    nombre: String?,
): String? =
    primeraRef(ref)?.let { IndicePuntosKm.normalizar(it) }?.takeIf { it.isNotEmpty() }
        ?: nombre?.let { IndicePuntosKm.normalizar(it) }?.takeIf { it.isNotEmpty() }?.let { PREFIJO_NOMBRE + it }

private fun decimal(valor: Double): String = String.format(LOCALE_ES, "%.1f", valor)

/**
 * Nombre legible del tramo de vía: «Ma-15 · km 20,1→32,4 · hacia Manacor». Sin PK «Ma-15 · 12,3 km»; sin destino se
 * omite «hacia …». Con PK estimado ([TramoVia.pkEstimado]) los kilómetros llevan «~»:
 * «km ~20,1→~32,4». Decimales con coma. Sin ref se usa el nombre de la vía.
 */
fun nombreTramoVia(tramo: TramoVia): String {
    val principal = primeraRef(tramo.ref) ?: tramo.nombreVia?.trim()?.takeIf { it.isNotEmpty() } ?: SIN_NOMBRE
    val recorrido =
        if (tramo.pkDesde != null && tramo.pkHasta != null) {
            val aprox = if (tramo.pkEstimado) "~" else ""
            "km $aprox${decimal(tramo.pkDesde)}$FLECHA$aprox${decimal(tramo.pkHasta)}"
        } else {
            "${decimal(tramo.longitudM / METROS_POR_KM)} km"
        }
    val destino =
        tramo.destino
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { "hacia $it" }
    return listOfNotNull(principal, recorrido, destino).joinToString(SEPARADOR)
}

/**
 * Cuánto se solapan dos tramos, de 0 a 1 respecto al más corto. Con PK en ambos, es la intersección de los intervalos
 * de kilómetros entre la longitud del menor. Sin PK en alguno, la fracción de puntos del trazado de un tramo que queda
 * a menos de 30 m del trazado del otro (la mayor de las dos direcciones). No mira la vía ni el sentido: eso es la
 * [TramoVia.clave].
 */
fun solapeTramosVia(
    a: TramoVia,
    b: TramoVia,
): Double {
    val pkA = a.intervaloPk
    val pkB = b.intervaloPk
    return if (pkA != null && pkB != null) solapePk(pkA, pkB) else max(cubierto(a, b), cubierto(b, a))
}

/** Mismo tramo de vía en dos viajes: misma [TramoVia.clave] y un solape de al menos [umbral] (por defecto la mitad). */
fun mismoTramoVia(
    a: TramoVia,
    b: TramoVia,
    umbral: Double = UMBRAL_SOLAPE,
): Boolean {
    val clave = a.clave
    return clave != null && clave == b.clave && solapeTramosVia(a, b) >= umbral
}

private fun solapePk(
    a: ClosedFloatingPointRange<Double>,
    b: ClosedFloatingPointRange<Double>,
): Double {
    val comun = min(a.endInclusive, b.endInclusive) - max(a.start, b.start)
    val menor = min(a.endInclusive - a.start, b.endInclusive - b.start)
    return if (comun <= 0.0 || menor <= 0.0) 0.0 else comun / menor
}

/** Fracción de los puntos de [a] a menos de 30 m del trazado de [b]. */
private fun cubierto(
    a: TramoVia,
    b: TramoVia,
): Double = if (a.trazado.size < 2) 0.0 else puntosCubiertos(a.trazado, b.trazado).size.toDouble() / a.trazado.size

/** Los puntos de [a] (en su orden) a menos de 30 m de la polilínea [b]; vacío si alguna tiene menos de dos puntos. */
internal fun puntosCubiertos(
    a: List<PuntoGeo>,
    b: List<PuntoGeo>,
): List<PuntoGeo> {
    if (a.size < 2 || b.size < 2) return emptyList()
    val trazaB = TrazaMetros(b)
    return a.filter { (trazaB.proyectar(it)?.distanciaM ?: Double.POSITIVE_INFINITY) <= CERCANIA_GEOMETRIA_M }
}
