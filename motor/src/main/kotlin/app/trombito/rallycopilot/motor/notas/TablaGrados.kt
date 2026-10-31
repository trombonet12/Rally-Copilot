package app.trombito.rallycopilot.motor.notas

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * Tabla radio → grado calibrable (serializable a JSON). La velocidad de curva es `v = √(μ·g·R)·factor`.
 *
 * - Sin velocidad de referencia (sin datos de la vía), los [umbralesKmh] (5 valores crecientes) separan los grados
 *   1..6 con la velocidad de curva absoluta.
 * - Con velocidad de referencia (límite de la vía o velocidad del piloto), manda la relación `r = v / vRef`: las
 *   [bandasRelativas] (5 valores crecientes: 0,45 / 0,60 / 0,72 / 0,86 / 1,0) separan los grados 1..6. Un grado 6
 *   exige además un radio de al menos `vRef² / (μConfort·g)` (con [muConfort] = 0,35, una curva que a vRef pide más
 *   de 0,35 g lateral no es «seis» aunque `v ≥ vRef`): si no, es un cinco.
 *
 * [factor] escala `v` para calibrar con la velocidad real del piloto. Horquilla: ángulo ≥ [anguloHorquillaGrados] y
 * radio central < [radioHorquillaM].
 */
@Serializable
data class TablaGrados(
    val mu: Double = 0.6,
    val g: Double = 9.81,
    val umbralesKmh: List<Double> = listOf(45.0, 65.0, 85.0, 110.0, 140.0),
    val bandasRelativas: List<Double> = listOf(0.45, 0.60, 0.72, 0.86, 1.0),
    val muConfort: Double = 0.35,
    val anguloHorquillaGrados: Double = 140.0,
    val radioHorquillaM: Double = 30.0,
    val factor: Double = 1.0,
) {
    init {
        require(mu > 0.0 && g > 0.0 && factor > 0.0 && muConfort > 0.0) {
            "mu, g, muConfort y factor deben ser positivos"
        }
        require(umbralesKmh.size == 5 && umbralesKmh.zipWithNext().all { (a, b) -> a < b }) {
            "Se necesitan 5 umbrales crecientes: $umbralesKmh"
        }
        require(bandasRelativas.size == 5 && bandasRelativas.zipWithNext().all { (a, b) -> a < b }) {
            "Se necesitan 5 bandas relativas crecientes: $bandasRelativas"
        }
    }

    /** Velocidad de curva en km/h para un radio en metros. */
    fun velocidadCurvaKmh(radioM: Double): Double = sqrt(mu * g * radioM) * 3.6 * factor

    /** Radio mínimo (m) para que una curva sea «seis» con la velocidad de referencia [vRefKmh]. */
    fun radioMinimoSeisM(vRefKmh: Double): Double {
        val ms = vRefKmh / KMH_POR_MS
        return ms * ms / (muConfort * g)
    }

    /**
     * Grado 1..6 del radio. Sin [vRefKmh] (o no positiva) se usan los umbrales absolutos; con ella, las bandas
     * relativas `v / vRef` y el tope de confort del grado 6.
     */
    fun grado(
        radioM: Double,
        vRefKmh: Double? = null,
    ): Int {
        val v = velocidadCurvaKmh(radioM)
        if (vRefKmh == null || vRefKmh <= 0.0) return umbralesKmh.count { v >= it } + 1
        val grado = bandasRelativas.count { v / vRefKmh >= it } + 1
        return if (grado == 6 && radioM < radioMinimoSeisM(vRefKmh)) 5 else grado
    }

    /** `true` si el ángulo y el radio central describen una horquilla. */
    fun esHorquilla(
        anguloGrados: Double,
        radioCentralM: Double,
    ): Boolean = anguloGrados >= anguloHorquillaGrados && radioCentralM < radioHorquillaM
}

private const val KMH_POR_MS = 3.6
