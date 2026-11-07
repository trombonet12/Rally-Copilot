package app.trombito.rallycopilot.domain.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.importar.EdicionArchivo

/**
 * Lo que trae un GPX exportado por Rally Copilot: la [traza] tal cual (ya viene limpia y simplificada), los cortes y
 * las [ediciones] de notas, que se guardan ligadas al tramo nuevo.
 */
class TramoImportable(
    val traza: List<PuntoGeo>,
    val sectores: List<Double>,
    val microsectores: List<Double>,
    val ediciones: List<EdicionArchivo>,
)

/** Guarda un tramo importable (de un GPX de Rally Copilot) con sus cortes y sus notas editadas. */
fun interface GuardaTramoArchivo {
    /** [nombre] es el del usuario (null: queda el automático). */
    suspend fun guardar(
        tramo: TramoImportable,
        nombre: String?,
    ): ResultadoGuardado
}
