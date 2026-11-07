package app.trombito.rallycopilot.motor.notas

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt

private const val PORCIENTO = 100.0

/** Ventana del suavizado gaussiano: ±3 sigmas; el exponente de la campana es −z²/2. */
private const val SIGMAS_VENTANA = 3.0
private const val EXPONENTE_GAUSS = -0.5

/**
 * Tipo de rasante: cresta, cambio brusco de pendiente convexo que no es cresta, cresta con caída tan brusca que
 * salta, o compresión (vaguada cóncava marcada, en la que el coche se hunde).
 */
enum class TipoRasante { RASANTE, CAMBIO_RASANTE, SALTO, COMPRESION }

/** Rasante detectada; [posicionM] son metros desde el inicio de la ruta. */
data class Rasante(
    val posicionM: Double,
    val tipo: TipoRasante,
)

/**
 * Umbrales de [DetectorRasantes]. Las pendientes y sus variaciones van en puntos porcentuales (% = m por 100 m);
 * las ventanas son distancias máximas (exclusivas) entre las dos pendientes comparadas.
 *
 * [variacionCompresionPuntos] es la subida de pendiente (cóncava) que hace una compresión en [ventanaCompresionM].
 * Es más exigente que [variacionCambioPuntos] a propósito: una vaguada se nota menos que una pérdida de
 * visibilidad y solo interesa avisar de las marcadas: 10 puntos (p. ej. de -5 % a +5 % en 40 m) son 2 más que el
 * cambio convexo y, en las dos trazas de test (Ma-2141 y Ma-10) dejan 5 compresiones frente a 22 cambios convexos
 * (con 8 salían 13, muchas en vaguadas suaves del MDT).
 */
data class UmbralesRasantes(
    val pasoM: Double = 5.0,
    val sigmaM: Double = 15.0,
    val pendienteSubidaCrestaPct: Double = 4.0,
    val pendienteBajadaCrestaPct: Double = 2.0,
    val ventanaCrestaM: Double = 60.0,
    val variacionCambioPuntos: Double = 8.0,
    val ventanaCambioM: Double = 40.0,
    val variacionSaltoPuntos: Double = 20.0,
    val ventanaSaltoM: Double = 30.0,
    val variacionCompresionPuntos: Double = 10.0,
    val ventanaCompresionM: Double = 40.0,
    val separacionM: Double = 60.0,
) {
    init {
        require(pasoM > 0.0 && sigmaM >= 0.0 && separacionM >= 0.0) { "Paso, sigma o separación inválidos" }
    }
}

/**
 * Detecta rasantes sobre un perfil de alturas equiespaciadas (típicamente [AlturasRuta]): suaviza con un núcleo
 * gaussiano (reflejo impar en los extremos, que conserva las rampas), deriva la pendiente y busca crestas
 * (la pendiente pasa de subida fuerte a bajada en poca distancia), saltos (crestas con caída brusca) y cambios de
 * pendiente convexos (la pendiente baja y quita visibilidad) y compresiones (el cambio simétrico, cóncavo: la
 * pendiente sube con fuerza). Entre dos rasantes a menos de [UmbralesRasantes.separacionM] solo queda la de mayor
 * prioridad (SALTO > RASANTE > CAMBIO_RASANTE = COMPRESION; a igual prioridad, la primera). Puro y determinista.
 */
class DetectorRasantes(
    private val umbrales: UmbralesRasantes = UmbralesRasantes(),
) {
    /** Rasantes de [alturasM] (una por [UmbralesRasantes.pasoM] metros), ordenadas por posición. */
    fun detectar(alturasM: List<Double>): List<Rasante> {
        if (alturasM.size < MIN_MUESTRAS) return emptyList()
        val suave = Pendientes.suavizar(alturasM.toDoubleArray(), umbrales.pasoM, umbrales.sigmaM)
        val pendiente = Pendientes.derivar(suave, umbrales.pasoM)
        val crestas = crestas(pendiente)
        val convexos = cambios(pendiente, crestas, 1.0, umbrales.variacionCambioPuntos, umbrales.ventanaCambioM)
        val concavos =
            cambios(pendiente, emptyList(), -1.0, umbrales.variacionCompresionPuntos, umbrales.ventanaCompresionM)
        return deduplicar(
            crestas.map { it.rasante } +
                convexos.map { Rasante(it, TipoRasante.CAMBIO_RASANTE) } +
                concavos.map { Rasante(it, TipoRasante.COMPRESION) },
        )
    }

    private class Cresta(
        val rasante: Rasante,
        val indice: Double,
    )

    /** Número máximo de muestras de separación entre dos pendientes cuya distancia sea menor que [ventanaM]. */
    private fun saltos(ventanaM: Double): Int = (ceil(ventanaM / umbrales.pasoM).toInt() - 1).coerceAtLeast(1)

    private fun crestas(p: DoubleArray): List<Cresta> {
        val resultado = ArrayList<Cresta>()
        for (c in 1 until p.size) {
            val cruza = p[c - 1] > 0.0 && p[c] <= 0.0
            val tipo = if (cruza) clasificarCresta(p, c) else null
            if (tipo != null) {
                val indice = (c - 1) + p[c - 1] / (p[c - 1] - p[c])
                resultado.add(Cresta(Rasante(indice * umbrales.pasoM, tipo), indice))
            }
        }
        return resultado
    }

    /** Tipo de la cresta cuya pendiente cruza el cero entre `c - 1` y `c`, o null si no cumple los umbrales. */
    private fun clasificarCresta(
        p: DoubleArray,
        c: Int,
    ): TipoRasante? {
        val maxCresta = saltos(umbrales.ventanaCrestaM)
        val maxSalto = saltos(umbrales.ventanaSaltoM)
        val ventana = maxOf(maxCresta, maxSalto)
        var esCresta = false
        var esSalto = false
        for (i in maxOf(0, c - ventana) until c) {
            for (j in c..minOf(p.lastIndex, i + ventana)) {
                val sube = p[i] >= umbrales.pendienteSubidaCrestaPct
                val baja = p[j] <= -umbrales.pendienteBajadaCrestaPct
                esCresta = esCresta || (j - i <= maxCresta && sube && baja)
                esSalto = esSalto || (j - i <= maxSalto && p[i] - p[j] >= umbrales.variacionSaltoPuntos)
            }
        }
        return when {
            esCresta && esSalto -> TipoRasante.SALTO
            esCresta -> TipoRasante.RASANTE
            else -> null
        }
    }

    /** Ventana de pendientes `[i, j]` y la caída de pendiente entre ellas. */
    private class Ventana(
        val i: Int,
        val j: Int,
        val delta: Double,
    )

    /**
     * La ventana que empieza en [i] con mayor variación de pendiente en el sentido de [signo] (1 = caída, convexo;
     * -1 = subida, cóncavo); j = -1 si no hay.
     */
    private fun mejorVentana(
        p: DoubleArray,
        i: Int,
        maxVentana: Int,
        signo: Double,
    ): Ventana {
        var mejor = Ventana(i, -1, 0.0)
        for (j in i + 1..minOf(p.lastIndex, i + maxVentana)) {
            val d = signo * (p[i] - p[j])
            if (d > mejor.delta) mejor = Ventana(i, j, d)
        }
        return mejor
    }

    /** Posiciones de los cambios de pendiente de sentido [signo] de [umbral] puntos o más en [ventanaM]. */
    private fun cambios(
        p: DoubleArray,
        crestas: List<Cresta>,
        signo: Double,
        umbral: Double,
        ventanaM: Double,
    ): List<Double> {
        val maxVentana = saltos(ventanaM)
        val resultado = ArrayList<Double>()
        var mejorRacha: Ventana? = null
        for (i in 0..p.size) {
            val v =
                if (i < p.size) {
                    mejorVentana(p, i, maxVentana, signo).takeIf { it.delta >= umbral }
                } else {
                    null
                }
            val actual = mejorRacha
            when {
                v != null -> {
                    mejorRacha = if (actual == null || v.delta > actual.delta) v else actual
                }

                actual != null -> {
                    cerrarRacha(actual.i, actual.j, crestas)?.let { resultado.add(it) }
                    mejorRacha = null
                }
            }
        }
        return resultado
    }

    /** Racha de ventanas con variación suficiente: cambio en el centro de la mejor, salvo que contenga una cresta. */
    private fun cerrarRacha(
        i: Int,
        j: Int,
        crestas: List<Cresta>,
    ): Double? {
        val contiene = crestas.any { it.indice >= i - 1 && it.indice <= j }
        return if (contiene) null else (i + j) / 2.0 * umbrales.pasoM
    }

    private fun deduplicar(candidatas: List<Rasante>): List<Rasante> {
        val ordenadas =
            candidatas.sortedWith(compareByDescending<Rasante> { it.tipo.prioridad() }.thenBy { it.posicionM })
        val elegidas = ArrayList<Rasante>()
        for (r in ordenadas) {
            if (elegidas.none { abs(it.posicionM - r.posicionM) < umbrales.separacionM }) elegidas.add(r)
        }
        return elegidas.sortedBy { it.posicionM }
    }

    private fun TipoRasante.prioridad(): Int =
        when (this) {
            TipoRasante.SALTO -> 2
            TipoRasante.RASANTE -> 1
            TipoRasante.CAMBIO_RASANTE, TipoRasante.COMPRESION -> 0
        }

    private companion object {
        const val MIN_MUESTRAS = 3
    }
}

/** Suavizado gaussiano y derivada de un perfil de alturas. */
private object Pendientes {
    fun suavizar(
        h: DoubleArray,
        pasoM: Double,
        sigmaM: Double,
    ): DoubleArray {
        if (sigmaM == 0.0) return h
        val semiancho = (SIGMAS_VENTANA * sigmaM / pasoM).roundToInt().coerceAtLeast(1)
        val pesos =
            DoubleArray(2 * semiancho + 1) {
                val z = (it - semiancho) * pasoM / sigmaM
                exp(EXPONENTE_GAUSS * z * z)
            }
        val n = h.size

        fun valor(j: Int): Double =
            when {
                j < 0 -> 2.0 * h[0] - h[minOf(-j, n - 1)]
                j >= n -> 2.0 * h[n - 1] - h[maxOf(2 * (n - 1) - j, 0)]
                else -> h[j]
            }
        return DoubleArray(n) { i ->
            var suma = 0.0
            var peso = 0.0
            for (j in i - semiancho..i + semiancho) {
                val w = pesos[j - i + semiancho]
                suma += w * valor(j)
                peso += w
            }
            suma / peso
        }
    }

    /** Pendiente en % por diferencias centrales (unilaterales en los extremos). */
    fun derivar(
        g: DoubleArray,
        pasoM: Double,
    ): DoubleArray {
        val n = g.size
        return DoubleArray(n) { i ->
            val a = maxOf(0, i - 1)
            val b = minOf(n - 1, i + 1)
            PORCIENTO * (g[b] - g[a]) / ((b - a) * pasoM)
        }
    }
}
