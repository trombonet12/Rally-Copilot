package app.trombito.rallycopilot.motor.analisis

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.Delta
import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.velocidad.msAKmh

/** Suma de los pesos 1-2-1 con que se suaviza la velocidad. */
private const val SUMA_PESOS_SUAVIZADO = 4.0

/** Valor [valor] en la distancia [distanciaM] (m desde la salida): velocidad en km/h o delta en ms. */
data class PuntoSerie(
    val distanciaM: Double,
    val valor: Double,
)

/**
 * Un sector o microsector analizado. [tiempoMs] sale de la propia [Pasada] (mismas marcas e interpolación que el
 * cronometraje en vivo). Los campos de comparación son null sin pasada comparada: [diferenciaMs] negativa = más rápido
 * en este parcial, [deltaAcumuladoMs] = diferencia acumulada al final del parcial.
 */
data class ParcialAnalisis(
    val numero: Int,
    val desdeM: Double,
    val hastaM: Double,
    val tiempoMs: Long,
    val acumuladoMs: Long,
    val velMediaKmh: Double,
    val velMaxKmh: Double,
    val comparadaMs: Long?,
    val diferenciaMs: Long?,
    val deltaAcumuladoMs: Long?,
)

/** Trozo de traza ([puntos], con sus vértices) con su velocidad media, para pintar el mapa por velocidad. */
data class SegmentoMapa(
    val puntos: List<PuntoGeo>,
    val desdeM: Double,
    val hastaM: Double,
    val velocidadKmh: Double,
)

/** Posición del coche a [tMs] ms de la salida, a [distanciaM] de la salida; [velocidadKmh] si se conoce. */
data class PosicionRepeticion(
    val tMs: Long,
    val punto: PuntoGeo,
    val distanciaM: Double,
    val velocidadKmh: Double?,
)

/** Posiciones en el tiempo (ordenadas por [PosicionRepeticion.tMs]) para repetir el recorrido sobre el mapa. */
data class Repeticion(
    val posiciones: List<PosicionRepeticion>,
    val duracionMs: Long,
) {
    /** Posición interpolada a [tMs] (acotada a la duración); null si no hay posiciones. */
    fun en(tMs: Long): PosicionRepeticion? {
        if (posiciones.isEmpty()) return null
        val t = tMs.coerceIn(posiciones.first().tMs, posiciones.last().tMs)
        val i = posiciones.indexOfFirst { it.tMs >= t }
        val b = posiciones[i]
        val a = if (i > 0) posiciones[i - 1] else b
        val ancho = b.tMs - a.tMs
        return if (ancho <= 0L) {
            b
        } else {
            val f = (t - a.tMs).toDouble() / ancho
            PosicionRepeticion(
                t,
                PuntoGeo(a.punto.lat + f * (b.punto.lat - a.punto.lat), a.punto.lon + f * (b.punto.lon - a.punto.lon)),
                a.distanciaM + f * (b.distanciaM - a.distanciaM),
                if (a.velocidadKmh != null && b.velocidadKmh != null) {
                    a.velocidadKmh + f * (b.velocidadKmh - a.velocidadKmh)
                } else {
                    null
                },
            )
        }
    }
}

/**
 * Análisis de una pasada por un tramo: tiempos y velocidades por sector y microsector, delta contra una pasada
 * comparada, series velocidad/distancia y delta/distancia (una muestra cada [PASO_SERIE_M]), segmentos coloreables
 * del mapa con su [escala] y la [repeticion] del recorrido.
 */
data class AnalisisPasada(
    val tramoId: String,
    val totalMs: Long,
    val deltaTotalMs: Long?,
    val sectores: List<ParcialAnalisis>,
    val microsectores: List<ParcialAnalisis>,
    val velocidad: List<PuntoSerie>,
    val delta: List<PuntoSerie>,
    val segmentos: List<SegmentoMapa>,
    val escala: EscalaVelocidad,
    val repeticion: Repeticion,
) {
    companion object {
        /** Separación (m) entre muestras de las series. */
        const val PASO_SERIE_M = 25.0

        /**
         * Analiza [pasada] sobre [tramo] con las fijaciones [puntos] de la conducción dentro de ella (proyectadas sobre
         * la traza con la misma ventana monótona que el `Cronometro`). Los tiempos, cortes y delta salen de las marcas
         * de la pasada, por lo que coinciden con el cronometraje en vivo; los puntos aportan posiciones reales y
         * velocidad instantánea. [comparada] (otra pasada del mismo tramo) activa los deltas.
         */
        fun de(
            tramo: Tramo,
            puntos: List<Fijacion>,
            pasada: Pasada,
            comparada: Pasada?,
        ): AnalisisPasada {
            require(pasada.tiempos.size >= 2) { "La pasada no tiene marcas" }
            val meta = tramo.metaM
            val muestras = distanciasDeMuestreo(meta)
            val velocidades = velocidadesKmh(muestras, pasada)
            val proyectados = ProyeccionPuntos.proyectar(tramo, puntos, pasada)
            val linea = LineaTramo(tramo.traza, tramo.longitudM)
            val segmentos = segmentos(muestras, velocidades, linea)
            return AnalisisPasada(
                tramoId = tramo.id,
                totalMs = pasada.totalMs,
                deltaTotalMs = comparada?.let { Delta.enDistancia(pasada, it, meta) },
                sectores = parciales(tramo.sectores, meta, pasada, comparada, muestras, velocidades, proyectados),
                microsectores =
                    parciales(
                        tramo.microsectores,
                        meta,
                        pasada,
                        comparada,
                        muestras,
                        velocidades,
                        proyectados,
                    ),
                velocidad = muestras.zip(velocidades) { d, v -> PuntoSerie(d, v) },
                delta = serieDelta(muestras, pasada, comparada),
                segmentos = segmentos,
                escala = EscalaVelocidad.de(segmentos.map { it.velocidadKmh }),
                repeticion = repeticion(proyectados, pasada, linea, meta),
            )
        }

        private fun distanciasDeMuestreo(meta: Double): List<Double> {
            val pasos = generateSequence(0.0) { it + PASO_SERIE_M }.takeWhile { it < meta - 1.0 }.toList()
            return pasos + meta
        }

        private fun tiempo(
            pasada: Pasada,
            distanciaM: Double,
        ): Double {
            val d = distanciaM.coerceIn(pasada.tiempos.first().distanciaM, pasada.tiempos.last().distanciaM)
            return pasada.tiempoEn(d) ?: 0.0
        }

        /** Velocidad (km/h) en cada muestra: diferencia centrada de la curva distancia-tiempo y suavizado [1, 2, 1]. */
        private fun velocidadesKmh(
            muestras: List<Double>,
            pasada: Pasada,
        ): List<Double> {
            val t = muestras.map { tiempo(pasada, it) }
            val crudas =
                muestras.indices.map { i ->
                    val a = (i - 1).coerceAtLeast(0)
                    val b = (i + 1).coerceAtMost(muestras.lastIndex)
                    val dt = t[b] - t[a]
                    if (dt > 0.0) (muestras[b] - muestras[a]) / dt * KMH_POR_M_MS else 0.0
                }
            return crudas.indices.map { i ->
                if (i == 0 ||
                    i == crudas.lastIndex
                ) {
                    crudas[i]
                } else {
                    (crudas[i - 1] + 2 * crudas[i] + crudas[i + 1]) / SUMA_PESOS_SUAVIZADO
                }
            }
        }

        private fun serieDelta(
            muestras: List<Double>,
            pasada: Pasada,
            comparada: Pasada?,
        ): List<PuntoSerie> =
            if (comparada == null) {
                emptyList()
            } else {
                muestras.mapNotNull { d ->
                    Delta.enDistancia(pasada, comparada, d)?.let { PuntoSerie(d, it.toDouble()) }
                }
            }

        private fun segmentos(
            muestras: List<Double>,
            velocidades: List<Double>,
            linea: LineaTramo,
        ): List<SegmentoMapa> =
            muestras.zipWithNext().mapIndexed { i, (desde, hasta) ->
                SegmentoMapa(linea.entre(desde, hasta), desde, hasta, (velocidades[i] + velocidades[i + 1]) / 2.0)
            }

        @Suppress("LongParameterList")
        private fun parciales(
            cortes: List<Double>,
            meta: Double,
            pasada: Pasada,
            comparada: Pasada?,
            muestras: List<Double>,
            velocidades: List<Double>,
            proyectados: List<ProyeccionPuntos.Proyectado>,
        ): List<ParcialAnalisis> {
            val contra =
                comparada
                    ?.let {
                        Delta.parciales(
                            cortes,
                            meta,
                            pasada,
                            it,
                        )
                    }.orEmpty()
                    .associateBy { it.numero }
            return (listOf(0.0) + cortes + meta).zipWithNext().mapIndexed { i, (desde, hasta) ->
                val ta = tiempo(pasada, desde)
                val tb = tiempo(pasada, hasta)
                val tiempoMs = Math.round(tb - ta)
                val enSector = { d: Double -> d >= desde && d <= hasta }
                val instantaneas = proyectados.filter { enSector(it.distanciaM) }.mapNotNull { it.velocidadMs }
                val media = if (tb > ta) (hasta - desde) / (tb - ta) * KMH_POR_M_MS else 0.0
                val maxima =
                    (
                        velocidades.filterIndexed { k, _ -> enSector(muestras[k]) } +
                            instantaneas.map { it.msAKmh() }
                    ).maxOrNull()
                val parcial = contra[i + 1]
                ParcialAnalisis(
                    numero = i + 1,
                    desdeM = desde,
                    hastaM = hasta,
                    tiempoMs = tiempoMs,
                    acumuladoMs = Math.round(tb),
                    velMediaKmh = media,
                    velMaxKmh = maxOf(media, maxima ?: media),
                    comparadaMs = parcial?.mejorMs,
                    diferenciaMs = parcial?.diferenciaMs,
                    deltaAcumuladoMs = parcial?.deltaAcumuladoMs,
                )
            }
        }

        private fun repeticion(
            proyectados: List<ProyeccionPuntos.Proyectado>,
            pasada: Pasada,
            linea: LineaTramo,
            meta: Double,
        ): Repeticion {
            val total = pasada.totalMs
            val reales =
                proyectados.map {
                    PosicionRepeticion(it.tMs, it.punto, it.distanciaM, it.velocidadMs?.let { v -> v.msAKmh() })
                }
            val base =
                if (reales.isEmpty()) {
                    pasada.tiempos.map { PosicionRepeticion(it.tMs, linea.puntoEn(it.distanciaM), it.distanciaM, null) }
                } else {
                    reales
                }
            val inicio =
                if (base.first().tMs >
                    0L
                ) {
                    listOf(PosicionRepeticion(0L, linea.puntoEn(0.0), 0.0, null))
                } else {
                    emptyList()
                }
            val fin =
                if (base.last().tMs <
                    total
                ) {
                    listOf(PosicionRepeticion(total, linea.puntoEn(meta), meta, null))
                } else {
                    emptyList()
                }
            return Repeticion(inicio + base + fin, total)
        }

        private const val KMH_POR_M_MS = 3600.0
    }
}
