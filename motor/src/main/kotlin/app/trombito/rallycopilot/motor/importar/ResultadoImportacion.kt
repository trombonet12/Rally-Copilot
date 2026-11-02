package app.trombito.rallycopilot.motor.importar

import app.trombito.rallycopilot.motor.alertas.XmlMalFormadoException
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import java.io.CharConversionException
import java.io.IOException

/** Por qué no se pudo importar un archivo GPX o KML. */
enum class MotivoErrorImportacion {
    /** El XML está mal formado, truncado, mal codificado o lleva DOCTYPE. */
    XML_MAL_FORMADO,

    /** El XML es válido pero no es un GPX/KML con una traza de al menos dos puntos. */
    SIN_TRAZA,

    /** Más puntos de los admitidos ([LectorTraza.TOPE_PUNTOS]). */
    DEMASIADOS_PUNTOS,

    /** Falló la lectura del flujo. */
    ENTRADA_ILEGIBLE,
}

/** Resultado de leer un GPX o un KML. */
sealed interface ResultadoImportacion {
    /** Traza de al menos 2 puntos; [nombre] es el del archivo (`<name>`) si lo trae. */
    data class Ok(
        val nombre: String?,
        val puntos: List<PuntoGeo>,
    ) : ResultadoImportacion {
        init {
            require(puntos.size >= 2) { "Una traza importada necesita al menos dos puntos" }
        }
    }

    /** No se pudo leer: [motivo] y, si lo hay, el [detalle] técnico. */
    data class Error(
        val motivo: MotivoErrorImportacion,
        val detalle: String? = null,
    ) : ResultadoImportacion
}

/** Una línea leída del archivo, con el nombre del elemento que la contiene. */
internal class LineaLeida(
    val nombre: String?,
    val puntos: List<PuntoGeo>,
)

/** Fallo interno de lectura con su motivo; no sale de este paquete. */
internal class FalloImportacion(
    val motivo: MotivoErrorImportacion,
    mensaje: String,
) : Exception(mensaje)

/** Utilidades comunes de los lectores de trazas. */
object LectorTraza {
    /** Máximo de puntos admitidos en una traza (un GPX de 20 MB no pasa de ~250 000). */
    const val TOPE_PUNTOS = 500_000

    /** Máximo de caracteres de texto acumulado en un elemento. */
    const val TOPE_TEXTO = 24 * 1024 * 1024

    /** Máximo de elementos anidados. */
    const val TOPE_PROFUNDIDAD = 64

    /** Distancia máxima (m) entre el final de una línea y el inicio de la siguiente para considerarlas contiguas. */
    const val CONTIGUA_M = 50.0

    internal fun resultado(accion: () -> ResultadoImportacion): ResultadoImportacion =
        try {
            accion()
        } catch (e: FalloImportacion) {
            ResultadoImportacion.Error(e.motivo, e.message)
        } catch (e: XmlMalFormadoException) {
            ResultadoImportacion.Error(MotivoErrorImportacion.XML_MAL_FORMADO, e.message)
        } catch (e: CharConversionException) {
            ResultadoImportacion.Error(MotivoErrorImportacion.XML_MAL_FORMADO, e.message)
        } catch (e: IOException) {
            ResultadoImportacion.Error(MotivoErrorImportacion.ENTRADA_ILEGIBLE, e.message)
        }

    /**
     * Elige la traza de varias [lineas]: las contiguas (la siguiente empieza a menos de [CONTIGUA_M] del final de
     * la anterior) se concatenan; de los grupos resultantes gana el más largo. Null si ninguno llega a 2 puntos.
     */
    internal fun elegir(lineas: List<LineaLeida>): Pair<String?, List<PuntoGeo>>? {
        val grupos = ArrayList<Pair<String?, MutableList<PuntoGeo>>>()
        for (linea in lineas.filter { it.puntos.size >= 2 }) {
            val ultimo = grupos.lastOrNull()
            if (ultimo != null && ultimo.second.last().distanciaA(linea.puntos.first()) <= CONTIGUA_M) {
                ultimo.second.addAll(linea.puntos)
            } else {
                grupos.add(linea.nombre to linea.puntos.toMutableList())
            }
        }
        val mejor = grupos.maxByOrNull { it.second.longitudMetros() } ?: return null
        return mejor.first to quitarAlturaVacia(mejor.second)
    }

    /** Si todas las alturas son 0 (KML «a ras de suelo») no son datos: se descartan. */
    internal fun quitarAlturaVacia(puntos: List<PuntoGeo>): List<PuntoGeo> =
        if (puntos.all { it.alt == null || it.alt == 0.0 }) puntos.map { it.copy(alt = null) } else puntos

    /** Punto con coordenadas válidas (finitas y en rango) o null. */
    internal fun punto(
        lat: Double?,
        lon: Double?,
        alt: Double? = null,
    ): PuntoGeo? {
        if (lat == null || lon == null) return null
        val valido = lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
        return if (valido) PuntoGeo(lat, lon, alt?.takeIf { it.isFinite() }) else null
    }
}
