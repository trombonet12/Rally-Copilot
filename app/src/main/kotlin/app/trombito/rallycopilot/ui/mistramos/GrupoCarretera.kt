package app.trombito.rallycopilot.ui.mistramos

import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.motor.vias.SentidoVia
import java.util.Locale

/**
 * Los tramos de una misma carretera y sentido: [clave] es estable (`ma15|CRECIENTE`), [carretera] cómo se escribe
 * («Ma-15») y [destino] el lugar hacia el que se va más veces en el grupo (null si ninguno lo sabe).
 */
data class GrupoCarretera(
    val clave: String,
    val carretera: String,
    val sentido: SentidoVia,
    val destino: String?,
    val tramos: List<ResumenTramo>,
)

private const val PREFIJO_NOMBRE = "~"
private val PARTES_REF = Regex("^([^0-9]*)(\\d*)(.*)$")
private val ES = Locale.forLanguageTag("es-ES")

/**
 * Agrupa los tramos de vía por carretera y sentido. Los grupos van por carretera (letras, luego número: Ma-2 antes que
 * Ma-10) y sentido (creciente, decreciente, desconocido); dentro de cada uno, por kilómetro de salida (los que no
 * tienen, al final). Un tramo sin datos de vía va a un último grupo sin carretera (nombre vacío).
 */
fun agruparCarreteras(tramos: List<ResumenTramo>): List<GrupoCarretera> =
    tramos
        .groupBy { it.via?.viaRef.orEmpty() to (it.via?.sentido ?: SentidoVia.DESCONOCIDO) }
        .map { (clave, grupo) -> grupo.aGrupo(clave.first, clave.second) }
        .sortedWith(
            compareBy<GrupoCarretera> { it.carretera.isEmpty() }
                .thenComparator { a, b -> compararRefs(a.clave.substringBefore('|'), b.clave.substringBefore('|')) }
                .thenBy { it.sentido.ordinal },
        )

private fun List<ResumenTramo>.aGrupo(
    viaRef: String,
    sentido: SentidoVia,
): GrupoCarretera {
    val ordenados =
        sortedWith(
            compareBy<ResumenTramo> { it.via?.pkDesde == null }
                .thenBy { it.via?.pkDesde ?: 0.0 }
                .thenBy { it.longitudM }
                .thenBy { it.id },
        )
    val destino =
        mapNotNull {
            it.via
                ?.destino
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        }.groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    val carretera = firstNotNullOfOrNull { it.carretera } ?: refLegible(viaRef)
    return GrupoCarretera("$viaRef|${sentido.name}", carretera, sentido, destino, ordenados)
}

/** La ref guardada («MA15», o «~nombre» sin ref) lista para leerse cuando el tramo no trae la carretera escrita. */
private fun refLegible(viaRef: String): String =
    if (viaRef.startsWith(PREFIJO_NOMBRE)) {
        viaRef.removePrefix(PREFIJO_NOMBRE).split(' ').joinToString(" ") { palabra ->
            palabra.replaceFirstChar { it.titlecase(ES) }
        }
    } else {
        viaRef
    }

/** Orden natural de refs: letras, luego número («MA2» < «MA10») y por último el resto («MA13» < «MA13A»). */
private fun compararRefs(
    a: String,
    b: String,
): Int {
    val pa = PARTES_REF.matchEntire(a)?.destructured
    val pb = PARTES_REF.matchEntire(b)?.destructured
    if (pa == null || pb == null) return a.compareTo(b)
    val (letrasA, numA, restoA) = pa
    val (letrasB, numB, restoB) = pb
    return letrasA
        .compareTo(letrasB)
        .takeIf { it != 0 }
        ?: (numA.toIntOrNull() ?: 0).compareTo(numB.toIntOrNull() ?: 0).takeIf { it != 0 }
        ?: restoA.compareTo(restoB)
}
