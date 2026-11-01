package app.trombito.rallycopilot.motor.analisis

import app.trombito.rallycopilot.motor.tramos.Pasada

/** Pasada guardada con su [id] y la fecha ([fechaMs], epoch ms) en que se hizo. */
data class PasadaRegistrada(
    val id: String,
    val pasada: Pasada,
    val fechaMs: Long,
)

/** Tramo guardado con su [distanciaM] cronometrada (hasta la meta) y todas sus [pasadas]. */
data class TramoRegistrado(
    val tramoId: String,
    val nombre: String,
    val distanciaM: Double,
    val pasadas: List<PasadaRegistrada>,
)

/** Trayecto conducido (con o sin tramo): [distanciaM] recorrida y [fechaMs] (epoch ms). */
data class RecorridoRegistrado(
    val fechaMs: Long,
    val distanciaM: Double,
)

/** Punto de la evolución de un tramo; [esRecord] si fue el mejor tiempo hasta ese momento. */
data class PuntoEvolucion(
    val pasadaId: String,
    val fechaMs: Long,
    val tiempoMs: Long,
    val esRecord: Boolean,
)

/** Resumen de un tramo: mejor tiempo, medias y [evolucion] por fecha creciente (vacía sin pasadas). */
data class ResumenTramo(
    val tramoId: String,
    val nombre: String,
    val distanciaM: Double,
    val numeroPasadas: Int,
    val mejorMs: Long?,
    val mejorPasadaId: String?,
    val mejorFechaMs: Long?,
    val mediaMs: Long?,
    val velMediaKmh: Double?,
    val velMejorKmh: Double?,
    val evolucion: List<PuntoEvolucion>,
)

/** Entrada del historial: una pasada con su diferencia contra el mejor tiempo de su tramo ([deltaMejorMs] ≥ 0). */
data class EntradaHistorial(
    val tramoId: String,
    val nombre: String,
    val pasadaId: String,
    val fechaMs: Long,
    val totalMs: Long,
    val deltaMejorMs: Long,
    val esRecord: Boolean,
)

/**
 * Perfil agregado del piloto: kilómetros conducidos, nº de tramos y de pasadas, mejores tiempos y evolución por tramo,
 * velocidad media global en tramos ([velMediaKmh]: metros totales de pasadas entre su tiempo total) e [historial]
 * reciente (más nueva primero).
 */
data class PerfilPiloto(
    val kmTotales: Double,
    val numeroTramos: Int,
    val numeroPasadas: Int,
    val tramos: List<ResumenTramo>,
    val velMediaKmh: Double?,
    val historial: List<EntradaHistorial>,
) {
    companion object {
        /** Entradas del historial por defecto. */
        const val HISTORIAL_MAX = 20

        private const val KMH_POR_M_MS = 3600.0
        private const val M_POR_KM = 1000.0

        /** Agrega [tramos] con sus pasadas y [recorridos] (km totales); [historialMax] limita el historial. */
        fun de(
            tramos: List<TramoRegistrado>,
            recorridos: List<RecorridoRegistrado>,
            historialMax: Int = HISTORIAL_MAX,
        ): PerfilPiloto {
            val resumenes = tramos.map { resumir(it) }
            val historial =
                tramos
                    .flatMap { t -> historialDe(t, resumenes.first { it.tramoId == t.tramoId }) }
                    .sortedByDescending { it.fechaMs }
                    .take(historialMax)
            val metros = tramos.sumOf { t -> t.pasadas.size * t.distanciaM }
            val tiempoMs = tramos.sumOf { t -> t.pasadas.sumOf { it.pasada.totalMs } }
            return PerfilPiloto(
                kmTotales = recorridos.sumOf { it.distanciaM } / M_POR_KM,
                numeroTramos = tramos.size,
                numeroPasadas = tramos.sumOf { it.pasadas.size },
                tramos = resumenes,
                velMediaKmh = if (tiempoMs > 0L) metros / tiempoMs * KMH_POR_M_MS else null,
                historial = historial,
            )
        }

        private fun resumir(tramo: TramoRegistrado): ResumenTramo {
            val cronologicas = tramo.pasadas.sortedBy { it.fechaMs }
            var mejorHastaAhora = Long.MAX_VALUE
            val evolucion =
                cronologicas.map {
                    val record = it.pasada.totalMs < mejorHastaAhora
                    mejorHastaAhora = minOf(mejorHastaAhora, it.pasada.totalMs)
                    PuntoEvolucion(it.id, it.fechaMs, it.pasada.totalMs, record)
                }
            // Empate de tiempos: gana la más antigua (la primera en lograrlo).
            val mejor = cronologicas.minByOrNull { it.pasada.totalMs }
            val media =
                if (cronologicas.isEmpty()) {
                    null
                } else {
                    Math.round(
                        cronologicas.map { it.pasada.totalMs }.average(),
                    )
                }
            return ResumenTramo(
                tramoId = tramo.tramoId,
                nombre = tramo.nombre,
                distanciaM = tramo.distanciaM,
                numeroPasadas = cronologicas.size,
                mejorMs = mejor?.pasada?.totalMs,
                mejorPasadaId = mejor?.id,
                mejorFechaMs = mejor?.fechaMs,
                mediaMs = media,
                velMediaKmh = media?.let { velocidad(tramo.distanciaM, it.toDouble()) },
                velMejorKmh = mejor?.let { velocidad(tramo.distanciaM, it.pasada.totalMs.toDouble()) },
                evolucion = evolucion,
            )
        }

        private fun velocidad(
            distanciaM: Double,
            tiempoMs: Double,
        ): Double? = if (tiempoMs > 0.0) distanciaM / tiempoMs * KMH_POR_M_MS else null

        private fun historialDe(
            tramo: TramoRegistrado,
            resumen: ResumenTramo,
        ): List<EntradaHistorial> {
            val records = resumen.evolucion.associate { it.pasadaId to it.esRecord }
            return tramo.pasadas.map {
                EntradaHistorial(
                    tramoId = tramo.tramoId,
                    nombre = tramo.nombre,
                    pasadaId = it.id,
                    fechaMs = it.fechaMs,
                    totalMs = it.pasada.totalMs,
                    deltaMejorMs = it.pasada.totalMs - (resumen.mejorMs ?: it.pasada.totalMs),
                    esRecord = records[it.id] == true,
                )
            }
        }
    }
}
