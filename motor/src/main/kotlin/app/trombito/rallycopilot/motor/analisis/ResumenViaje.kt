package app.trombito.rallycopilot.motor.analisis

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.Via
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val MS_POR_S = 1_000.0
private const val S_POR_MIN = 60
private const val MIN_POR_H = 60
private const val KMH_POR_M_MS = 3_600.0
private const val KMH_POR_M_S = 3.6
private const val M_POR_KM = 1_000.0
private const val PORCENTAJE = 100.0

/** Un tramo cronometrado hecho en el viaje, ya comparado por el llamador con su mejor tiempo previo. */
data class TramoHecho(
    val nombre: String,
    val tiempoMs: Long,
    /** Diferencia con el mejor tiempo previo (negativa = más rápido); null si era la primera pasada. */
    val deltaMs: Long?,
    val esRecord: Boolean,
)

/**
 * Una carretera recorrida (las vías con la misma `ref`, o nombre si no la tienen). [velMediaHistoricaKmh] y
 * [diferenciaPct] son null sin histórico previo; [diferenciaPct] positivo = más rápido que tu media, y se calcula sobre
 * las mismas vías que tienen histórico (para comparar lo comparable).
 */
data class ViaRecorrida(
    val ref: String?,
    val nombre: String?,
    val metros: Double,
    val velMediaKmh: Double?,
    val velMediaHistoricaKmh: Double?,
    val diferenciaPct: Double?,
) {
    /** Cómo llamarla: la ref y, si no la tiene, el nombre. */
    val etiqueta: String? get() = ref ?: nombre
}

/** Llegada: lo [previstoS] (null si no había previsión) y [desviacionS] = real − previsto (negativa = antes). */
data class Llegada(
    val previstoS: Long?,
    val desviacionS: Long?,
)

/**
 * Resumen al terminar un recorrido (guiado o libre). Todo puro y determinista; ver [ResumenViaje.de].
 */
data class ResumenViaje(
    val distanciaM: Double,
    val tiempoTotalS: Long,
    /** Tiempo sin paradas (mismo criterio que [VelocidadVias]). */
    val tiempoEnMarchaS: Long,
    val mediaEnMarchaKmh: Double?,
    /** Máxima sostenida: sin picos aislados de GPS. */
    val velocidadMaxKmh: Double,
    val llegada: Llegada,
    /** Trozos de la traza con su velocidad, para pintar el mapa; coloreables con [escala]. */
    val segmentos: List<SegmentoMapa>,
    val escala: EscalaVelocidad,
    /** Carreteras recorridas, las de más metros primero. */
    val vias: List<ViaRecorrida>,
    /** Tramos hechos: récords primero, luego del que más mejoró al que más empeoró; sin comparar al final. */
    val tramos: List<TramoHecho>,
    val records: Int,
    /** De 1 a 3 frases cortas (vacío solo si no hay recorrido). */
    val conclusiones: List<String>,
) {
    companion object {
        /** Longitud objetivo (m) de cada segmento del mapa. */
        const val SEGMENTO_M = 100.0

        /** Velocidades por encima de esta se consideran error de GPS y no cuentan como máxima. */
        const val MAX_PLAUSIBLE_KMH = 250.0

        /**
         * Resumen del recorrido [puntos] (ordenados en el tiempo). [previstoS] es la duración prevista del viaje (null
         * si no la había). [coincidenciaDe] asigna cada trozo a una vía; [historicoVias] es lo acumulado ANTES de este
         * viaje por `via.id`. [tramos] llegan ya comparados.
         */
        fun de(
            puntos: List<PuntoTiempo>,
            previstoS: Long?,
            coincidenciaDe: VelocidadVias.CoincidenciaDe,
            historicoVias: Map<Long, VelocidadVia> = emptyMap(),
            tramos: List<TramoHecho> = emptyList(),
        ): ResumenViaje {
            val pasos = CalculoViaje.pasosDe(puntos)
            val enMarcha = pasos.filter { it.enMarcha }
            val marcha = Marcha(enMarcha.sumOf { it.metros }, enMarcha.sumOf { it.dtMs })
            val distanciaM = pasos.filter { !it.salto }.sumOf { it.metros }
            val totalMs = if (puntos.size >= 2) puntos.last().tiempoMs - puntos.first().tiempoMs else 0L
            val totalS = (totalMs / MS_POR_S).roundToLong()
            val segmentos = CalculoViaje.segmentosDe(pasos)
            val vias = CalculoViaje.viasRecorridas(VelocidadVias.analizar(puntos, coincidenciaDe), historicoVias)
            val ordenados = CalculoViaje.ordenar(tramos)
            val records = tramos.count { it.esRecord }
            val llegada = Llegada(previstoS, previstoS?.let { totalS - it })
            return ResumenViaje(
                distanciaM = distanciaM,
                tiempoTotalS = totalS,
                tiempoEnMarchaS = (marcha.tiempoMs / MS_POR_S).roundToLong(),
                mediaEnMarchaKmh = marcha.velMediaKmh,
                velocidadMaxKmh = CalculoViaje.velocidadMaxima(enMarcha.map { it.kmh }),
                llegada = llegada,
                segmentos = segmentos,
                escala = EscalaVelocidad.de(segmentos.map { it.velocidadKmh }),
                vias = vias,
                tramos = ordenados,
                records = records,
                conclusiones = FrasesViaje.conclusiones(distanciaM, marcha.velMediaKmh, llegada, vias, tramos),
            )
        }
    }
}

/** Cálculo de las partes numéricas del resumen. */
private object CalculoViaje {
    /** Ventana (pasos) de la mediana con la que se descartan picos aislados. */
    private const val VENTANA_PICOS = 5

    /** Clase de un paso entre dos puntos: salto de GPS (ni cuenta ni une segmentos), parado o en marcha. */
    enum class Tipo { SALTO, PARADO, MARCHA }

    class Paso(
        val desde: PuntoGeo,
        val hasta: PuntoGeo,
        val metros: Double,
        val dtMs: Long,
        val kmh: Double,
        val tipo: Tipo,
    ) {
        val salto: Boolean get() = tipo == Tipo.SALTO
        val enMarcha: Boolean get() = tipo == Tipo.MARCHA
    }

    fun pasosDe(puntos: List<PuntoTiempo>): List<Paso> =
        puntos.zipWithNext().map { (a, b) ->
            val metros = a.punto.distanciaA(b.punto)
            val dt = b.tiempoMs - a.tiempoMs
            val salto = metros > AgregadoCarreteras.MAX_SALTO_M
            val va = a.velocidadMs
            val vb = b.velocidadMs
            val kmh =
                when {
                    dt <= 0L -> 0.0
                    va != null && vb != null -> (va + vb) / 2 * KMH_POR_M_S
                    else -> metros / dt * KMH_POR_M_MS
                }
            // Mismo criterio que VelocidadVias: sin salto, con tiempo, sin hueco largo y sin estar parado.
            val enMarcha = !salto && dt in 1L..VelocidadVias.MAX_HUECO_MS && kmh >= VelocidadVias.MIN_MARCHA_KMH
            val tipo =
                when {
                    salto -> Tipo.SALTO
                    enMarcha -> Tipo.MARCHA
                    else -> Tipo.PARADO
                }
            Paso(a.punto, b.punto, metros, dt, kmh, tipo)
        }

    /** Máxima sostenida: mediana móvil de [VENTANA_PICOS] pasos (mata picos aislados) tras descartar lo imposible. */
    fun velocidadMaxima(velocidadesKmh: List<Double>): Double {
        val v = velocidadesKmh.filter { it <= ResumenViaje.MAX_PLAUSIBLE_KMH }
        val radio = VENTANA_PICOS / 2
        return v.indices.maxOfOrNull { i ->
            val ventana = v.subList((i - radio).coerceAtLeast(0), (i + radio + 1).coerceAtMost(v.size)).sorted()
            ventana[(ventana.size - 1) / 2]
        } ?: 0.0
    }

    private class Acumulador {
        val puntos = ArrayList<PuntoGeo>()
        var metros = 0.0
        var marcha = Marcha()
    }

    fun segmentosDe(pasos: List<Paso>): List<SegmentoMapa> {
        val resultado = ArrayList<SegmentoMapa>()
        var actual = Acumulador()
        var desdeM = 0.0

        fun cerrar() {
            val vel = actual.marcha.velMediaKmh
            if (actual.puntos.size >= 2 && vel != null) {
                resultado += SegmentoMapa(actual.puntos.toList(), desdeM, desdeM + actual.metros, vel)
            }
            desdeM += actual.metros
            actual = Acumulador()
        }
        for (paso in pasos) {
            if (paso.salto) {
                cerrar()
                continue
            }
            if (actual.puntos.isEmpty()) actual.puntos += paso.desde
            actual.puntos += paso.hasta
            actual.metros += paso.metros
            if (paso.enMarcha) actual.marcha += Marcha(paso.metros, paso.dtMs)
            if (actual.metros >= ResumenViaje.SEGMENTO_M) {
                val ultimo = paso.hasta
                cerrar()
                actual.puntos += ultimo
            }
        }
        cerrar()
        return resultado
    }

    fun ordenar(tramos: List<TramoHecho>): List<TramoHecho> =
        tramos.sortedWith(
            compareByDescending<TramoHecho> { it.esRecord }
                .thenBy { it.deltaMs == null }
                .thenBy { it.deltaMs ?: 0L }
                .thenBy { it.nombre },
        )

    private fun claveCarretera(via: Via): String? {
        val ref = via.ref?.trim()?.takeIf { it.isNotEmpty() }
        val nombre = via.nombre?.trim()?.takeIf { it.isNotEmpty() }
        return ref?.let { "ref:${it.uppercase()}" } ?: nombre?.let { "nombre:${it.lowercase()}" }
    }

    fun viasRecorridas(
        analisis: AnalisisMarcha,
        historico: Map<Long, VelocidadVia>,
    ): List<ViaRecorrida> =
        analisis.usos
            .mapNotNull { uso -> claveCarretera(uso.via)?.let { it to uso } }
            .groupBy({ it.first }, { it.second })
            .values
            .map { grupo -> viaRecorrida(grupo, historico) }
            .sortedWith(compareByDescending<ViaRecorrida> { it.metros }.thenBy { it.etiqueta ?: "" })

    private fun viaRecorrida(
        grupo: List<UsoViaMarcha>,
        historico: Map<Long, VelocidadVia>,
    ): ViaRecorrida {
        val principal = grupo.maxBy { it.total.metros }.via
        val total = grupo.fold(Marcha()) { acc, uso -> acc + uso.total }
        val conHistorico = grupo.filter { (historico[it.via.id]?.total?.tiempoMs ?: 0L) > 0L }
        val previa = conHistorico.fold(Marcha()) { acc, uso -> acc + historico.getValue(uso.via.id).total }
        val propia = conHistorico.fold(Marcha()) { acc, uso -> acc + uso.total }
        val velPrevia = previa.velMediaKmh
        val velPropia = propia.velMediaKmh
        val diferencia =
            if (velPrevia != null && velPrevia > 0.0 && velPropia != null) {
                (velPropia - velPrevia) / velPrevia * PORCENTAJE
            } else {
                null
            }
        return ViaRecorrida(
            ref = principal.ref?.trim()?.takeIf { it.isNotEmpty() },
            nombre = principal.nombre?.trim()?.takeIf { it.isNotEmpty() },
            metros = total.metros,
            velMediaKmh = total.velMediaKmh,
            velMediaHistoricaKmh = velPrevia,
            diferenciaPct = diferencia,
        )
    }
}

/** Frases de conclusión por reglas deterministas. */
private object FrasesViaje {
    private const val MIN_METROS_CONCLUSION = 500.0
    private const val MIN_DIFERENCIA_PCT = 3.0
    private const val MAX_CONCLUSIONES = 3

    fun conclusiones(
        distanciaM: Double,
        mediaKmh: Double?,
        llegada: Llegada,
        vias: List<ViaRecorrida>,
        tramos: List<TramoHecho>,
    ): List<String> {
        val frases = ArrayList<String>()
        frasesRecords(tramos)?.let { frases += it }
        llegada.desviacionS?.let { frases += fraseLlegada(it) }
        frases +=
            vias
                .filter {
                    it.metros >= MIN_METROS_CONCLUSION &&
                        it.etiqueta != null &&
                        abs(it.diferenciaPct ?: 0.0).roundToInt() >= MIN_DIFERENCIA_PCT
                }.sortedByDescending { abs(it.diferenciaPct ?: 0.0) }
                .map { fraseVia(it) }
        if (frases.isEmpty() && distanciaM > 0.0) {
            frases += "Recorrido de ${decimal(distanciaM / M_POR_KM)} km" +
                (mediaKmh?.let { " a ${it.roundToInt()} km/h de media" } ?: "")
        }
        return frases.take(MAX_CONCLUSIONES)
    }

    private fun frasesRecords(tramos: List<TramoHecho>): String? {
        val records = tramos.filter { it.esRecord }
        return when (records.size) {
            0 -> null
            1 -> "Récord nuevo en ${records.single().nombre}"
            else -> "${records.size} récords nuevos"
        }
    }

    private fun fraseLlegada(desviacionS: Long): String {
        val minutos = (abs(desviacionS).toDouble() / S_POR_MIN).roundToInt()
        val duracion =
            if (minutos < MIN_POR_H) {
                "$minutos min"
            } else {
                "${minutos / MIN_POR_H} h ${(minutos % MIN_POR_H).toString().padStart(2, '0')} min"
            }
        return when {
            minutos == 0 -> "Llegaste a la hora prevista"
            desviacionS < 0 -> "Llegaste $duracion antes de lo previsto"
            else -> "Llegaste $duracion más tarde de lo previsto"
        }
    }

    private fun fraseVia(via: ViaRecorrida): String {
        val pct = via.diferenciaPct ?: 0.0
        val comparacion = if (pct > 0) "más rápido" else "más lento"
        return "${via.etiqueta}: ${abs(pct).roundToInt()} % $comparacion que tu media"
    }

    private fun decimal(valor: Double): String = String.format(Locale.ROOT, "%.1f", valor).replace('.', ',')
}
