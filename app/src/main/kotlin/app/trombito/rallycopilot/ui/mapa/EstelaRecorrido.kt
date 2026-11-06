package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.motor.geo.PuntoGeo

/**
 * Puntos del recorrido en curso para pintar la estela de por dónde se ha pasado. Solo guarda un punto si dista al menos
 * [pasoMinimoM] del último guardado (simplifica el ruido del GPS parado) y conserva como mucho los [maximo] últimos
 * (los más antiguos se descartan). La estela va en segmentos: si entre un punto y el siguiente hay más de [saltoM]
 * metros (teletransporte, repetición de una traza) o pasan más de [huecoMs] ms sin fijaciones (pérdida de GPS), el
 * punto abre un segmento nuevo y no se dibuja una recta entre ambos. Sin hilos: lo usa el colector de fijaciones.
 */
internal class EstelaRecorrido(
    private val maximo: Int = MAXIMO_PUNTOS,
    private val pasoMinimoM: Double = PASO_MINIMO_M,
    private val saltoM: Double = SALTO_MAXIMO_M,
    private val huecoMs: Long = HUECO_MAXIMO_MS,
) {
    private val segmentos = ArrayDeque<ArrayDeque<PuntoGeo>>()
    private var total = 0
    private var ultimaPublicacionMs: Long? = null
    private var ultimaFijacionMs: Long? = null
    private var cortarAlSiguiente = false

    /** Guarda [punto] si avanza lo bastante respecto al último; devuelve si cambió la estela. */
    fun anadir(punto: PuntoGeo): Boolean {
        val ultimo = segmentos.lastOrNull()?.lastOrNull()
        if (ultimo != null && ultimo.distanciaA(punto) < pasoMinimoM) return false
        val nuevoSegmento = ultimo == null || cortarAlSiguiente || ultimo.distanciaA(punto) > saltoM
        if (nuevoSegmento) segmentos.addLast(ArrayDeque())
        cortarAlSiguiente = false
        segmentos.last().addLast(punto)
        total++
        while (total > maximo) {
            val primero = segmentos.first()
            primero.removeFirst()
            total--
            if (primero.isEmpty()) segmentos.removeFirst()
        }
        return true
    }

    /**
     * Guarda [punto] y devuelve la estela (segmentos) a publicar, o null si no cambió o ya se publicó hace menos de
     * [PERIODO_PUBLICACION_MS] (el cambio se publica con el siguiente punto). [ahoraMs]: reloj monótono.
     */
    fun registrar(
        punto: PuntoGeo,
        ahoraMs: Long,
    ): List<List<PuntoGeo>>? {
        val anterior = ultimaFijacionMs
        if (anterior != null && ahoraMs - anterior > huecoMs) cortarAlSiguiente = true
        ultimaFijacionMs = ahoraMs
        val cambio = anadir(punto)
        val ultima = ultimaPublicacionMs
        if (!cambio || (ultima != null && ahoraMs - ultima < PERIODO_PUBLICACION_MS)) return null
        ultimaPublicacionMs = ahoraMs
        return segmentos()
    }

    /** Copia inmutable de los segmentos, del más antiguo al más reciente. */
    fun segmentos(): List<List<PuntoGeo>> = segmentos.map { it.toList() }

    /** Todos los puntos, del más antiguo al más reciente (sin separar los segmentos). */
    fun puntos(): List<PuntoGeo> = segmentos.flatten()

    fun limpiar() {
        segmentos.clear()
        total = 0
        ultimaPublicacionMs = null
        ultimaFijacionMs = null
        cortarAlSiguiente = false
    }

    companion object {
        const val MAXIMO_PUNTOS = 5_000
        const val PASO_MINIMO_M = 5.0
        const val PERIODO_PUBLICACION_MS = 1_000L

        /** Distancia entre dos puntos seguidos a partir de la cual no se unen con una línea. */
        const val SALTO_MAXIMO_M = 150.0

        /** Tiempo entre dos fijaciones seguidas a partir del cual no se unen con una línea. */
        const val HUECO_MAXIMO_MS = 10_000L
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
