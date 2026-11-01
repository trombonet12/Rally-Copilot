package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.motor.tramos.MarcaTiempo
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Formato de almacenamiento de los tramos y sus pasadas.
 *
 * - Cortes: números separados por comas (`Double.toString`, sin pérdida).
 * - Marcas: `d:t` por marca separadas por `;`, con la distancia en milímetros y el tiempo en milisegundos; la primera
 *   es absoluta y el resto, la diferencia con la anterior (más corto que las absolutas y sin perder precisión de ms).
 * - [compactar] reduce las marcas de la pasada (una por fijación) a las que hacen falta para interpolar el delta: una
 *   cada [PASO_M] metros, las de los cortes de sector y microsector, la salida y la meta.
 */
internal object CodecTramos {
    /** Distancia aproximada entre marcas guardadas. */
    const val PASO_M = 50.0

    /** Un salto de tiempo mayor (ms) entre dos marcas (una parada) conserva la marca anterior a él. */
    private const val SALTO_MS = 5_000L
    private const val MM_POR_M = 1000.0
    private const val EPS_CORTE_M = 1e-6
    private const val EPS_SNAP_M = 0.001

    fun codificarCortes(cortes: List<Double>): String = cortes.joinToString(",")

    fun decodificarCortes(texto: String): List<Double> =
        if (texto.isEmpty()) emptyList() else texto.split(',').map(String::toDouble)

    fun codificarMarcas(marcas: List<MarcaTiempo>): String {
        var dAnterior = 0L
        var tAnterior = 0L
        val texto = StringBuilder()
        for (marca in marcas) {
            val d = (marca.distanciaM * MM_POR_M).roundToLong()
            if (texto.isNotEmpty()) texto.append(';')
            texto.append(d - dAnterior).append(':').append(marca.tMs - tAnterior)
            dAnterior = d
            tAnterior = marca.tMs
        }
        return texto.toString()
    }

    /**
     * Las marcas del [texto]. Como se guardan al milímetro, una marca que cae a menos de [EPS_SNAP_M] de un corte (o de
     * la meta) de [cortesM] recupera su distancia exacta: así la pasada cubre la meta y los cortes tal cual.
     * @throws IllegalArgumentException si el texto no es un formato válido.
     */
    fun decodificarMarcas(
        texto: String,
        cortesM: Collection<Double> = emptyList(),
    ): List<MarcaTiempo> {
        if (texto.isEmpty()) return emptyList()
        var d = 0L
        var t = 0L
        return texto.split(';').map {
            val (dd, dt) = it.split(':').also { partes -> require(partes.size == 2) { "Marca no válida" } }
            d += dd.toLong()
            t += dt.toLong()
            val distanciaM = d / MM_POR_M
            MarcaTiempo(cortesM.firstOrNull { c -> abs(c - distanciaM) < EPS_SNAP_M } ?: distanciaM, t)
        }
    }

    /** Marcas a guardar: ver la descripción del objeto. [obligatoriasM] son las distancias de los cortes del tramo. */
    fun compactar(
        marcas: List<MarcaTiempo>,
        obligatoriasM: Collection<Double>,
    ): List<MarcaTiempo> {
        if (marcas.size <= 2) return marcas
        val resultado = ArrayList<MarcaTiempo>()
        var ultimaGuardada = marcas.first()
        resultado += ultimaGuardada
        for (i in 1 until marcas.lastIndex) {
            val m = marcas[i]
            val antesDeSalto = marcas[i + 1].tMs - m.tMs > SALTO_MS
            val esCorte = obligatoriasM.any { abs(it - m.distanciaM) < EPS_CORTE_M }
            if (esCorte || antesDeSalto || m.distanciaM - ultimaGuardada.distanciaM >= PASO_M) {
                resultado += m
                ultimaGuardada = m
            }
        }
        resultado += marcas.last()
        return resultado
    }
}
