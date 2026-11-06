package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.motor.geo.PuntoGeo

/**
 * Puntos del recorrido en curso para pintar la estela de por dónde se ha pasado. Solo guarda un punto si dista al menos
 * [pasoMinimoM] del último guardado (simplifica el ruido del GPS parado) y conserva como mucho los [maximo] últimos
 * (los más antiguos se descartan). Sin hilos: lo usa el colector de fijaciones del ViewModel.
 */
internal class EstelaRecorrido(
    private val maximo: Int = MAXIMO_PUNTOS,
    private val pasoMinimoM: Double = PASO_MINIMO_M,
) {
    private val guardados = ArrayDeque<PuntoGeo>()
    private var ultimaPublicacionMs: Long? = null

    /** Guarda [punto] si avanza lo bastante respecto al último; devuelve si cambió la estela. */
    fun anadir(punto: PuntoGeo): Boolean {
        val ultimo = guardados.lastOrNull()
        if (ultimo != null && ultimo.distanciaA(punto) < pasoMinimoM) return false
        guardados.addLast(punto)
        if (guardados.size > maximo) guardados.removeFirst()
        return true
    }

    /**
     * Guarda [punto] y devuelve la estela a publicar, o null si no cambió o ya se publicó hace menos de
     * [PERIODO_PUBLICACION_MS] (el cambio se publica con el siguiente punto). [ahoraMs]: reloj monótono.
     */
    fun registrar(
        punto: PuntoGeo,
        ahoraMs: Long,
    ): List<PuntoGeo>? {
        val cambio = anadir(punto)
        val ultima = ultimaPublicacionMs
        if (!cambio || (ultima != null && ahoraMs - ultima < PERIODO_PUBLICACION_MS)) return null
        ultimaPublicacionMs = ahoraMs
        return puntos()
    }

    /** Copia inmutable de los puntos, del más antiguo al más reciente. */
    fun puntos(): List<PuntoGeo> = guardados.toList()

    fun limpiar() {
        guardados.clear()
        ultimaPublicacionMs = null
    }

    companion object {
        const val MAXIMO_PUNTOS = 5_000
        const val PASO_MINIMO_M = 5.0
        const val PERIODO_PUBLICACION_MS = 1_000L
    }
}

/**
 * Identifica el recorrido que se graba: mientras no cambie la clave la estela sigue creciendo; con otra, empieza de
 * cero. Null = sin conducción (la estela se borra). Cambiar el destino o recalcular la ruta no abre un recorrido nuevo;
 * pasar de modo libre a guiado (o al revés) sí, igual que la grabación de recorridos.
 */
internal fun claveRecorrido(estado: EstadoNavegacion): String? =
    when (estado) {
        is EstadoNavegacion.Inactiva, is EstadoNavegacion.Error -> null
        is EstadoNavegacion.Libre -> "libre:${estado.reconocimientoTramoId.orEmpty()}"
        is EstadoNavegacion.Guiando, is EstadoNavegacion.Llegada -> "guiado"
    }
