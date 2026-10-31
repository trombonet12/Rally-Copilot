package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Una vía que pasa por un nodo: la vía y el índice del punto dentro de `Via.puntos`. */
data class Arista(
    val via: Via,
    val indice: Int,
)

/**
 * Grafo de vías: los nodos son coordenadas compartidas (mismo punto a 1e-6°, es decir, los nodos de OSM) y cada nodo
 * guarda las aristas (vía, índice de punto) que pasan por él. Se construye en una pasada con una tabla hash de
 * claves primitivas, sin objetos por punto, para que 33 000 vías salgan en bastante menos de un segundo.
 */
class GrafoVias(
    private val vias: List<Via>,
) {
    private val inicioVia = IntArray(vias.size + 1)
    private val total: Int
    private val viaDePunto: IntArray
    private val nodoDePunto: IntArray
    private val tablaClaves: LongArray
    private val tablaNodos: IntArray
    private val bitsTabla: Int
    private val inicioNodo: IntArray
    private val puntosDeNodo: IntArray

    init {
        vias.forEachIndexed { v, via -> inicioVia[v + 1] = inicioVia[v] + via.puntos.size }
        total = inicioVia[vias.size]
        var capacidad = CAPACIDAD_MINIMA
        while (capacidad < total * 2) capacidad = capacidad shl 1
        bitsTabla = Integer.numberOfTrailingZeros(capacidad)
        tablaClaves = LongArray(capacidad) { VACIO }
        tablaNodos = IntArray(capacidad)
        viaDePunto = IntArray(total)
        nodoDePunto = IntArray(total)
        val numNodos = indexar()
        inicioNodo = IntArray(numNodos + 1)
        puntosDeNodo = IntArray(total)
        compactar(numNodos)
    }

    private fun indexar(): Int {
        var nodos = 0
        vias.forEachIndexed { v, via ->
            via.puntos.forEachIndexed { i, punto ->
                val g = inicioVia[v] + i
                val clave = clave(punto)
                val ranura = ranura(clave)
                if (tablaClaves[ranura] == VACIO) {
                    tablaClaves[ranura] = clave
                    tablaNodos[ranura] = nodos++
                }
                viaDePunto[g] = v
                nodoDePunto[g] = tablaNodos[ranura]
            }
        }
        return nodos
    }

    private fun compactar(numNodos: Int) {
        for (g in 0 until total) inicioNodo[nodoDePunto[g] + 1]++
        for (n in 0 until numNodos) inicioNodo[n + 1] += inicioNodo[n]
        val cursor = inicioNodo.copyOf(numNodos)
        for (g in 0 until total) puntosDeNodo[cursor[nodoDePunto[g]]++] = g
    }

    private fun ranura(clave: Long): Int {
        val mascara = (1 shl bitsTabla) - 1
        var h = ((clave * MEZCLA) ushr (Long.SIZE_BITS - bitsTabla)).toInt()
        while (tablaClaves[h] != VACIO && tablaClaves[h] != clave) h = (h + 1) and mascara
        return h
    }

    /** Aristas (vía, índice de punto) que pasan por la coordenada dada; vacío si ninguna vía tiene ese punto. */
    fun aristas(punto: PuntoGeo): List<Arista> {
        val ranura = ranura(clave(punto))
        if (tablaClaves[ranura] == VACIO) return emptyList()
        val nodo = tablaNodos[ranura]
        return (inicioNodo[nodo] until inicioNodo[nodo + 1]).map { k ->
            val g = puntosDeNodo[k]
            val v = viaDePunto[g]
            Arista(vias[v], g - inicioVia[v])
        }
    }

    /**
     * Rangos de las ramas del nodo (una por dirección en que se puede salir de cada vía que pasa), de mayor a menor.
     * Un nodo con menos de 3 ramas no es un cruce.
     */
    private fun rangosDeRamas(nodo: Int): List<Int> {
        val rangos = ArrayList<Int>()
        for (k in inicioNodo[nodo] until inicioNodo[nodo + 1]) {
            val g = puntosDeNodo[k]
            val v = viaDePunto[g]
            val i = g - inicioVia[v]
            val ramas = (if (i > 0) 1 else 0) + (if (i < vias[v].puntos.size - 1) 1 else 0)
            repeat(ramas) { rangos.add(rangoClase(vias[v].clase)) }
        }
        rangos.sortDescending()
        return rangos
    }

    private val puntoDeNodo: (Int) -> PuntoGeo = { nodo ->
        val g = puntosDeNodo[inicioNodo[nodo]]
        vias[viaDePunto[g]].puntos[g - inicioVia[viaDePunto[g]]]
    }
    private val cruce: IntArray by lazy {
        var n = 0
        val nodos = IntArray(inicioNodo.size - 1)
        for (nodo in nodos.indices) if (rangosDeRamas(nodo).size >= RAMAS_MINIMAS_CRUCE) nodos[n++] = nodo
        nodos.copyOf(n)
    }

    /** Por cada cruce candidato: alguna rama extra (las dos primeras son la carretera) es unclassified o mayor. */
    private val ramaExtraImportante: BooleanArray by lazy {
        BooleanArray(cruce.size) { c -> rangosDeRamas(cruce[c]).drop(2).any { it >= RANGO_BAJO } }
    }
    private val latCruce: DoubleArray by lazy {
        DoubleArray(cruce.size) { puntoDeNodo(cruce[it]).lat }
    }
    private val lonCruce: DoubleArray by lazy {
        DoubleArray(cruce.size) { puntoDeNodo(cruce[it]).lon }
    }
    private val rejilla: Map<Long, IntArray> by lazy {
        val celdas = HashMap<Long, MutableList<Int>>()
        for (c in cruce.indices) {
            val fila = floor(latCruce[c] * CELDAS_POR_GRADO).toInt()
            val columna = floor(lonCruce[c] * CELDAS_POR_GRADO).toInt()
            celdas.getOrPut(claveCelda(fila, columna)) { ArrayList() }.add(c)
        }
        celdas.mapValues { it.value.toIntArray() }
    }

    /**
     * Posiciones (metros a lo largo de [polilinea]) de los cruces del grafo que caen sobre ella a menos de
     * [toleranciaM]. Un cruce es un nodo con 3 o más ramas (contando las dos direcciones de una vía que pasa) y,
     * además, alguna rama extra es unclassified o de rango mayor, o la polilínea gira en el nodo más de
     * 30°. Ordenadas y sin duplicados a menos de 50 m.
     */
    fun cruces(
        polilinea: List<PuntoGeo>,
        toleranciaM: Double = 3.0,
    ): List<Double> {
        val encontrados = ArrayList<Hallazgo>()
        val acumulada = DoubleArray(polilinea.size)
        for (i in 0 until polilinea.size - 1) {
            val largo = polilinea[i].distanciaA(polilinea[i + 1])
            buscarEnSegmento(Segmento(polilinea[i], polilinea[i + 1], acumulada[i], largo), toleranciaM, encontrados)
            acumulada[i + 1] = acumulada[i] + largo
        }
        return encontrados
            .sortedBy { it.posicionM }
            .filter { ramaExtraImportante[it.cruce] || Giro.esGiro(polilinea, acumulada, it.posicionM) }
            .fold(ArrayList<Double>()) { sin, h ->
                if (sin.isEmpty() || h.posicionM - sin.last() >= DISTANCIA_DUPLICADOS_M) sin.add(h.posicionM)
                sin
            }
    }

    private class Hallazgo(
        val posicionM: Double,
        val cruce: Int,
    )

    private fun buscarEnSegmento(
        segmento: Segmento,
        toleranciaM: Double,
        salida: MutableList<Hallazgo>,
    ) {
        val a = segmento.a
        val b = segmento.b
        val tolLat = toleranciaM / METROS_POR_GRADO
        val tolLon = tolLat / max(segmento.cosLat, COS_MINIMO)
        val (f0, f1) = rango(min(a.lat, b.lat) - tolLat, max(a.lat, b.lat) + tolLat)
        val (c0, c1) = rango(min(a.lon, b.lon) - tolLon, max(a.lon, b.lon) + tolLon)
        for (fila in f0..f1) {
            for (col in c0..c1) {
                rejilla[claveCelda(fila, col)]?.forEach { probarCruce(segmento, it, toleranciaM, salida) }
            }
        }
    }

    private fun probarCruce(
        segmento: Segmento,
        cruceIndice: Int,
        toleranciaM: Double,
        salida: MutableList<Hallazgo>,
    ) {
        val px = (lonCruce[cruceIndice] - segmento.a.lon) * segmento.cosLat * METROS_POR_GRADO
        val py = (latCruce[cruceIndice] - segmento.a.lat) * METROS_POR_GRADO
        val t = segmento.proyectar(px, py)
        if (hypot(px - t * segmento.bx, py - t * segmento.by) <= toleranciaM) {
            salida.add(Hallazgo(segmento.acumuladaM + t * segmento.largoM, cruceIndice))
        }
    }

    /** Segmento de la polilínea con su vector en metros locales (origen en `a`) y su posición acumulada. */
    private class Segmento(
        val a: PuntoGeo,
        val b: PuntoGeo,
        val acumuladaM: Double,
        val largoM: Double,
    ) {
        val cosLat = cos(Math.toRadians(a.lat))
        val bx = (b.lon - a.lon) * cosLat * METROS_POR_GRADO
        val by = (b.lat - a.lat) * METROS_POR_GRADO

        /** Fracción 0..1 del segmento donde cae la proyección del punto (px, py) en metros locales. */
        fun proyectar(
            px: Double,
            py: Double,
        ): Double {
            val largo2 = bx * bx + by * by
            return if (largo2 > 0.0) ((px * bx + py * by) / largo2).coerceIn(0.0, 1.0) else 0.0
        }
    }

    private fun rango(
        minimo: Double,
        maximo: Double,
    ): Pair<Int, Int> = floor(minimo * CELDAS_POR_GRADO).toInt() to floor(maximo * CELDAS_POR_GRADO).toInt()

    private fun claveCelda(
        fila: Int,
        columna: Int,
    ): Long = (fila + DESPLAZA_CELDA) * ANCHO_CELDAS + (columna + DESPLAZA_CELDA)

    companion object {
        private const val VACIO = Long.MIN_VALUE
        private const val MEZCLA = -7046029254386353131L
        private const val CAPACIDAD_MINIMA = 16
        private const val GRADOS_E6 = 1_000_000.0
        private const val DESPLAZA_LAT_E6 = 90_000_000L
        private const val DESPLAZA_LON_E6 = 180_000_000L
        private const val ANCHO_LON_E6 = 400_000_000L
        private const val RAMAS_MINIMAS_CRUCE = 3
        private const val RANGO_BAJO = 1
        private const val DISTANCIA_DUPLICADOS_M = 50.0
        private const val METROS_POR_GRADO = PuntoGeo.RADIO_TIERRA_M * Math.PI / 180.0
        private const val COS_MINIMO = 0.01
        private const val CELDAS_POR_GRADO = 1000.0
        private const val DESPLAZA_CELDA = 200_000L
        private const val ANCHO_CELDAS = 1_000_000L

        /** Clave de un punto: latitud y longitud en 1e-6° empaquetadas en un Long. */
        fun clave(punto: PuntoGeo): Long =
            (Math.round(punto.lat * GRADOS_E6) + DESPLAZA_LAT_E6) * ANCHO_LON_E6 +
                (Math.round(punto.lon * GRADOS_E6) + DESPLAZA_LON_E6)

        /** Rango de importancia de una clase `highway` (los `*_link` valen como su base): 7 motorway ... 0 resto. */
        internal fun rangoClase(clase: String): Int =
            when (clase.removeSuffix("_link")) {
                "motorway" -> 7
                "trunk" -> 6
                "primary" -> 5
                "secondary" -> 4
                "tertiary" -> 3
                "unclassified", "residential", "living_street" -> RANGO_BAJO
                else -> 0
            }
    }
}

/** Giro de una polilínea en un punto de ella. */
private object Giro {
    const val GIRO_MINIMO_GRADOS = 30.0
    const val VENTANA_M = 20.0
    const val LADO_MINIMO_M = 5.0
    const val METROS_POR_GRADO = PuntoGeo.RADIO_TIERRA_M * Math.PI / 180.0

    /** La polilínea gira en [posicionM] más de 30 grados. */
    fun esGiro(
        polilinea: List<PuntoGeo>,
        acumulada: DoubleArray,
        posicionM: Double,
    ): Boolean = en(polilinea, acumulada, posicionM) > GIRO_MINIMO_GRADOS

    /** Giro (grados, 0..180) de [polilinea] en [posicionM]: rumbo de los 20 m previos contra los 20 m siguientes. */
    private fun en(
        polilinea: List<PuntoGeo>,
        acumulada: DoubleArray,
        posicionM: Double,
    ): Double {
        val total = acumulada.last()
        val antes = puntoEn(polilinea, acumulada, (posicionM - VENTANA_M).coerceAtLeast(0.0))
        val centro = puntoEn(polilinea, acumulada, posicionM)
        val despues = puntoEn(polilinea, acumulada, (posicionM + VENTANA_M).coerceAtMost(total))
        val cosLat = cos(Math.toRadians(centro.lat))
        val ax = (centro.lon - antes.lon) * cosLat
        val ay = centro.lat - antes.lat
        val bx = (despues.lon - centro.lon) * cosLat
        val by = despues.lat - centro.lat
        val na = hypot(ax, ay) * METROS_POR_GRADO
        val nb = hypot(bx, by) * METROS_POR_GRADO
        if (na < LADO_MINIMO_M || nb < LADO_MINIMO_M) return 0.0
        val coseno = ((ax * bx + ay * by) / (hypot(ax, ay) * hypot(bx, by))).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(coseno))
    }

    private fun puntoEn(
        polilinea: List<PuntoGeo>,
        acumulada: DoubleArray,
        posicionM: Double,
    ): PuntoGeo {
        val k = acumulada.binarySearch(posicionM)
        if (k >= 0) return polilinea[k]
        val i = (-k - 2).coerceIn(0, polilinea.size - 2)
        val largo = acumulada[i + 1] - acumulada[i]
        val t = if (largo > 0.0) ((posicionM - acumulada[i]) / largo).coerceIn(0.0, 1.0) else 0.0
        val a = polilinea[i]
        val b = polilinea[i + 1]
        return PuntoGeo(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon))
    }
}
