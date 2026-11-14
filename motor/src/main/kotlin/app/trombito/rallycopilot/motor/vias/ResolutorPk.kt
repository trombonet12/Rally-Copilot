package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/** PK de los extremos de un trozo; [estimado] si sale de la distancia por el eje y no de hitos. */
internal class PkTramo(
    val desde: Double?,
    val hasta: Double?,
    val estimado: Boolean = false,
    /** Con menos diferencia entre extremos el sentido por PK no es fiable y manda el orden de dibujo. */
    val minDeltaKm: Double = TroceadorVias.MIN_DELTA_PK_KM,
)

/**
 * Da el PK de los extremos de un trozo de carretera: sobre su [EjeCarretera] si lo hay (con hitos calibrado, o
 * estimado) y, si no, el de los hitos más cercanos de [indicePk]; sin ninguno, null.
 */
internal class ResolutorPk(
    private val ejes: EjesCarreteras,
    private val indicePk: IndicePuntosKm?,
) {
    fun de(
        ref: String?,
        puntos: List<PuntoGeo>,
    ): PkTramo = enEje(ref?.let { ejes.de(it) }, puntos) ?: enHitos(ref, puntos)

    /** El primer y el último punto del trozo que caen cerca del [eje]. */
    private fun enEje(
        eje: EjeCarretera?,
        puntos: List<PuntoGeo>,
    ): PkTramo? {
        val desde = eje?.let { e -> puntos.firstNotNullOfOrNull { e.pk(it) } }
        val hasta = eje?.let { e -> puntos.asReversed().firstNotNullOfOrNull { e.pk(it) } }
        return if (eje == null || desde == null || hasta == null) {
            null
        } else {
            PkTramo(desde, hasta, !eje.deHitos, MIN_DELTA_EJE_KM)
        }
    }

    private fun enHitos(
        ref: String?,
        puntos: List<PuntoGeo>,
    ): PkTramo {
        if (indicePk == null || ref == null) return PkTramo(null, null)
        val primero = puntos.firstNotNullOfOrNull { indicePk.buscar(ref, it) }
        val ultimo = puntos.asReversed().firstNotNullOfOrNull { indicePk.buscar(ref, it) }
        return PkTramo(primero?.km, ultimo?.km)
    }

    private companion object {
        /** Con PK del eje basta con que los extremos del trozo difieran de verdad (un trozo mide al menos 1 km). */
        const val MIN_DELTA_EJE_KM = 1e-3
    }
}
