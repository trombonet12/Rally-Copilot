package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.ceil
import kotlin.math.sqrt

/** Punto de un recorrido grabado con la velocidad (m/s) medida en él, si la hay. */
data class MuestraVelocidad(
    val punto: PuntoGeo,
    val vMs: Double?,
)

/** Resultado de calibrar: [factor] para [TablaGrados.factor] (`null` si no hay datos bastantes) y [curvas] usadas. */
data class ResultadoCalibracion(
    val factor: Double?,
    val curvas: Int,
)

/**
 * Calibración global con la velocidad real del piloto. En cada curva de cada recorrido toma la velocidad mínima de
 * su 50 % central y la compara con la teórica `√(μ·g·R_central)`; el factor es el percentil 85 de los cocientes,
 * acotado a [[FACTOR_MIN], [FACTOR_MAX]]. Con menos de [CURVAS_MIN] curvas válidas devuelve `null` (se usa el
 * límite de la vía). Determinista.
 */
object PerfilVelocidad {
    const val CURVAS_MIN = 50
    const val FACTOR_MIN = 0.6
    const val FACTOR_MAX = 1.6
    const val V_MIN_MS = 3.0
    const val PERCENTIL = 0.85

    /** Calibra [tabla] (solo se usan `mu` y `g`) con los [recorridos] grabados. */
    fun calibrar(
        recorridos: List<List<MuestraVelocidad>>,
        tabla: TablaGrados,
    ): ResultadoCalibracion {
        val cocientes = recorridos.flatMap { cocientesDe(it, tabla) }
        if (cocientes.size < CURVAS_MIN) return ResultadoCalibracion(null, cocientes.size)
        val ordenados = cocientes.sorted()
        val rango = ceil(PERCENTIL * ordenados.size).toInt().coerceIn(1, ordenados.size)
        return ResultadoCalibracion(ordenados[rango - 1].coerceIn(FACTOR_MIN, FACTOR_MAX), cocientes.size)
    }

    /** Cociente velocidad real / teórica de cada curva válida del recorrido. */
    private fun cocientesDe(
        recorrido: List<MuestraVelocidad>,
        tabla: TablaGrados,
    ): List<Double> {
        val puntos = recorrido.map { it.punto }
        if (puntos.size < 2) return emptyList()
        val acumulada = DoubleArray(puntos.size)
        for (i in 1 until puntos.size) acumulada[i] = acumulada[i - 1] + puntos[i - 1].distanciaA(puntos[i])
        return DetectorCurvas().detectar(puntos).mapNotNull { curva ->
            val largo = curva.finM - curva.inicioM
            val desde = curva.inicioM + largo * CUARTO
            val hasta = curva.finM - largo * CUARTO
            val v =
                recorrido
                    .filterIndexed { i, _ -> acumulada[i] in desde..hasta }
                    .mapNotNull { it.vMs }
                    .minOrNull()
            if (v == null || v < V_MIN_MS) null else v / sqrt(tabla.mu * tabla.g * curva.radioCentralM)
        }
    }

    private const val CUARTO = 0.25
}
