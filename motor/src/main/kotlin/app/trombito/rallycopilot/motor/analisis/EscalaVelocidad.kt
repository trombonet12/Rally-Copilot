package app.trombito.rallycopilot.motor.analisis

/** Posición en la escala cuando todos los cortes valen lo mismo: el centro. */
private const val FRACCION_ESCALA_PLANA = 0.5

/**
 * Escala de color por cuantiles de la velocidad de una pasada: [cortesKmh] son las velocidades que dejan por debajo las
 * [PROBABILIDADES] de los segmentos (mínimo, p10, p25, mediana, p75, p90, máximo). Así unos pocos picos o paradas no
 * aplastan el degradado: [fraccion] da 0 (la más lenta) a 1 (la más rápida) por rango de percentil.
 */
data class EscalaVelocidad(
    val cortesKmh: List<Double>,
) {
    /** Posición 0..1 de [kmh] en la escala (0,5 si todos los segmentos van igual). */
    fun fraccion(kmh: Double): Double {
        val minimo = cortesKmh.first()
        val maximo = cortesKmh.last()
        return when {
            maximo <= minimo -> {
                FRACCION_ESCALA_PLANA
            }

            kmh <= minimo -> {
                0.0
            }

            kmh >= maximo -> {
                1.0
            }

            else -> {
                val j = cortesKmh.indexOfLast { it <= kmh }.coerceAtMost(cortesKmh.size - 2)
                val ancho = cortesKmh[j + 1] - cortesKmh[j]
                val dentro = if (ancho > 0.0) (kmh - cortesKmh[j]) / ancho else 0.0
                PROBABILIDADES[j] + dentro * (PROBABILIDADES[j + 1] - PROBABILIDADES[j])
            }
        }
    }

    companion object {
        /** Probabilidades de los cortes. */
        val PROBABILIDADES = listOf(0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 1.0)

        /** Escala de las [velocidadesKmh] dadas (con un solo valor o ninguno, plana). */
        fun de(velocidadesKmh: List<Double>): EscalaVelocidad {
            val orden = velocidadesKmh.sorted()
            return EscalaVelocidad(
                PROBABILIDADES.map { p ->
                    if (orden.isEmpty()) 0.0 else cuantil(orden, p)
                },
            )
        }

        private fun cuantil(
            orden: List<Double>,
            p: Double,
        ): Double {
            val posicion = p * (orden.size - 1)
            val i = posicion.toInt().coerceAtMost(orden.size - 1)
            val j = (i + 1).coerceAtMost(orden.size - 1)
            return orden[i] + (posicion - i) * (orden[j] - orden[i])
        }
    }
}
