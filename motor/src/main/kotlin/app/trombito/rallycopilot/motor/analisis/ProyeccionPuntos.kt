package app.trombito.rallycopilot.motor.analisis

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.Cronometro
import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.motor.tramos.TrazaPlana
import app.trombito.rallycopilot.motor.velocidad.Fijacion

/**
 * Proyecta las fijaciones de una conducción sobre la traza del tramo con la misma proyección y ventana monótona que el
 * `Cronometro`, y las pone en tiempo relativo a la salida (cruce interpolado, como hace el cronómetro en vivo).
 */
internal object ProyeccionPuntos {
    /** Fijación proyectada: [tMs] desde la salida, [distanciaM] desde la salida (monótona) y velocidad si la trae. */
    class Proyectado(
        val tMs: Long,
        val punto: PuntoGeo,
        val distanciaM: Double,
        val velocidadMs: Double?,
    )

    private class Candidato(
        val fijacion: Fijacion,
        val progresoM: Double,
    )

    fun proyectar(
        tramo: Tramo,
        puntos: List<Fijacion>,
        pasada: Pasada,
    ): List<Proyectado> {
        val candidatos = candidatos(tramo, puntos)
        val primero = candidatos.indexOfFirst { it.progresoM >= 0.0 }
        return if (primero < 0) {
            emptyList()
        } else {
            val t0 = instanteDeSalida(candidatos, primero, pasada)
            candidatos
                .drop(primero)
                .takeWhile { it.progresoM <= tramo.metaM }
                .map { Proyectado(it.fijacion.tiempoMs - t0, it.fijacion.punto, it.progresoM, it.fijacion.velocidadMs) }
                .filter { it.tMs in 0L..pasada.totalMs }
        }
    }

    private fun candidatos(
        tramo: Tramo,
        puntos: List<Fijacion>,
    ): List<Candidato> {
        val traza = TrazaPlana(tramo.traza, tramo.longitudM)
        val resultado = mutableListOf<Candidato>()
        for (f in puntos) {
            val previo = resultado.lastOrNull()
            val ventana =
                if (previo == null) {
                    Double.NEGATIVE_INFINITY..VENTANA_INICIAL_M
                } else {
                    val dt = (f.tiempoMs - previo.fijacion.tiempoMs).coerceAtLeast(0L)
                    (previo.progresoM - ATRAS_M)..(
                        previo.progresoM +
                            maxOf(
                                ADELANTE_MIN_M,
                                VELOCIDAD_MAX_MS * dt / MS_POR_S,
                            )
                    )
                }
            val p = traza.proyectar(f.punto, ventana)
            if (p != null && p.lateralM <= Cronometro.LATERAL_MAX_M) {
                resultado += Candidato(f, if (previo == null) p.progresoM else maxOf(previo.progresoM, p.progresoM))
            }
        }
        return resultado
    }

    /** Instante (reloj de las fijaciones) del cruce de la salida, interpolado si hay una fijación previa. */
    private fun instanteDeSalida(
        candidatos: List<Candidato>,
        primero: Int,
        pasada: Pasada,
    ): Long {
        val b = candidatos[primero]
        return if (primero > 0) {
            val a = candidatos[primero - 1]
            val fraccion = -a.progresoM / (b.progresoM - a.progresoM)
            Math.round(a.fijacion.tiempoMs + fraccion * (b.fijacion.tiempoMs - a.fijacion.tiempoMs))
        } else {
            b.fijacion.tiempoMs - Math.round(pasada.tiempoEn(b.progresoM) ?: 0.0)
        }
    }

    private const val VENTANA_INICIAL_M = 150.0
    private const val ATRAS_M = 30.0
    private const val ADELANTE_MIN_M = 150.0
    private const val VELOCIDAD_MAX_MS = 70.0
    private const val MS_POR_S = 1000.0
}
