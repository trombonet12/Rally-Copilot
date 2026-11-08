package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.voz.Frase
import app.trombito.rallycopilot.motor.voz.TipoFrase

/**
 * Situación del conductor en un tramo de radar. [mediaKmh] es la media desde la entrada (`null` hasta recorrer
 * [VelocidadMediaTramo.RECORRIDO_MIN_M] o si el reloj no avanzó) y, con [terminado], la final del tramo.
 * [superaLimite] es `true` si la media pasa del límite (para ponerla en rojo). [frase] es el aviso de voz pendiente
 * (el mismo id mientras la media siga por encima), o `null`.
 */
data class EstadoTramo(
    val tramo: TramoRadar,
    val mediaKmh: Double?,
    val terminado: Boolean,
    val superaLimite: Boolean,
    val frase: Frase?,
)

/**
 * Mide la velocidad media en los [tramos] de radar de una ruta: distancia recorrida entre la entrada y la salida
 * dividida por el tiempo, con el reloj que se inyecta en [actualizar] (milisegundos, monótono).
 *
 * - La entrada y la salida se interpolan linealmente entre la fijación anterior y la actual, para que el intervalo
 *   entre fijaciones no falsee la media.
 * - Solo se mide un tramo que se ve entrar: si la primera fijación ya está dentro, o si se retrocede por detrás de la
 *   entrada, el tramo no se mide hasta volver a entrar.
 * - Aviso corto («media alta», id `tramo-media-<tramo>-<n>`, [TipoFrase.AVISO_VELOCIDAD], [Frase.puntoM] `null`: va
 *   por detrás de notas y maniobras) solo si la media supera el límite en [HISTERESIS_KMH] (3 km/h) o más; una vez por
 *   superación: si la media baja al límite o menos se retira y se rearma, y la siguiente superación usa otro id.
 *   Además, como mucho [MAX_POR_TRAMO] (2) avisos por tramo y con al menos [SEPARACION_MS] (60 s) entre ellos.
 *   [EstadoTramo.superaLimite] (para el rojo de la UI) sigue siendo «media > límite», sin histéresis.
 *
 * Solo modo ruta: los tramos salen de [AlertasRadar.tramos] (posiciones sobre la ruta). En modo libre la media la
 * mide [MediaTramoLibre] con el mismo cálculo y los mismos avisos ([MedicionTramo]).
 */
class VelocidadMediaTramo(
    private val tramos: List<TramoRadar>,
) {
    private class Muestra(
        val posicionM: Double,
        val ahoraMs: Long,
    )

    private class Activo(
        val tramo: TramoRadar,
        val medicion: MedicionTramo,
    )

    private var previa: Muestra? = null
    private var activo: Activo? = null

    /** Estado en [posicionM] (metros sobre la ruta) en el instante [ahoraMs]; `null` si no se está en un tramo. */
    fun actualizar(
        posicionM: Double,
        ahoraMs: Long,
    ): EstadoTramo? {
        val anterior = previa
        previa = Muestra(posicionM, ahoraMs)
        if (activo?.let { posicionM < it.tramo.inicioM } == true) activo = null
        val ahora = Muestra(posicionM, ahoraMs)
        val actual = activo ?: anterior?.let { abrir(it, ahora) }?.also { activo = it }
        return actual?.let {
            if (posicionM >= it.tramo.finM) {
                activo = null
                val finMs = instanteEn(checkNotNull(anterior), ahora, it.tramo.finM)
                it.medicion.cerrar(it.tramo.finM - it.tramo.inicioM, finMs)
            } else {
                it.medicion.medir(posicionM - it.tramo.inicioM, ahoraMs)
            }
        }
    }

    private fun abrir(
        anterior: Muestra,
        ahora: Muestra,
    ): Activo? {
        val tramo =
            tramos
                .filter { it.finM > it.inicioM && anterior.posicionM < it.inicioM && it.inicioM <= ahora.posicionM }
                .minByOrNull { it.inicioM }
        return tramo?.let { Activo(it, MedicionTramo(it, instanteEn(anterior, ahora, it.inicioM))) }
    }

    /** Instante (ms, interpolado) en que se pasó por [posicionM] entre las dos muestras. */
    private fun instanteEn(
        anterior: Muestra,
        ahora: Muestra,
        posicionM: Double,
    ): Double {
        val fraccion = (posicionM - anterior.posicionM) / (ahora.posicionM - anterior.posicionM)
        return anterior.ahoraMs + fraccion * (ahora.ahoraMs - anterior.ahoraMs)
    }

    companion object {
        /** Antes de recorrer esto dentro del tramo la media no es fiable y no se da. */
        const val RECORRIDO_MIN_M = 100.0

        /** La media debe superar el límite en esto (km/h) o más para avisar. */
        const val HISTERESIS_KMH = 3.0

        /** Separación mínima entre dos avisos de un mismo tramo. */
        const val SEPARACION_MS = 60_000L

        /** Avisos máximos por tramo. */
        const val MAX_POR_TRAMO = 2
    }
}

/**
 * Medida de un tramo ya abierto, común a guiado ([VelocidadMediaTramo]) y libre ([MediaTramoLibre]): la media sobre
 * lo recorrido desde [inicioMs] (instante interpolado de la entrada), la histéresis y el cupo de avisos «media alta».
 */
internal class MedicionTramo(
    private val tramo: TramoRadar,
    private val inicioMs: Double,
) {
    private var superaciones = 0
    private var ultimoAvisoMs: Long? = null
    private var armado = true
    private var pendiente: Frase? = null

    /** Estado con [recorridoM] metros desde la entrada en el instante [ahoraMs]. */
    fun medir(
        recorridoM: Double,
        ahoraMs: Long,
    ): EstadoTramo {
        val media =
            if (recorridoM >= VelocidadMediaTramo.RECORRIDO_MIN_M) mediaKmh(recorridoM, ahoraMs - inicioMs) else null
        val supera = supera(media, tramo.limiteKmh)
        if (media != null && !supera) {
            armado = true
            pendiente = null
        } else if (armado && puedeAvisar(media, ahoraMs)) {
            armado = false
            superaciones++
            ultimoAvisoMs = ahoraMs
            pendiente = Frase("tramo-media-${tramo.id}-$superaciones", TEXTO, TipoFrase.AVISO_VELOCIDAD, null)
        }
        return EstadoTramo(tramo, media, terminado = false, superaLimite = supera, frase = pendiente)
    }

    /** Estado final: [recorridoM] metros entre la entrada y la salida, que se pasó en el instante [finMs]. */
    fun cerrar(
        recorridoM: Double,
        finMs: Double,
    ): EstadoTramo {
        val media = mediaKmh(recorridoM, finMs - inicioMs)
        return EstadoTramo(tramo, media, terminado = true, superaLimite = supera(media, tramo.limiteKmh), frase = null)
    }

    /** Media por encima del límite en la histéresis o más, con cupo por tramo y separación desde el último aviso. */
    private fun puedeAvisar(
        media: Double?,
        ahoraMs: Long,
    ): Boolean {
        val limite = tramo.limiteKmh ?: return false
        val separado = ultimoAvisoMs?.let { ahoraMs - it >= VelocidadMediaTramo.SEPARACION_MS } ?: true
        return media != null &&
            media >= limite + VelocidadMediaTramo.HISTERESIS_KMH &&
            separado &&
            superaciones < VelocidadMediaTramo.MAX_POR_TRAMO
    }

    private fun supera(
        media: Double?,
        limiteKmh: Int?,
    ): Boolean = media != null && limiteKmh != null && media > limiteKmh

    /** km/h para [metros] en [milisegundos] (`m · 3600 / ms`, exacto con enteros); `null` si el tiempo no avanza. */
    private fun mediaKmh(
        metros: Double,
        milisegundos: Double,
    ): Double? = if (milisegundos > 0.0) metros * MS_POR_HORA_KM / milisegundos else null

    private companion object {
        const val MS_POR_HORA_KM = 3600.0
        const val TEXTO = "media alta"
    }
}
