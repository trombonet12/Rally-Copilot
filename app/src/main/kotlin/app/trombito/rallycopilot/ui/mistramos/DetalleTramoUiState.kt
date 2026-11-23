package app.trombito.rallycopilot.ui.mistramos

import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.domain.tramos.TipoTramo
import app.trombito.rallycopilot.motor.analisis.PuntoEvolucion
import app.trombito.rallycopilot.motor.geo.PuntoGeo

/**
 * Una pasada del detalle: [deltaMejorMs] es su tiempo menos el de la mejor (0 en la mejor). [recorridoId] es el viaje
 * que la grabó (null si ya no existe): con él se abre el resumen del viaje.
 */
data class FilaPasada(
    val id: Long,
    val inicioMs: Long,
    val totalMs: Long,
    val deltaMejorMs: Long,
    val esMejor: Boolean,
    val recorridoId: Long? = null,
)

/**
 * Un subtramo guardado del detalle: el trozo de [desdeM] a [hastaM] (metros desde la salida) recorrido en [inicioMs]
 * en [tiempoMs]; [deltaMs] es su tiempo menos el mejor de entonces (null si era la primera vez en ese trozo).
 * [desdeKm] y [hastaKm] son esos mismos puntos en kilómetros de la CARRETERA, cuando el tramo es de vía y se sabe
 * (null: se enseñan los kilómetros desde la salida del tramo).
 */
data class FilaSubtramo(
    val indice: Int,
    val inicioMs: Long,
    val desdeM: Double,
    val hastaM: Double,
    val tiempoMs: Long,
    val deltaMs: Long?,
    val desdeKm: Double? = null,
    val hastaKm: Double? = null,
)

/**
 * El subtramo con sus puntos en el kilómetro de la carretera: un tramo de vía con hitos de salida y meta reparte su
 * rango de kilómetros en proporción a los metros recorridos (cuadra con el título del tramo, también si el sentido
 * es el de los kilómetros decrecientes). Cualquier otro tramo lo deja como está.
 */
fun FilaSubtramo.enKmDeCarretera(tramo: ResumenTramo?): FilaSubtramo {
    val pkDesde = tramo?.via?.pkDesde
    val pkHasta = tramo?.via?.pkHasta
    val longitudM = tramo?.longitudM ?: 0.0
    val deVia = tramo?.tipo == TipoTramo.VIA && longitudM > 0.0
    if (!deVia || pkDesde == null || pkHasta == null) return this

    fun km(metros: Double) = pkDesde + (pkHasta - pkDesde) * (metros / longitudM).coerceIn(0.0, 1.0)
    return copy(desdeKm = km(desdeM), hastaKm = km(hastaM))
}

/** Puntos de la gráfica de evolución: [esRecord] si la pasada mejoró la mejor anterior (la primera no lo es). */
fun evolucionDe(pasadas: List<FilaPasada>): List<PuntoEvolucion> {
    var mejorHastaAhora = Long.MAX_VALUE
    return pasadas.sortedBy { it.inicioMs }.map {
        val record = mejorHastaAhora != Long.MAX_VALUE && it.totalMs < mejorHastaAhora
        mejorHastaAhora = minOf(mejorHastaAhora, it.totalMs)
        PuntoEvolucion(it.id.toString(), it.inicioMs, it.totalMs, record)
    }
}

/**
 * Una nota marcada como mal cantada: a [posicionM] metros de la salida; [texto] es lo que decía la nota si se pudo
 * reconstruir desde la hoja del tramo (null: solo se enseña el kilómetro).
 */
data class FilaNotaPorRevisar(
    val posicionM: Double,
    val texto: String?,
)

/** Cómo va la exportación del tramo para compartirlo: [PREPARANDO] mientras se escribe el archivo. */
enum class EstadoCompartir { LIBRE, PREPARANDO, FALLO }

/** Detalle de un tramo. Con [cargando] falso y [tramo] nulo, el tramo ya no existe. */
data class DetalleTramoUiState(
    val cargando: Boolean = true,
    val tramo: ResumenTramo? = null,
    val traza: List<PuntoGeo> = emptyList(),
    /** Estilo MapLibre offline para dibujar la traza sobre el mapa; null si aún no hay datos de mapa instalados. */
    val estiloMapaJson: String? = null,
    val pasadas: List<FilaPasada> = emptyList(),
    /** Curvas de la hoja de notas del tramo (null mientras no se sabe). */
    val curvas: Int? = null,
    /** Subtramos guardados de este tramo, el más reciente primero; vacío = no se enseña la sección. */
    val subtramos: List<FilaSubtramo> = emptyList(),
    /** Notas marcadas como mal cantadas, por posición; vacío = no se enseña la sección. */
    val notasPorRevisar: List<FilaNotaPorRevisar> = emptyList(),
    val renombrando: Boolean = false,
    val compartir: EstadoCompartir = EstadoCompartir.LIBRE,
)
