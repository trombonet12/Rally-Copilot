package app.trombito.rallycopilot.motor.ruta

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.DetectorCurvas
import app.trombito.rallycopilot.motor.notas.Remuestreo

/**
 * Cuánto «de curvas» es una ruta, para elegir entre las rutas candidatas de Valhalla (que no tiene coste de curvatura).
 *
 * - [curvasM]: metros de carretera «de curvas». Se detectan las curvas con [DetectorCurvas]; solo cuentan las de al
 *   menos [ANGULO_MIN_CURVA_GRADOS] de giro (grados 5 a 1 de las notas) y de radio central [RADIO_MAX_CURVA_M] m o
 *   menor (una curva abierta de varios km de radio no hace divertida una carretera). Las que quedan a [HUECO_MAX_M] m
 *   o menos una de otra se funden en un tramo de curvas, con las rectas cortas de en medio. Es la suma de la longitud
 *   de esos tramos: «12 km de curvas» en una ruta de 20 km significa que 12 de los 20 son un puerto o una carretera
 *   revirada.
 * - [giroTotalGrados]: suma del giro de todas las curvas detectadas (≥ 5°), sean abiertas o cerradas.
 * - [indiceSinuosidad]: giro total por kilómetro (grados/km); 0 en una recta, cientos en una carretera revirada.
 */
data class PuntuacionCurvas(
    val longitudM: Double,
    val curvasM: Double,
    val numeroCurvas: Int,
    val giroTotalGrados: Double,
) {
    val curvasKm: Double get() = curvasM / METROS_POR_KM

    val indiceSinuosidad: Double
        get() = if (longitudM <= 0.0) 0.0 else giroTotalGrados / (longitudM / METROS_POR_KM)

    companion object {
        const val ANGULO_MIN_CURVA_GRADOS = 30.0
        const val RADIO_MAX_CURVA_M = 300.0
        const val HUECO_MAX_M = 150.0
        private const val METROS_POR_KM = 1_000.0

        val VACIA = PuntuacionCurvas(0.0, 0.0, 0, 0.0)

        /** Mide la polilínea [puntos]; con menos de dos puntos distintos devuelve [VACIA]. Sin estado. */
        fun medir(
            puntos: List<PuntoGeo>,
            detector: DetectorCurvas = DetectorCurvas(),
        ): PuntuacionCurvas {
            val traza = Remuestreo.remuestrear(puntos)
            if (traza.size < 2) return VACIA
            val curvas = detector.detectar(traza)
            var tramosM = 0.0
            var inicio = Double.NaN
            var fin = Double.NaN
            val cerradas =
                curvas.filter {
                    it.anguloGrados >= ANGULO_MIN_CURVA_GRADOS &&
                        it.radioCentralM <= RADIO_MAX_CURVA_M
                }
            for (c in cerradas) {
                if (!inicio.isNaN() && c.inicioM - fin <= HUECO_MAX_M) {
                    fin = c.finM
                } else {
                    if (!inicio.isNaN()) tramosM += fin - inicio
                    inicio = c.inicioM
                    fin = c.finM
                }
            }
            if (!inicio.isNaN()) tramosM += fin - inicio
            return PuntuacionCurvas(
                longitudM = traza.s.last(),
                curvasM = tramosM,
                numeroCurvas = curvas.size,
                giroTotalGrados = curvas.sumOf { it.anguloGrados },
            )
        }
    }
}
