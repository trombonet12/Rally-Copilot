package app.trombito.rallycopilot.domain.alertas

import app.trombito.rallycopilot.motor.alertas.IncidenciaCercana
import app.trombito.rallycopilot.motor.alertas.TipoRadar
import kotlinx.coroutines.flow.StateFlow

/**
 * El radar más cercano por delante que ya se avisa (dentro de su ventana de aviso). [id] es el del radar de los datos
 * y [sentidoContrario] que es un fijo que vigila el otro sentido (se avisa «por si acaso»).
 */
data class RadarProximo(
    val tipo: TipoRadar,
    val distanciaM: Double,
    val limiteKmh: Int?,
    val id: String = "",
    val sentidoContrario: Boolean = false,
)

/** Se acaba de pasar por el radar fijo [radarId] a [velocidadKmh]; [limiteKmh] es lo que controla (si se sabe). */
data class PasoRadarEnCurso(
    val radarId: String,
    val velocidadKmh: Double,
    val limiteKmh: Int?,
)

/**
 * Tramo de radar en el que se está (o que se acaba de terminar: [terminado], unos segundos). [mediaKmh] es `null`
 * hasta recorrer unos 100 m; [superaLimite] pide la media en rojo. [id] es el `tramo_id` del radar de tramo.
 */
data class TramoActivo(
    val mediaKmh: Double?,
    val limiteKmh: Int?,
    val superaLimite: Boolean,
    val terminado: Boolean,
    val id: String? = null,
)

/** Velocidad frente al límite de la vía actual: [rojo] pasa del límite, [exceso] pasa del límite más un 10 %. */
data class ExcesoEnCurso(
    val limiteKmh: Int?,
    val rojo: Boolean,
    val exceso: Boolean,
)

/**
 * Alertas de la conducción en curso para la UI: [radarProximo], [tramo], [exceso], las [incidencias] relevantes (las de
 * la ruta primero, luego las del radio de 3 km del coche; para pintarlas en el mapa) y la antigüedad de los datos de
 * incidencias ([antiguedadIncidenciasMs] es `null` si nunca se obtuvieron; [errorIncidencias] si el último refresco
 * falló y se usa la copia). Los radares vienen del paquete de datos y no tienen antigüedad propia. [pasoRadar] es el
 * último radar fijo cruzado, que se mantiene unos segundos para enseñar la velocidad de paso.
 */
data class AlertasEnCurso(
    val radarProximo: RadarProximo? = null,
    val tramo: TramoActivo? = null,
    val exceso: ExcesoEnCurso = ExcesoEnCurso(limiteKmh = null, rojo = false, exceso = false),
    val incidencias: List<IncidenciaCercana> = emptyList(),
    val antiguedadIncidenciasMs: Long? = null,
    val errorIncidencias: ErrorIncidencias? = null,
    val pasoRadar: PasoRadarEnCurso? = null,
)

/** Alertas en vivo de la sesión de navegación; null si no hay conducción en curso. */
interface ProveedorAlertas {
    val alertas: StateFlow<AlertasEnCurso?>
}
