package app.trombito.rallycopilot.data.resumen

import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.domain.tramos.SubtramoGuardado
import app.trombito.rallycopilot.domain.tramos.TipoTramo
import java.util.Locale

/**
 * Cómo se llama en el resumen del viaje el trozo [SubtramoGuardado] de un tramo. Si cubre el tramo entero (o casi), su
 * nombre. Si es un trozo (subtramo o pasada parcial), su rango de km: en un tramo de vía con hitos, los km de la
 * carretera («Ma-10 · km 12,3→15,8»); si no, los km del propio tramo («km 0,5→2,0 del tramo Sóller»).
 */
internal object NombreTramoHecho {
    private val ES: Locale = Locale.forLanguageTag("es-ES")
    private const val M_POR_KM = 1_000.0

    /** Lo que puede faltar de un extremo para seguir contando como tramo entero (ruido de la traza). */
    private const val TOLERANCIA_M = 50.0

    fun de(
        tramo: ResumenTramo,
        trozo: SubtramoGuardado,
    ): String {
        val entero = trozo.desdeM <= TOLERANCIA_M && trozo.hastaM >= tramo.longitudM - TOLERANCIA_M
        if (entero) return tramo.nombre
        val carretera = tramo.carretera?.takeIf { tramo.tipo == TipoTramo.VIA && kmDeCarretera(tramo, trozo) != null }
        val rango = kmDeCarretera(tramo, trozo) ?: "km ${km(trozo.desdeM)}→${km(trozo.hastaM)}"
        return carretera?.let { "$it · $rango" } ?: "$rango del tramo ${tramo.nombre}"
    }

    /** «km a→b» de la carretera si el tramo es de vía y tiene hitos; null si no. */
    private fun kmDeCarretera(
        tramo: ResumenTramo,
        trozo: SubtramoGuardado,
    ): String? {
        val pkSalida = tramo.via?.pkDesde
        val pkMeta = tramo.via?.pkHasta
        if (tramo.tipo != TipoTramo.VIA || pkSalida == null || pkMeta == null) return null
        // El km de la carretera crece o decrece a lo largo del tramo según vaya de salida a meta.
        val signo = if (pkMeta >= pkSalida) 1.0 else -1.0
        val a = pkSalida + signo * trozo.desdeM / M_POR_KM
        val b = pkSalida + signo * trozo.hastaM / M_POR_KM
        return "km ${formato(a)}→${formato(b)}"
    }

    /** Los km del propio tramo (metros desde su salida) con una décima y coma decimal. */
    private fun km(metros: Double) = formato(metros / M_POR_KM)

    private fun formato(km: Double) = String.format(ES, "%.1f", km.coerceAtLeast(0.0))
}
