package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import java.time.Instant

/** Clase de incidencia de tráfico, ya reducida a lo que importa al conductor. */
enum class TipoIncidencia {
    OBRAS,
    ACCIDENTE,
    RETENCION,
    CORTE,
    METEO,
    OTRA,
}

/** Gravedad aproximada (DATEX: lowest/low = baja, medium = media, high/highest = alta). */
enum class GravedadIncidencia {
    BAJA,
    MEDIA,
    ALTA,
}

/**
 * Incidencia de tráfico vigente. [puntos] son WGS84: uno si es puntual o, si es un tramo, desde y hasta (en el orden
 * en que se indican, que no tiene por qué ser el de la marcha). [descripcion] es una frase corta en español ya hecha
 * («Obras en Ma-15, calzada cortada»); [fin] es `null` si no tiene fecha de fin prevista.
 */
data class Incidencia(
    val id: String,
    val tipo: TipoIncidencia,
    val puntos: List<PuntoGeo>,
    val carretera: String?,
    val descripcion: String,
    val inicio: Instant?,
    val fin: Instant?,
    val gravedad: GravedadIncidencia?,
) {
    /** `true` si tiene fecha de fin y [ahora] ya la alcanzó (el feed puede tardar en retirarla). */
    fun caducadaEn(ahora: Instant): Boolean = fin != null && !ahora.isBefore(fin)
}

/** Recuadro en grados (lat/lon), bordes incluidos. */
data class CajaGeo(
    val latMin: Double,
    val lonMin: Double,
    val latMax: Double,
    val lonMax: Double,
) {
    fun contiene(punto: PuntoGeo): Boolean = punto.lat in latMin..latMax && punto.lon in lonMin..lonMax
}

/** Por qué no se pudo leer el feed. */
enum class MotivoErrorDatex {
    /** No es XML bien formado (truncado, vacío, HTML de error...). */
    XML_MAL_FORMADO,

    /** Es XML, pero no una publicación DATEX II. */
    NO_ES_DATEX,

    /** Falló la lectura del flujo (red cortada, gzip corrupto...). */
    ENTRADA_ILEGIBLE,
}

/** Resultado de [LectorDatex.leer]: nunca se lanza una excepción por datos malos. */
sealed interface ResultadoDatex {
    /** [situacionesLeidas] cuenta todas las del feed (también las de fuera de Mallorca). */
    data class Ok(
        val incidencias: List<Incidencia>,
        val situacionesLeidas: Int,
    ) : ResultadoDatex

    data class Error(
        val motivo: MotivoErrorDatex,
        val detalle: String?,
    ) : ResultadoDatex
}
