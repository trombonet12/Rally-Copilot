package app.trombito.rallycopilot.motor.ruta

/** Identidad de una vía para el histórico de ritmo: su ref (`Ma-13`), su nombre o un id; cualquier texto estable. */
@JvmInline
value class IdVia(
    val valor: String,
)

/** Ritmo real medido en una vía: [velocidadMediaKmh] en marcha, [pasadas] por ella y [km] recorridos en total. */
data class RitmoVia(
    val velocidadMediaKmh: Double,
    val pasadas: Int,
    val km: Double,
) {
    /** Hay histórico suficiente para fiarse: 2 pasadas o más, o 3 km recorridos o más. */
    val suficiente: Boolean get() = velocidadMediaKmh > 0.0 && (pasadas >= PASADAS_MIN || km >= KM_MIN)

    private companion object {
        const val PASADAS_MIN = 2
        const val KM_MIN = 3.0
    }
}

/** Viaje pasado: tiempo real y, si se conocía, el previsto (Valhalla) con el que se compara. */
data class ViajeHistorico(
    val realS: Double,
    val previstoS: Double?,
)

/**
 * Lo que la app sabe del ritmo de Joan: [vias] con su velocidad media real y el [factorPersonal] global (real/previsto)
 * que se aplica al tiempo de Valhalla donde no hay histórico de la vía.
 */
data class PerfilRitmo(
    val vias: Map<IdVia, RitmoVia> = emptyMap(),
    val factorPersonal: Double = FACTOR_PERSONAL_DEFECTO,
) {
    companion object {
        const val FACTOR_PERSONAL_DEFECTO = 1.12
        const val FACTOR_PERSONAL_MIN = 1.0
        const val FACTOR_PERSONAL_MAX = 1.4
        const val VIAJES_MIN = 3

        /**
         * Factor personal desde el histórico: mediana de real/previsto de los viajes con previsto conocido, acotada a
         * 1,0–1,4. Con menos de [VIAJES_MIN] viajes utilizables, el defecto.
         */
        fun factorDesdeHistorico(viajes: List<ViajeHistorico>): Double {
            val razones =
                viajes
                    .mapNotNull { v -> v.previstoS?.takeIf { it > 0.0 && v.realS > 0.0 }?.let { v.realS / it } }
                    .sorted()
            if (razones.size < VIAJES_MIN) return FACTOR_PERSONAL_DEFECTO
            val mitad = razones.size / 2
            val mediana = if (razones.size % 2 == 1) razones[mitad] else (razones[mitad - 1] + razones[mitad]) / 2
            return mediana.coerceIn(FACTOR_PERSONAL_MIN, FACTOR_PERSONAL_MAX)
        }
    }
}
