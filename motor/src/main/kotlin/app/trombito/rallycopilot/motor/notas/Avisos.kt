package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.geo.ProyeccionPlana
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.Arista
import app.trombito.rallycopilot.motor.vias.DatosNotasVias
import app.trombito.rallycopilot.motor.vias.EtiquetasVia
import app.trombito.rallycopilot.motor.vias.GrafoVias
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Tipo de aviso de la hoja de notas y lo que se canta. [frase] es la frase suelta; los avisos de precaución
 * ([esCuidado]) se cantan en una nota como «cuidado» al principio de la frase más su [palabra] (vacía si es solo
 * «cuidado»).
 */
enum class TipoAviso(
    val frase: String,
    val esCuidado: Boolean = false,
    val palabra: String = frase,
) {
    PUENTE("puente"),
    TUNEL("túnel"),
    ROTONDA("rotonda"),
    ESTRECHA("estrecha"),
    BACHE("cuidado, resalto", esCuidado = true, palabra = "resalto"),
    BADEN("cuidado badén", esCuidado = true, palabra = "badén"),
    PASO_A_NIVEL("paso a nivel"),
    STOP("stop"),
    CEDA("ceda el paso"),
    SEMAFORO("semáforo"),
    CRUCE("cruce"),
    RASANTE("rasante"),
    CAMBIO_RASANTE("cambio de rasante"),
    SALTO("salto"),
    COMPRESION("compresión"),
    CUIDADO("cuidado", esCuidado = true, palabra = ""),
}

/** Aviso en [posicionM] metros desde el inicio de la ruta. */
data class Aviso(
    val posicionM: Double,
    val tipo: TipoAviso,
)

/**
 * Datos de las vías que enriquecen la hoja: el índice espacial, el grafo y los datos de `vias-notas`; [umbrales]
 * son los del detector de rasantes.
 */
class ContextoVias(
    val indice: IndiceVias,
    val grafo: GrafoVias,
    val datosNotas: DatosNotasVias,
    val umbrales: UmbralesRasantes = UmbralesRasantes(),
)

/**
 * Avisos extraídos de una ruta (ordenados y sin duplicados del mismo tipo a menos de 30 m) y posiciones de los
 * `hazard:curve` que no se han convertido en aviso: la hoja los une a la curva cercana o los canta como «cuidado».
 */
class AvisosRuta(
    val avisos: List<Aviso>,
    val curvasPeligrosasM: List<Double>,
)

/** Ordena por posición (y tipo) y descarta los del mismo tipo a menos de [SEPARACION_MIN_M] de uno ya elegido. */
internal fun List<Aviso>.sinDuplicados(): List<Aviso> {
    val elegidos = ArrayList<Aviso>()
    for (a in sortedWith(compareBy<Aviso>({ it.posicionM }, { it.tipo.ordinal }))) {
        if (elegidos.none { it.tipo == a.tipo && abs(it.posicionM - a.posicionM) < SEPARACION_MIN_M }) elegidos.add(a)
    }
    return elegidos
}

private const val SEPARACION_MIN_M = 30.0

/**
 * Extrae los avisos de una ruta a partir de las etiquetas OSM de las vías por las que pasa (muestras cada
 * [AlturasRuta.PASO_M] m casadas con el [IndiceVias]), los nodos de aviso, los cruces del [GrafoVias] y las
 * rasantes del perfil de alturas. Puro y determinista.
 */
class ExtractorAvisos(
    private val contexto: ContextoVias,
) {
    /** Avisos de [ruta]; vacío si tiene menos de dos puntos distintos. */
    fun extraer(ruta: List<PuntoGeo>): AvisosRuta {
        val muestras = AlturasRuta.muestrear(ruta)
        if (muestras.isEmpty()) return AvisosRuta(emptyList(), emptyList())
        val tramos = LectorTramos(contexto).leer(muestras)
        val nodos = avisosDeNodos(ruta, tramos.viasRuta)
        val cruces = crucesFueraDeRotondas(ruta, tramos.rotondas, tramos.viasRuta)
        val rasantes = rasantes(ruta).filterNot { enJunta(it.posicionM, tramos.extremosEstructuraM) }
        val avisos = tramos.avisos + nodos.avisos + cruces + rasantes
        return AvisosRuta(avisos.sinDuplicados(), (tramos.curvasPeligrosasM + nodos.curvasPeligrosasM).sorted())
    }

    private fun avisosDeNodos(
        ruta: List<PuntoGeo>,
        viasRuta: Set<Long>,
    ): AvisosRuta {
        val plana = RutaPlana(ruta)
        val (curvas, resto) =
            contexto.datosNotas.nodos
                .mapNotNull { nodo ->
                    ClaseNodo.de(nodo.tipo)?.let { clase ->
                        plana
                            .proyectar(nodo.punto, RADIO_NODO_M)
                            ?.takeIf { perteneceARuta(nodo.punto, viasRuta) }
                            ?.takeIf { !esDeOtraVia(clase, nodo.punto, viasRuta) }
                            ?.let { clase to it }
                    }
                }.partition { it.first == ClaseNodo.CURVA_PELIGROSA }
        return AvisosRuta(resto.map { Aviso(it.second, it.first.aviso) }, curvas.map { it.second })
    }

    /** Un nodo que es vértice de vías solo cuenta si alguna es de la ruta (no el stop de una calle lateral). */
    private fun perteneceARuta(
        punto: PuntoGeo,
        viasRuta: Set<Long>,
    ): Boolean {
        val aristas = contexto.grafo.aristas(punto)
        return aristas.isEmpty() || aristas.any { it.via.id in viasRuta }
    }

    /**
     * STOP y CEDA de un cruce que son de la otra vía: la ruta llega desde una vía de mayor rango que las demás, o de
     * igual rango y siguiendo la misma `ref` (entra y sale por ella). Si la ruta llega por una vía de menor rango o
     * empatada sin `ref`, se avisa. Fuera de un cruce (menos de 3 ramas) siempre se avisa.
     */
    private fun esDeOtraVia(
        clase: ClaseNodo,
        punto: PuntoGeo,
        viasRuta: Set<Long>,
    ): Boolean {
        val aristas = contexto.grafo.aristas(punto)
        val esCruce = aristas.sumOf { ramas(it) } >= RAMAS_CRUCE
        return (clase == ClaseNodo.STOP || clase == ClaseNodo.CEDA) && esCruce && llegaPorLaPrincipal(aristas, viasRuta)
    }

    private fun llegaPorLaPrincipal(
        aristas: List<Arista>,
        viasRuta: Set<Long>,
    ): Boolean {
        val deRuta = aristas.filter { it.via.id in viasRuta }
        val mismaRef =
            deRuta
                .filter { it.via.ref != null }
                .groupBy { it.via.ref }
                .values
                .any { grupo -> grupo.sumOf { ramas(it) } >= 2 }
        val rangoRuta = deRuta.maxOfOrNull { GrafoVias.rangoClase(it.via.clase) } ?: 0
        val rangoOtras = aristas.filter { it.via.id !in viasRuta }.maxOfOrNull { GrafoVias.rangoClase(it.via.clase) }
        return when {
            rangoOtras == null -> false
            else -> rangoRuta > rangoOtras || (mismaRef && rangoRuta == rangoOtras)
        }
    }

    /** Direcciones en que se puede salir del nodo por esta arista (1 en un extremo de la vía, 2 en medio). */
    private fun ramas(arista: Arista): Int =
        (if (arista.indice > 0) 1 else 0) + (if (arista.indice < arista.via.puntos.size - 1) 1 else 0)

    private fun crucesFueraDeRotondas(
        ruta: List<PuntoGeo>,
        rotondas: List<ClosedFloatingPointRange<Double>>,
        viasRuta: Set<Long>,
    ): List<Aviso> =
        contexto.grafo
            .crucesConNodo(ruta)
            .filter { c ->
                rotondas.none { c.posicionM >= it.start - ANTES_ROTONDA_M && c.posicionM <= it.endInclusive }
            }.filter { c ->
                // Un nodo solo de vías ajenas a la ruta (la carretera bajo un puente) no es un cruce de la ruta.
                contexto.grafo.aristas(c.nodo).any { it.via.id in viasRuta }
            }.filterNot { esEntradaOSalidaDeAutovia(contexto.grafo.aristas(it.nodo), viasRuta) }
            .map { Aviso(it.posicionM, TipoAviso.CRUCE) }

    /** Las rasantes a [JUNTA_M] o menos de un extremo de puente o túnel son un artefacto del MDT. */
    private fun enJunta(
        posicionM: Double,
        extremosM: List<Double>,
    ): Boolean = extremosM.any { abs(it - posicionM) <= JUNTA_M }

    private fun rasantes(ruta: List<PuntoGeo>): List<Aviso> {
        val alturas = AlturasRuta.calcular(ruta, contexto.indice, contexto.datosNotas.perfiles)
        return DetectorRasantes(contexto.umbrales).detectar(alturas).map {
            Aviso(
                it.posicionM,
                when (it.tipo) {
                    TipoRasante.RASANTE -> TipoAviso.RASANTE
                    TipoRasante.CAMBIO_RASANTE -> TipoAviso.CAMBIO_RASANTE
                    TipoRasante.SALTO -> TipoAviso.SALTO
                    TipoRasante.COMPRESION -> TipoAviso.COMPRESION
                },
            )
        }
    }

    private companion object {
        const val RADIO_NODO_M = 12.0
        const val ANTES_ROTONDA_M = 25.0
        const val RAMAS_CRUCE = 3
        const val JUNTA_M = 30.0
    }
}

/**
 * En autovía no se cantan cruces: la vía de la ruta en el nodo es motorway, trunk o un `*_link`, o todas las demás
 * ramas son `*_link` (las salidas que toma la ruta ya las canta la navegación).
 */
private fun esEntradaOSalidaDeAutovia(
    aristas: List<Arista>,
    viasRuta: Set<Long>,
): Boolean {
    val (deRuta, otras) = aristas.partition { it.via.id in viasRuta }
    return deRuta.any { esAutovia(it.via.clase) } ||
        (otras.isNotEmpty() && otras.all { it.via.clase.endsWith("_link") })
}

private fun esAutovia(clase: String): Boolean = clase == "motorway" || clase == "trunk" || clase.endsWith("_link")

/** Clasificación de los tipos crudos de los nodos `N` de `vias-notas`. */
private enum class ClaseNodo(
    val aviso: TipoAviso,
) {
    BACHE(TipoAviso.BACHE),
    BADEN(TipoAviso.BADEN),
    PASO_A_NIVEL(TipoAviso.PASO_A_NIVEL),
    STOP(TipoAviso.STOP),
    CEDA(TipoAviso.CEDA),
    SEMAFORO(TipoAviso.SEMAFORO),
    CUIDADO(TipoAviso.CUIDADO),
    CURVA_PELIGROSA(TipoAviso.CUIDADO),
    ;

    companion object {
        private val BACHES =
            setOf(
                "bump",
                "hump",
                "table",
                "cushion",
                "rumble_strip",
                "rumble_strips",
                "speed_bump",
                "speed_hump",
                "speed_table",
            )

        /** Clase del nodo de tipo [tipo], o null si no merece aviso. */
        fun de(tipo: String): ClaseNodo? =
            when {
                tipo == "hazard:curve" -> CURVA_PELIGROSA
                tipo.startsWith("hazard:") || tipo == "hazard" -> CUIDADO
                tipo in BACHES -> BACHE
                tipo == "dip" || tipo == "ford" -> BADEN
                tipo == "level_crossing" || tipo == "railway_crossing" -> PASO_A_NIVEL
                tipo == "stop" -> STOP
                tipo == "give_way" -> CEDA
                tipo == "traffic_signals" -> SEMAFORO
                else -> null
            }
    }
}

/**
 * Resultado de recorrer las muestras: avisos de tramo, rotondas (rangos en metros), vías de la ruta, curvas peligrosas
 * y extremos (inicio y fin) de los puentes y túneles, donde el MDT da rasantes falsas.
 */
private class Tramos(
    val avisos: List<Aviso>,
    val rotondas: List<ClosedFloatingPointRange<Double>>,
    val viasRuta: Set<Long>,
    val curvasPeligrosasM: List<Double>,
    val extremosEstructuraM: List<Double>,
)

/**
 * Recorre las muestras de la ruta y emite un aviso al empezar cada tramo con puente, túnel, rotonda o vía estrecha,
 * y al empezar un `hazard` de vía. Las muestras sin vía cercana conservan el estado anterior (no cortan el tramo).
 */
private class LectorTramos(
    private val contexto: ContextoVias,
) {
    private val activos = BooleanArray(TIPOS.size)
    private val inicios = DoubleArray(TIPOS.size)
    private val avisos = ArrayList<Aviso>()
    private val rotondas = ArrayList<ClosedFloatingPointRange<Double>>()
    private val vias = HashSet<Long>()
    private val curvasPeligrosas = ArrayList<Double>()
    private val extremosEstructura = ArrayList<Double>()
    private var peligroPrevio: String? = null

    fun leer(muestras: List<AlturasRuta.Muestra>): Tramos {
        muestras.forEachIndexed { k, m ->
            val coincidencia = contexto.indice.cercana(m.punto, m.rumboGrados, AlturasRuta.RADIO_VIA_M)
            if (coincidencia != null) {
                vias.add(coincidencia.via.id)
                val etiquetas = contexto.datosNotas.etiquetas[coincidencia.via.id]
                val s = k * AlturasRuta.PASO_M
                actualizarTramos(s, banderas(coincidencia.via, etiquetas))
                actualizarPeligro(s, etiquetas?.hazard)
            }
        }
        val fin = (muestras.size - 1) * AlturasRuta.PASO_M
        if (activos[POS_ROTONDA]) rotondas.add(inicios[POS_ROTONDA]..fin)
        return Tramos(avisos, rotondas, vias, curvasPeligrosas, extremosEstructura)
    }

    private fun actualizarTramos(
        s: Double,
        banderas: BooleanArray,
    ) {
        for (t in TIPOS.indices) {
            if (banderas[t] && !activos[t]) {
                avisos.add(Aviso(s, TIPOS[t]))
                inicios[t] = s
                if (t in ESTRUCTURAS) extremosEstructura.add(s)
            } else if (!banderas[t] && activos[t]) {
                if (t == POS_ROTONDA) rotondas.add(inicios[t]..s)
                if (t in ESTRUCTURAS) extremosEstructura.add(s)
            }
            activos[t] = banderas[t]
        }
    }

    private fun actualizarPeligro(
        s: Double,
        peligro: String?,
    ) {
        if (peligro != null && peligro != peligroPrevio) {
            if (peligro.contains("curve")) curvasPeligrosas.add(s) else avisos.add(Aviso(s, TipoAviso.CUIDADO))
        }
        peligroPrevio = peligro
    }

    private fun banderas(
        via: Via,
        e: EtiquetasVia?,
    ): BooleanArray =
        booleanArrayOf(
            e?.bridge.let { it != null && it != "no" },
            e?.tunnel.let { it != null && it != "no" && it != "culvert" },
            e?.junction == "roundabout",
            e != null && estrecha(via, e),
        )

    /** Un carril sin sentido único, o menos de 5 m de ancho. */
    private fun estrecha(
        via: Via,
        e: EtiquetasVia,
    ): Boolean =
        (e.lanes == 1 && via.sentidoUnico == SentidoUnico.NO) || (e.width != null && e.width < ANCHO_ESTRECHA_M)

    private companion object {
        val TIPOS = listOf(TipoAviso.PUENTE, TipoAviso.TUNEL, TipoAviso.ROTONDA, TipoAviso.ESTRECHA)
        val POS_ROTONDA = TIPOS.indexOf(TipoAviso.ROTONDA)
        val ESTRUCTURAS = setOf(TIPOS.indexOf(TipoAviso.PUENTE), TIPOS.indexOf(TipoAviso.TUNEL))
        const val ANCHO_ESTRECHA_M = 5.0
    }
}

/**
 * Ruta proyectada a metros locales (misma proyección que [AlturasRuta] y [Remuestreo], así que las posiciones
 * coinciden con las de las curvas) con una rejilla de segmentos para proyectar puntos cercanos.
 */
private class RutaPlana(
    ruta: List<PuntoGeo>,
) {
    private val plano = ProyeccionPlana.deTraza(ruta)
    private val xs = DoubleArray(ruta.size) { plano.x(ruta[it]) }
    private val ys = DoubleArray(ruta.size) { plano.y(ruta[it]) }
    private val acumulada = DoubleArray(ruta.size)
    private val rejilla = HashMap<Long, MutableList<Int>>()

    init {
        for (i in 1 until ruta.size) {
            acumulada[i] = acumulada[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        }
        for (i in 0 until ruta.size - 1) {
            for (cx in celda(minOf(xs[i], xs[i + 1]))..celda(maxOf(xs[i], xs[i + 1]))) {
                for (cy in celda(minOf(ys[i], ys[i + 1]))..celda(maxOf(ys[i], ys[i + 1]))) {
                    rejilla.getOrPut(clave(cx, cy)) { ArrayList() }.add(i)
                }
            }
        }
    }

    /** Posición (m desde el inicio) de la proyección de [punto] si cae a [maxM] o menos de la ruta; si no, null. */
    fun proyectar(
        punto: PuntoGeo,
        maxM: Double,
    ): Double? {
        val px = plano.x(punto)
        val py = plano.y(punto)
        return segmentosCercanos(px, py, maxM)
            .mapNotNull { evaluar(it, px, py, maxM) }
            .minWithOrNull(compareBy({ it.distanciaM }, { it.posicionM }))
            ?.posicionM
    }

    /** Proyección de un punto sobre un segmento: distancia a él y posición a lo largo de la ruta. */
    private class Proyeccion(
        val distanciaM: Double,
        val posicionM: Double,
    )

    private fun segmentosCercanos(
        px: Double,
        py: Double,
        maxM: Double,
    ): List<Int> =
        (celda(px - maxM)..celda(px + maxM)).flatMap { cx ->
            (celda(py - maxM)..celda(py + maxM)).flatMap { cy -> rejilla[clave(cx, cy)].orEmpty() }
        }

    private fun evaluar(
        i: Int,
        px: Double,
        py: Double,
        maxM: Double,
    ): Proyeccion? {
        val t = fraccion(i, px, py)
        val d = hypot(px - (xs[i] + t * (xs[i + 1] - xs[i])), py - (ys[i] + t * (ys[i + 1] - ys[i])))
        return if (d <= maxM) Proyeccion(d, acumulada[i] + t * (acumulada[i + 1] - acumulada[i])) else null
    }

    private fun fraccion(
        i: Int,
        px: Double,
        py: Double,
    ): Double {
        val dx = xs[i + 1] - xs[i]
        val dy = ys[i + 1] - ys[i]
        val largo2 = dx * dx + dy * dy
        return if (largo2 == 0.0) 0.0 else (((px - xs[i]) * dx + (py - ys[i]) * dy) / largo2).coerceIn(0.0, 1.0)
    }

    private fun celda(metros: Double): Int = floor(metros / CELDA_M).toInt()

    private fun clave(
        cx: Int,
        cy: Int,
    ): Long = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)

    private companion object {
        const val CELDA_M = 25.0
    }
}
