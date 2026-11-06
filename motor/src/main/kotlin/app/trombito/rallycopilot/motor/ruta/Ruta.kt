package app.trombito.rallycopilot.motor.ruta

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/**
 * Maniobra de una ruta. Se ejecuta en el punto [inicioIndice] de la polilínea y abarca hasta [finIndice]
 * (convención de Valhalla: la maniobra i termina donde empieza la i+1). [vias]: refs y nombres de la vía posterior.
 */
data class Maniobra(
    val tipo: TipoManiobra,
    val instruccionValhalla: String,
    val vias: List<String>,
    val inicioIndice: Int,
    val finIndice: Int,
    val longitudM: Double,
    val tiempoS: Double,
    /**
     * En una rotonda, número de la salida que se toma (1 = primera); null si no se ha podido calcular. Valhalla 3.9.1
     * devuelve siempre 2 en `roundabout_exit_count`, así que se ignora y lo calcula [CorrectorRotondas] con el grafo.
     */
    val salidaRotonda: Int?,
    /** En una rotonda fundida, índice del punto donde la ruta sale del anillo (donde empieza la maniobra de salida). */
    val salidaAnilloIndice: Int? = null,
)

/**
 * [indicesParadas]: índice en [puntos] de cada parada intermedia, en el orden del viaje (el final de cada tramo de
 * Valhalla salvo el último); vacío si la ruta va directa al destino.
 */
data class Ruta(
    val puntos: List<PuntoGeo>,
    val maniobras: List<Maniobra>,
    val longitudM: Double,
    val tiempoS: Double,
    val indicesParadas: List<Int> = emptyList(),
) {
    /**
     * Primera maniobra real tras la parada [n] (0 = la primera): la salida del tramo siguiente no cuenta. Null si no
     * hay parada [n] o no queda ninguna maniobra.
     */
    fun maniobraTrasParada(n: Int): Maniobra? {
        val indice = indicesParadas.getOrNull(n) ?: return null
        return maniobras.firstOrNull { it.inicioIndice > indice && it.tipo != TipoManiobra.SALIDA }
    }
}

/** La respuesta de Valhalla no es una ruta utilizable (JSON roto, error del servidor o datos incoherentes). */
class RutaInvalida(
    mensaje: String,
    causa: Throwable? = null,
) : Exception(mensaje, causa)
