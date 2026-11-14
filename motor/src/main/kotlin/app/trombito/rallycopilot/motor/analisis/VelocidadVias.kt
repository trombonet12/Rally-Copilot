package app.trombito.rallycopilot.motor.analisis

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.Rumbo
import app.trombito.rallycopilot.motor.vias.Coincidencia
import app.trombito.rallycopilot.motor.vias.Via

private const val M_POR_KM = 1_000.0
private const val KMH_POR_M_MS = 3_600.0

/** Un punto del recorrido con su instante ([tiempoMs], monótono o epoch: solo importan las diferencias). */
data class PuntoTiempo(
    val punto: PuntoGeo,
    val tiempoMs: Long,
    /** Velocidad del GPS en m/s si la hay; sin ella se deduce de la distancia y el tiempo. */
    val velocidadMs: Double? = null,
)

/** Metros y tiempo EN MARCHA acumulados: se suman las dos magnitudes, nunca las medias. */
data class Marcha(
    val metros: Double = 0.0,
    val tiempoMs: Long = 0L,
) {
    /** Velocidad media en marcha (km/h), o null sin tiempo. */
    val velMediaKmh: Double? get() = if (tiempoMs > 0L) metros / tiempoMs * KMH_POR_M_MS else null

    operator fun plus(otra: Marcha) = Marcha(metros + otra.metros, tiempoMs + otra.tiempoMs)
}

/** Lo que un recorrido hizo en una vía: la marcha en el sentido de `via.puntos` ([adelante]) y en el contrario. */
class UsoViaMarcha(
    val via: Via,
    val adelante: Marcha,
    val atras: Marcha,
) {
    val total: Marcha get() = adelante + atras
}

/** Análisis de un recorrido con tiempo: una entrada por vía que recorrió al menos [VelocidadVias.MIN_PASADA_M]. */
class AnalisisMarcha(
    val usos: List<UsoViaMarcha>,
)

/** Velocidad real acumulada en una vía a lo largo de [pasadas] recorridos. */
data class VelocidadVia(
    val via: Via,
    val pasadas: Int,
    val adelante: Marcha,
    val atras: Marcha,
) {
    val total: Marcha get() = adelante + atras

    /** Velocidad media en marcha (km/h) en ambos sentidos; null sin datos. */
    val velMediaKmh: Double? get() = total.velMediaKmh

    val km: Double get() = total.metros / M_POR_KM

    /** Velocidad media (km/h) en el [sentidoAdelante] o en el contrario; null si nunca se condujo así. */
    fun velMediaKmh(sentidoAdelante: Boolean): Double? = (if (sentidoAdelante) adelante else atras).velMediaKmh
}

/**
 * Velocidad media real por vía a partir de recorridos con tiempo ([PuntoTiempo]). Puro y acumulable: [analizar] resume
 * un recorrido y [agregar] suma metros y tiempos (no medias) a lo ya acumulado.
 *
 * Cada par de puntos seguidos cuenta como un tramo asignado a la vía de su punto medio. No cuentan (ni metros ni
 * tiempo) los tramos con hueco de más de [MAX_HUECO_MS], tiempo no positivo, salto de más de
 * [AgregadoCarreteras.MAX_SALTO_M] o velocidad por debajo de [MIN_MARCHA_KMH] (parado). Una vía solo suma pasada si el
 * recorrido la hizo al menos [MIN_PASADA_M] en marcha.
 */
object VelocidadVias {
    const val MIN_MARCHA_KMH = 5.0
    const val MAX_HUECO_MS = 30_000L
    const val MIN_PASADA_M = AgregadoCarreteras.MIN_PASADA_M
    private const val KMH_POR_M_S = 3.6

    /** Vía de un punto dado el rumbo de la marcha, con el sentido (`adelante`); null si no hay ninguna cerca. */
    fun interface CoincidenciaDe {
        fun coincidencia(
            punto: PuntoGeo,
            rumboGrados: Double,
        ): Coincidencia?
    }

    private class Acumulado(
        val via: Via,
    ) {
        var adelante = Marcha()
        var atras = Marcha()
    }

    fun analizar(
        puntos: List<PuntoTiempo>,
        coincidenciaDe: CoincidenciaDe,
    ): AnalisisMarcha {
        val porVia = LinkedHashMap<Long, Acumulado>()
        val asignados =
            puntos.zipWithNext().mapNotNull { (a, b) ->
                val tramo = tramoEnMarcha(a, b) ?: return@mapNotNull null
                coincidenciaDe.coincidencia(tramo.medio, tramo.rumbo)?.let { it to tramo.marcha }
            }
        for ((coincidencia, marcha) in asignados) {
            val acumulado = porVia.getOrPut(coincidencia.via.id) { Acumulado(coincidencia.via) }
            if (coincidencia.adelante) acumulado.adelante += marcha else acumulado.atras += marcha
        }
        val usos =
            porVia.values
                .filter { (it.adelante + it.atras).metros >= MIN_PASADA_M }
                .map { UsoViaMarcha(it.via, it.adelante, it.atras) }
        return AnalisisMarcha(usos)
    }

    /** Suma [analisis] (uno por recorrido) a [base] (una pasada por recorrido y vía). */
    fun agregar(
        analisis: List<AnalisisMarcha>,
        base: List<VelocidadVia> = emptyList(),
    ): List<VelocidadVia> {
        val porVia = LinkedHashMap<Long, VelocidadVia>()
        for (previa in base) porVia[previa.via.id] = previa
        for (recorrido in analisis) {
            for (uso in recorrido.usos) {
                val previa = porVia[uso.via.id]
                porVia[uso.via.id] =
                    VelocidadVia(
                        via = uso.via,
                        pasadas = (previa?.pasadas ?: 0) + 1,
                        adelante = (previa?.adelante ?: Marcha()) + uso.adelante,
                        atras = (previa?.atras ?: Marcha()) + uso.atras,
                    )
            }
        }
        return porVia.values.toList()
    }

    /** Analiza y suma recorridos de una vez. */
    fun resumir(
        recorridos: List<List<PuntoTiempo>>,
        coincidenciaDe: CoincidenciaDe,
    ): List<VelocidadVia> = agregar(recorridos.map { analizar(it, coincidenciaDe) })

    private class TramoEnMarcha(
        val medio: PuntoGeo,
        val rumbo: Double,
        val marcha: Marcha,
    )

    /** El tramo a-b si cuenta (en marcha, sin hueco ni salto); null si hay que descartarlo. */
    private fun tramoEnMarcha(
        a: PuntoTiempo,
        b: PuntoTiempo,
    ): TramoEnMarcha? {
        val distancia = a.punto.distanciaA(b.punto)
        val dt = b.tiempoMs - a.tiempoMs
        if (!enMarcha(a, b, distancia, dt)) return null
        val medio = PuntoGeo((a.punto.lat + b.punto.lat) / 2, (a.punto.lon + b.punto.lon) / 2)
        return TramoEnMarcha(medio, Rumbo.entre(a.punto, b.punto), Marcha(distancia, dt))
    }

    private fun enMarcha(
        a: PuntoTiempo,
        b: PuntoTiempo,
        distancia: Double,
        dt: Long,
    ): Boolean {
        if (dt <= 0L || dt > MAX_HUECO_MS || distancia > AgregadoCarreteras.MAX_SALTO_M) return false
        val va = a.velocidadMs
        val vb = b.velocidadMs
        val kmh =
            when {
                va != null && vb != null -> (va + vb) / 2 * KMH_POR_M_S
                else -> distancia / dt * KMH_POR_M_MS
            }
        return kmh >= MIN_MARCHA_KMH
    }
}
