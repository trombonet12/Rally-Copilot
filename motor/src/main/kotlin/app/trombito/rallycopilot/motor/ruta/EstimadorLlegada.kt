package app.trombito.rallycopilot.motor.ruta

/** Tramo de la ruta a estimar: su vía (null si se desconoce), su longitud y el tiempo que da Valhalla. */
data class TramoEstimable(
    val via: IdVia?,
    val longitudM: Double,
    val tiempoValhallaS: Double,
)

/**
 * Hora de llegada realista. Por tramo: con histórico suficiente de la vía, longitud / velocidad media real (entre
 * 0,6× y 1,6× la de Valhalla); si no, tiempo de Valhalla × factor personal (1,0–1,4). Encima, la corrección en vivo
 * ([RitmoViaje.factor]) a lo que queda.
 */
class EstimadorLlegada(
    private val perfil: PerfilRitmo = PerfilRitmo(),
) {
    /** Tiempo previsto (s) del [tramo] según el perfil, sin corrección en vivo. */
    fun tiempoPrevistoS(tramo: TramoEstimable): Double {
        val ritmo = tramo.via?.let { perfil.vias[it] }?.takeIf { it.suficiente }
        val valhalla = tramo.tiempoValhallaS
        if (ritmo == null || valhalla <= 0.0 || tramo.longitudM <= 0.0) return valhalla * factorPersonal()
        val velocidadValhallaKmh = tramo.longitudM / valhalla * KMH_POR_MS
        val relacion = (ritmo.velocidadMediaKmh / velocidadValhallaKmh).coerceIn(RELACION_MIN, RELACION_MAX)
        return valhalla / relacion
    }

    /** Tiempo previsto (s) de todos los [tramos]. */
    fun tiempoPrevistoS(tramos: List<TramoEstimable>): Double = tramos.sumOf { tiempoPrevistoS(it) }

    /** Tiempo que queda (s) por los [tramos] pendientes, con la corrección en vivo de [ritmoViaje] si se da. */
    fun tiempoRestanteS(
        tramos: List<TramoEstimable>,
        ritmoViaje: RitmoViaje? = null,
    ): Double = tiempoPrevistoS(tramos) * (ritmoViaje?.factor ?: 1.0)

    /** Ajuste para [SeguidorRuta]: cada maniobra es un tramo, con la primera vía que declara como identidad. */
    fun comoAjuste(ritmoViaje: RitmoViaje? = null): AjusteTiempo =
        AjusteTiempo { maniobra, fraccion ->
            val tramo =
                TramoEstimable(
                    via = maniobra.vias.firstOrNull()?.let { IdVia(it) },
                    longitudM = maniobra.longitudM * fraccion,
                    tiempoValhallaS = maniobra.tiempoS * fraccion,
                )
            tiempoPrevistoS(tramo) * (ritmoViaje?.factor ?: 1.0)
        }

    private fun factorPersonal(): Double =
        perfil.factorPersonal.coerceIn(PerfilRitmo.FACTOR_PERSONAL_MIN, PerfilRitmo.FACTOR_PERSONAL_MAX)

    private companion object {
        const val KMH_POR_MS = 3.6
        const val RELACION_MIN = 0.6
        const val RELACION_MAX = 1.6
    }
}
