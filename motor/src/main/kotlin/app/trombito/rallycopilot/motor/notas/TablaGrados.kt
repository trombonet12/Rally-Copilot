package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.velocidad.kmhAMs
import app.trombito.rallycopilot.motor.velocidad.msAKmh
import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * Tabla de grados calibrable (serializable a JSON).
 *
 * Grado de una curva de las notas: depende solo del ángulo girado ([gradoPorAngulo]). Por debajo de
 * [anguloMinimoGrados] no hay nota; 10–30° es 6, 30–60° 5, 60–90° 4, 90–120° 3, 120–150° 2 y 150–180° 1 (en el
 * límite exacto gana el número mayor: 30° es 6, 60° es 5). Más de [anguloHorquillaGrados] (180°) es una horquilla,
 * sin grado.
 *
 * El radio ya no decide el grado de la nota, pero [grado] y [velocidadCurvaKmh] siguen sirviendo para «cierra» y
 * «abre» y para el perfil de velocidad. La velocidad de curva es `v = √(μ·g·R)·factor`.
 *
 * - [grado]: sin velocidad de referencia (sin datos de la vía), los [umbralesKmh] (5 valores crecientes) separan los
 *   grados 1..6 con la velocidad de curva absoluta.
 * - Con velocidad de referencia (límite de la vía o velocidad del piloto), manda la relación `r = v / vRef`: las
 *   [bandasRelativas] (5 valores crecientes: 0,45 / 0,60 / 0,72 / 0,86 / 1,0) separan los grados 1..6. Un grado 6
 *   exige además un radio de al menos `vRef² / (μConfort·g)` (con [muConfort] = 0,35, una curva que a vRef pide más
 *   de 0,35 g lateral no es «seis» aunque `v ≥ vRef`): si no, es un cinco.
 *
 * [factor] escala `v` para calibrar con la velocidad real del piloto.
 */
@Serializable
data class TablaGrados(
    val mu: Double = 0.6,
    val g: Double = 9.81,
    val umbralesKmh: List<Double> = listOf(45.0, 65.0, 85.0, 110.0, 140.0),
    val bandasRelativas: List<Double> = listOf(0.45, 0.60, 0.72, 0.86, 1.0),
    val muConfort: Double = 0.35,
    val limitesAnguloGrados: List<Double> = listOf(30.0, 60.0, 90.0, 120.0, 150.0),
    val anguloMinimoGrados: Double = 10.0,
    val anguloHorquillaGrados: Double = 180.0,
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
        require(limitesAnguloGrados.size == 5 && limitesAnguloGrados.zipWithNext().all { (a, b) -> a < b }) {
            "Se necesitan 5 límites de ángulo crecientes: $limitesAnguloGrados"
        }
    }

    /** Velocidad de curva en km/h para un radio en metros. */
    fun velocidadCurvaKmh(radioM: Double): Double = sqrt(mu * g * radioM).msAKmh() * factor

    /** Radio mínimo (m) para que una curva sea «seis» con la velocidad de referencia [vRefKmh]. */
    fun radioMinimoSeisM(vRefKmh: Double): Double {
        val ms = vRefKmh.kmhAMs()
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

    /**
     * Grado 1..6 del ángulo girado, o `null` si es menor de [anguloMinimoGrados] (no hay nota). En un límite exacto
     * gana el número mayor. No contempla la horquilla: ver [esHorquilla].
     */
    fun gradoPorAngulo(anguloGrados: Double): Int? =
        if (anguloGrados < anguloMinimoGrados) null else 6 - limitesAnguloGrados.count { anguloGrados > it }

    /** `true` si el ángulo supera [anguloHorquillaGrados]. */
    fun esHorquilla(anguloGrados: Double): Boolean = anguloGrados > anguloHorquillaGrados
}
