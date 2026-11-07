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
 * Solo modo ruta: los tramos salen de [AlertasRadar.tramos] (posiciones sobre la ruta).
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
        val inicioMs: Double,
    ) {
        var superaciones = 0
        var ultimoAvisoMs: Long? = null
        var armado = true
        var pendiente: Frase? = null
    }

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
                cerrar(it, checkNotNull(anterior), ahora)
            } else {
                medir(it, posicionM, ahoraMs)
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
        return tramo?.let { Activo(it, instanteEn(anterior, ahora, it.inicioM)) }
    }

    private fun cerrar(
        actual: Activo,
        anterior: Muestra,
        ahora: Muestra,
    ): EstadoTramo {
        val tramo = actual.tramo
        val finMs = instanteEn(anterior, ahora, tramo.finM)
        val media = mediaKmh(tramo.finM - tramo.inicioM, finMs - actual.inicioMs)
        val supera = supera(media, tramo.limiteKmh)
        return EstadoTramo(tramo, media, terminado = true, superaLimite = supera, frase = null)
    }

    private fun medir(
        actual: Activo,
        posicionM: Double,
        ahoraMs: Long,
    ): EstadoTramo {
        val tramo = actual.tramo
        val recorrido = posicionM - tramo.inicioM
        val media = if (recorrido >= RECORRIDO_MIN_M) mediaKmh(recorrido, ahoraMs - actual.inicioMs) else null
        val supera = supera(media, tramo.limiteKmh)
        if (media != null && !supera) {
            actual.armado = true
            actual.pendiente = null
        } else if (actual.armado && puedeAvisar(actual, media, ahoraMs)) {
            actual.armado = false
            actual.superaciones++
            actual.ultimoAvisoMs = ahoraMs
            val id = "tramo-media-${tramo.id}-${actual.superaciones}"
            actual.pendiente = Frase(id, TEXTO, TipoFrase.AVISO_VELOCIDAD, null)
        }
        return EstadoTramo(tramo, media, terminado = false, superaLimite = supera, frase = actual.pendiente)
    }

    /** Media por encima del límite en [HISTERESIS_KMH] o más, con cupo por tramo y separación desde el último aviso. */
    private fun puedeAvisar(
        actual: Activo,
        media: Double?,
        ahoraMs: Long,
    ): Boolean {
        val limite = actual.tramo.limiteKmh ?: return false
        val separado = actual.ultimoAvisoMs?.let { ahoraMs - it >= SEPARACION_MS } ?: true
        return media != null && media >= limite + HISTERESIS_KMH && separado && actual.superaciones < MAX_POR_TRAMO
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
        private const val MS_POR_HORA_KM = 3600.0

        /** La media debe superar el límite en esto (km/h) o más para avisar. */
        const val HISTERESIS_KMH = 3.0

        /** Separación mínima entre dos avisos de un mismo tramo. */
        const val SEPARACION_MS = 60_000L

        /** Avisos máximos por tramo. */
        const val MAX_POR_TRAMO = 2
        private const val TEXTO = "media alta"
    }
}
