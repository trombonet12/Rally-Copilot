package app.trombito.rallycopilot.motor.analisis

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.Rumbo
import app.trombito.rallycopilot.motor.notas.DetectorCurvas
import app.trombito.rallycopilot.motor.notas.GeneradorNotas
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.vias.Via
import app.trombito.rallycopilot.motor.vias.idDeVia
import app.trombito.rallycopilot.motor.vias.primeraRef
import kotlin.math.ceil

/** Número de grados de nota (1 la más cerrada, 6 la más suave). */
private const val NUM_GRADOS = 6

/**
 * Curvas contadas por categoría de nota: [porGrado] tiene 6 valores (el índice 0 es el grado 1), [escuadras] son las
 * curvas de ángulo recto cerrado y [horquillas] las de más de 180°. Las escuadras y las horquillas no entran en los
 * grados, como en la hoja de notas.
 */
data class CurvasPorGrado(
    val porGrado: List<Int> = List(NUM_GRADOS) { 0 },
    val escuadras: Int = 0,
    val horquillas: Int = 0,
) {
    init {
        require(porGrado.size == NUM_GRADOS) { "Se esperaban $NUM_GRADOS grados: ${porGrado.size}" }
    }

    /** Todas las curvas, de cualquier categoría. */
    val total: Int get() = porGrado.sum() + escuadras + horquillas

    /** Curvas del [grado] (1..6). */
    fun deGrado(grado: Int): Int = porGrado[grado - 1]

    operator fun plus(otras: CurvasPorGrado) =
        CurvasPorGrado(
            porGrado = List(NUM_GRADOS) { porGrado[it] + otras.porGrado[it] },
            escuadras = escuadras + otras.escuadras,
            horquillas = horquillas + otras.horquillas,
        )

    companion object {
        val VACIO = CurvasPorGrado()

        /** Cuenta las curvas de [notas]: grado nulo = horquilla, modificador de escuadra = escuadra. */
        fun de(notas: List<Nota>): CurvasPorGrado {
            val grados = IntArray(NUM_GRADOS)
            var escuadras = 0
            var horquillas = 0
            for (nota in notas) {
                val grado = nota.grado
                when {
                    Modificador.ESCUADRA in nota.modificadores -> escuadras++
                    grado == null -> horquillas++
                    else -> grados[grado - 1]++
                }
            }
            return CurvasPorGrado(grados.toList(), escuadras, horquillas)
        }
    }
}

/**
 * Lo que un recorrido hizo sobre una vía: los [metros] que la recorrió, las [curvas] que tomó en ella y su reparto por
 * grado ([grados], con el mismo total que [curvas]; vacío si quien lo construye no lo sabe).
 */
class UsoVia(
    val via: Via,
    val metros: Double,
    val curvas: Int,
    val grados: CurvasPorGrado = CurvasPorGrado.VACIO,
)

/**
 * Análisis de un recorrido: sus [metros] (la polilínea, menos los saltos de GPS), los [usos] de cada vía (una entrada
 * por vía, aunque el recorrido tenga muchos puntos en ella) y sus [curvas]. Es lo que se guarda en caché por
 * recorrido para no repetir el cálculo.
 */
class AnalisisRecorrido(
    val metros: Double,
    val usos: List<UsoVia>,
    val curvas: CurvasPorGrado,
)

/** Una vía conducida: en cuántos recorridos aparece ([pasadas], una por recorrido), sus [metros] y [curvas] totales. */
data class ViaConducida(
    val via: Via,
    val pasadas: Int,
    val metros: Double,
    val curvas: Int,
)

/**
 * Una carretera: las vías que comparten `ref` (o, sin ella, nombre; ver [FichasCarreteras.idCarretera]). [pasadas] son
 * los recorridos que pasaron por alguna de sus vías.
 */
data class Carretera(
    val ref: String?,
    val nombre: String?,
    val pasadas: Int,
    val metros: Double,
    val curvas: Int,
)

/** Resultado de agregar todos los recorridos. */
data class ResumenCarreteras(
    val recorridos: Int,
    val metrosTotales: Double,
    val curvas: CurvasPorGrado,
    /** Las vías conducidas, las más repetidas primero. */
    val vias: List<ViaConducida>,
    /**
     * Las carreteras con curvas, la más revirada primero: por curvas por kilómetro, no por total (gana la que más
     * serpentea, no la más larga). Las de menos de [AgregadoCarreteras.MIN_METROS_REVIRADA] van detrás: un trozo corto
     * con una curva no es «revirado».
     */
    val carreteras: List<Carretera>,
    /**
     * Una ficha por carretera con el ritmo por sentido y la fecha del último viaje. La rellena quien conoce los
     * tiempos y las fechas de los recorridos ([FichasCarreteras.agregar]); [AgregadoCarreteras.agregar] la deja vacía.
     */
    val fichas: List<FichaCarretera> = emptyList(),
)

/**
 * Estadísticas de «Mis carreteras»: pasadas por vía, kilómetros y curvas por grado de los recorridos grabados. Puro:
 * la asignación punto → vía se recibe como función, y el análisis de cada recorrido ([analizar]) se separa de la suma
 * ([agregar]) para poder guardar el primero en caché.
 *
 * Cada recorrido se parte en trozos donde el GPS saltó más de [MAX_SALTO_M] (un túnel o una pérdida de señal no son
 * carretera), se remuestrea cada [PASO_M] metros y se asigna cada muestra a una vía. Una vía solo cuenta como pasada de
 * un recorrido si este la recorrió al menos [MIN_PASADA_M] (cruzar una calle suelta no es conducirla). Las curvas
 * salen de [DetectorCurvas] y [GeneradorNotas], las mismas que la hoja de notas, y se asignan a la vía donde cae el
 * centro de cada una.
 */
object AgregadoCarreteras {
    const val PASO_M = 20.0
    const val MIN_PASADA_M = 40.0
    const val MAX_SALTO_M = 250.0

    /** Radio con el que conviene buscar la vía de una muestra (error del GPS más ancho de la calzada). */
    const val RADIO_VIA_M = 30.0

    /** Longitud mínima de una carretera para que sus curvas por kilómetro cuenten en el orden «más revirada». */
    const val MIN_METROS_REVIRADA = 1_000.0
    private const val M_POR_KM = 1_000.0
    private const val MIN_KM_DIVISOR = 0.001
    private const val MITAD = 2.0
    private const val MITAD_DE_PASO = 0.5
    private val DETECTOR = DetectorCurvas()
    private val GENERADOR = GeneradorNotas()

    /** Vía de un punto dado el rumbo de la marcha, o null si no hay ninguna cerca. */
    fun interface ViaDe {
        fun via(
            punto: PuntoGeo,
            rumboGrados: Double,
        ): Via?
    }

    /** Muestra remuestreada: punto, rumbo del segmento y metros que representa. */
    private class Muestra(
        val punto: PuntoGeo,
        val rumbo: Double,
    )

    /** Acumulador por vía dentro de un recorrido. */
    private class UsoMutable(
        val via: Via,
    ) {
        var metros = 0.0
        var curvas = 0
        var grados = CurvasPorGrado.VACIO
    }

    fun analizar(
        puntos: List<PuntoGeo>,
        viaDe: ViaDe,
    ): AnalisisRecorrido {
        var metros = 0.0
        var curvas = CurvasPorGrado.VACIO
        val usos = LinkedHashMap<Long, UsoMutable>()
        for (trozo in partirEnSaltos(puntos)) {
            val largo = trozo.zipWithNext { a, b -> a.distanciaA(b) }.sum()
            metros += largo
            val notas = analizarTrozo(trozo, largo, viaDe, usos)
            curvas += CurvasPorGrado.de(notas)
        }
        val propios =
            usos.values
                .filter { it.metros >= MIN_PASADA_M }
                .map { UsoVia(it.via, it.metros, it.curvas, it.grados) }
        return AnalisisRecorrido(metros, propios, curvas)
    }

    /** Suma a [usos] los metros y las curvas de un trozo continuo de [largo] metros y devuelve sus notas. */
    private fun analizarTrozo(
        trozo: List<PuntoGeo>,
        largo: Double,
        viaDe: ViaDe,
        usos: MutableMap<Long, UsoMutable>,
    ): List<Nota> {
        val muestras = muestrear(trozo)
        if (muestras.isEmpty()) return emptyList()
        val paso = largo / muestras.size
        val vias = muestras.map { viaDe.via(it.punto, it.rumbo) }
        for (via in vias.filterNotNull()) usos.getOrPut(via.id) { UsoMutable(via) }.metros += paso
        val notas = GENERADOR.generar(DETECTOR.detectar(trozo))
        for (nota in notas) {
            val centro = (nota.posicionM + nota.finM) / MITAD
            val via = vias[(centro / paso).toInt().coerceIn(0, vias.lastIndex)]
            if (via != null) {
                usos[via.id]?.let {
                    it.curvas++
                    it.grados += CurvasPorGrado.de(listOf(nota))
                }
            }
        }
        return notas
    }

    /** Analiza cada recorrido de [recorridos] y los suma. */
    fun resumir(
        recorridos: List<List<PuntoGeo>>,
        viaDe: ViaDe,
    ): ResumenCarreteras = agregar(recorridos.map { analizar(it, viaDe) })

    fun agregar(analisis: List<AnalisisRecorrido>): ResumenCarreteras {
        val porVia = LinkedHashMap<Long, ViaConducida>()
        val porCarretera = LinkedHashMap<String, Carretera>()
        var curvas = CurvasPorGrado.VACIO
        for (recorrido in analisis) {
            curvas += recorrido.curvas
            for (uso in recorrido.usos) {
                val previa = porVia[uso.via.id]
                porVia[uso.via.id] =
                    ViaConducida(
                        via = uso.via,
                        pasadas = (previa?.pasadas ?: 0) + 1,
                        metros = (previa?.metros ?: 0.0) + uso.metros,
                        curvas = (previa?.curvas ?: 0) + uso.curvas,
                    )
            }
            sumarCarreteras(recorrido.usos, porCarretera)
        }
        return ResumenCarreteras(
            recorridos = analisis.size,
            metrosTotales = analisis.sumOf { it.metros },
            curvas = curvas,
            vias =
                porVia.values.sortedWith(
                    compareByDescending<ViaConducida> { it.pasadas }.thenByDescending { it.metros },
                ),
            carreteras =
                porCarretera.values
                    .filter { it.curvas > 0 }
                    .sortedWith(
                        compareByDescending<Carretera> { it.metros >= MIN_METROS_REVIRADA }
                            .thenByDescending { it.curvas / (it.metros / M_POR_KM).coerceAtLeast(MIN_KM_DIVISOR) }
                            .thenByDescending { it.metros },
                    ),
        )
    }

    /** Suma a [destino] las carreteras de un recorrido: una pasada por recorrido y carretera. */
    private fun sumarCarreteras(
        usos: List<UsoVia>,
        destino: MutableMap<String, Carretera>,
    ) {
        val deEsteRecorrido =
            usos
                .mapNotNull { uso -> claveCarretera(uso.via)?.let { it to uso } }
                .groupBy({ it.first }, { it.second })
        for ((clave, grupo) in deEsteRecorrido) {
            val previa = destino[clave]
            val principal = grupo.maxBy { it.metros }.via
            destino[clave] =
                Carretera(
                    ref = previa?.ref ?: primeraRef(principal.ref),
                    nombre = previa?.nombre ?: principal.nombre?.trim()?.takeIf { it.isNotEmpty() },
                    pasadas = (previa?.pasadas ?: 0) + 1,
                    metros = (previa?.metros ?: 0.0) + grupo.sumOf { it.metros },
                    curvas = (previa?.curvas ?: 0) + grupo.sumOf { it.curvas },
                )
        }
    }

    /**
     * Clave de agrupación: la primera ref normalizada si la hay y, si no, el nombre; una vía anónima no es una
     * carretera de la lista. Es la misma identidad que la de los tramos de vía guardados
     * ([FichasCarreteras.idCarretera]).
     */
    private fun claveCarretera(via: Via): String? = idDeVia(via.ref, via.nombre)

    /** Parte la polilínea donde dos puntos seguidos distan más de [MAX_SALTO_M]; descarta los trozos de un punto. */
    private fun partirEnSaltos(puntos: List<PuntoGeo>): List<List<PuntoGeo>> {
        val trozos = mutableListOf<List<PuntoGeo>>()
        var actual = ArrayList<PuntoGeo>()
        for (p in puntos) {
            val ultimo = actual.lastOrNull()
            if (ultimo != null && ultimo.distanciaA(p) > MAX_SALTO_M) {
                if (actual.size >= 2) trozos += actual
                actual = ArrayList()
            }
            actual += p
        }
        if (actual.size >= 2) trozos += actual
        return trozos
    }

    /** Reparte [trozo] en trozos iguales de unos [PASO_M] metros y devuelve el punto medio de cada uno con su rumbo. */
    private fun muestrear(trozo: List<PuntoGeo>): List<Muestra> {
        val total = trozo.zipWithNext { a, b -> a.distanciaA(b) }.sum()
        if (total <= 0.0) return emptyList()
        val n = ceil(total / PASO_M).toInt().coerceAtLeast(1)
        val resultado = ArrayList<Muestra>(n)
        var segmento = 0
        var antesM = 0.0
        for (i in 0 until n) {
            val objetivo = total * (i + MITAD_DE_PASO) / n
            while (segmento < trozo.size - 2 && antesM + trozo[segmento].distanciaA(trozo[segmento + 1]) < objetivo) {
                antesM += trozo[segmento].distanciaA(trozo[segmento + 1])
                segmento++
            }
            val a = trozo[segmento]
            val b = trozo[segmento + 1]
            val largo = a.distanciaA(b)
            val t = if (largo > 0.0) ((objetivo - antesM) / largo).coerceIn(0.0, 1.0) else 0.0
            val punto = PuntoGeo(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
            resultado += Muestra(punto, Rumbo.entre(a, b))
        }
        return resultado
    }
}
