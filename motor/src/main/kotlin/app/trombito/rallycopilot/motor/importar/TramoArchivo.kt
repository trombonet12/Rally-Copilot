package app.trombito.rallycopilot.motor.importar

import app.trombito.rallycopilot.motor.edicion.AccionEdicion
import app.trombito.rallycopilot.motor.geo.PuntoGeo

/**
 * Una edición de notas dentro de un archivo de tramo: [posicionM] son metros sobre la traza del archivo y
 * [marcaTiempoMs] ordena las ediciones (las posteriores se aplican después), igual que `NotasEditadas`.
 */
data class EdicionArchivo(
    val posicionM: Double,
    val accion: AccionEdicion,
    val marcaTiempoMs: Long,
)

/**
 * Lo propio de Rally Copilot que lleva un GPX exportado además de la traza: los cortes de [sectores] y [microsectores]
 * (metros desde la salida) y las [ediciones] de notas de Joan, que prevalecen sobre las automáticas. Se guardan las
 * ediciones y no las notas generadas: al importar, las automáticas se regeneran con los datos del móvil que importa.
 */
data class ExtensionRally(
    val sectores: List<Double>,
    val microsectores: List<Double>,
    val ediciones: List<EdicionArchivo>,
)

/** Una nota o aviso de la hoja del tramo como waypoint legible por otras apps: [frase] es lo que canta el copiloto. */
data class NotaArchivo(
    val punto: PuntoGeo,
    val posicionM: Double,
    val frase: String,
    val esAviso: Boolean,
)

/**
 * Un tramo listo para escribirse como GPX con [EscritorGpx]: traza, [notas] (la hoja ya fusionada con las ediciones,
 * como waypoints) y la extensión propia ([sectores], [microsectores], [ediciones]) para recuperarlo entero.
 */
data class TramoArchivo(
    val nombre: String,
    val traza: List<PuntoGeo>,
    val sectores: List<Double>,
    val microsectores: List<Double>,
    val notas: List<NotaArchivo>,
    val ediciones: List<EdicionArchivo>,
) {
    init {
        require(traza.size >= 2) { "Un tramo exportado necesita al menos dos puntos" }
    }
}
