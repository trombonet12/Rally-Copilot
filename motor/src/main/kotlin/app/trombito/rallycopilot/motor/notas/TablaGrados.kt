package app.trombito.rallycopilot.motor.notas

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * Tabla radio → grado calibrable (serializable a JSON). La velocidad de curva es `v = √(μ·g·R)·factor`; los
 * [umbralesKmh] (5 valores crecientes) separan los grados 1..6. [factor] escala `v` para calibrar con la velocidad
 * real del piloto. Horquilla: ángulo ≥ [anguloHorquillaGrados] y radio central < [radioHorquillaM].
 */
@Serializable
data class TablaGrados(
    val mu: Double = 0.6,
    val g: Double = 9.81,
    val umbralesKmh: List<Double> = listOf(45.0, 65.0, 85.0, 110.0, 140.0),
    val anguloHorquillaGrados: Double = 140.0,
    val radioHorquillaM: Double = 30.0,
    val factor: Double = 1.0,
) {
    init {
        require(mu > 0.0 && g > 0.0 && factor > 0.0) { "mu, g y factor deben ser positivos" }
        require(umbralesKmh.size == 5 && umbralesKmh.zipWithNext().all { (a, b) -> a < b }) {
            "Se necesitan 5 umbrales crecientes: $umbralesKmh"
        }
    }

    /** Velocidad de curva en km/h para un radio en metros. */
    fun velocidadCurvaKmh(radioM: Double): Double = sqrt(mu * g * radioM) * 3.6 * factor

    /** Grado 1..6 del radio; si hay [vRefKmh] y la velocidad de curva la alcanza, manda el límite: grado 6. */
    fun grado(
        radioM: Double,
        vRefKmh: Double? = null,
    ): Int {
        val v = velocidadCurvaKmh(radioM)
        return if (vRefKmh != null && v >= vRefKmh) 6 else umbralesKmh.count { v >= it } + 1
    }

    /** `true` si el ángulo y el radio central describen una horquilla. */
    fun esHorquilla(
        anguloGrados: Double,
        radioCentralM: Double,
    ): Boolean = anguloGrados >= anguloHorquillaGrados && radioCentralM < radioHorquillaM
}
