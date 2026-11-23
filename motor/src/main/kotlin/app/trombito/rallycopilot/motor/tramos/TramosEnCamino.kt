package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.Rumbo
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Trozo de un tramo guardado que el camino recorre en su mismo sentido. [desdeM] y [hastaM] son metros desde el inicio
 * del camino; [geometria] es el trozo del camino entre ambos (para pintarlo), [salida] y [meta] sus extremos sobre el
 * camino (donde van las banderas). [empiezaEnSalida] / [acabaEnMeta] dicen si esos extremos son la salida / la meta
 * reales del tramo (si no, el camino entra o sale del tramo por mitad: el trozo está recortado). [tramoDesdeM] y
 * [tramoHastaM] son los mismos extremos en metros desde la salida del tramo.
 */
data class TramoEnCamino(
    val tramoId: String,
    val desdeM: Double,
    val hastaM: Double,
    val geometria: List<PuntoGeo>,
    val salida: PuntoGeo,
    val meta: PuntoGeo,
    val empiezaEnSalida: Boolean,
    val acabaEnMeta: Boolean,
    val tramoDesdeM: Double,
    val tramoHastaM: Double,
) {
    val longitudM: Double get() = hastaM - desdeM
}

/**
 * Tramos conocidos por los que pasa un camino por delante (la ruta guiada restante o, en libre, los próximos
 * kilómetros de la vía actual). Se crea una vez con las trazas guardadas (rejilla de `IndiceTrazas`) y se consulta
 * con [sobre] cada vez que cambia el camino.
 *
 * - El camino se muestrea cada [PASO_M] (10 m). Una muestra «está en» un tramo si dista [RADIO_M] (25 m) o menos de su
 *   traza y el rumbo del camino difiere menos de [DetectorSubtramo.ANGULO_GRADOS] del de la traza (mismo sentido).
 *   Solo se miran los tramos de la celda de la muestra y sus vecinas: con cientos de viajes, unos pocos por muestra.
 * - Una racha de muestras consecutivas con progreso creciente en la traza (se toleran [FALLOS_MAX] muestras fuera
 *   seguidas) es un trozo; cuenta si mide [LONGITUD_MIN_M] (300 m) o más, o el [FRACCION_MIN] (80 %) del tramo si
 *   este es más corto. Un tramo solo recorrido en parte se recorta al solape (precisión de una muestra).
 * - Sin duplicados: dos trozos cuyo intervalo en el camino se solapa [SOLAPE_DUPLICADO] (80 %) o más del mayor son el
 *   mismo trecho de carretera; queda el de más extensión y, con extensión equivalente (±[TOLERANCIA_EXTENSION_M]), el
 *   tramo más largo (un subtramo cae dentro de su tramo), y a igualdad el de menor id.
 * - Resultado ordenado por [TramoEnCamino.desdeM].
 *
 * Coste: unas [PASO_M]⁻¹ muestras por metro de camino × los tramos cercanos a cada una; sin trabajo sobre el histórico
 * lejano al camino. Determinista: sin reloj ni azar.
 */
class TramosEnCamino(
    private val tramos: List<Tramo>,
) {
    private val indice = IndiceTrazas(tramos)
    private val longitudes = tramos.associate { it.id to it.longitudM }

    /** Tramos conocidos recorridos por [camino] en su sentido, ordenados por [TramoEnCamino.desdeM]. */
    fun sobre(camino: List<PuntoGeo>): List<TramoEnCamino> {
        val medido = CaminoMedido(camino)
        if (tramos.isEmpty() || !medido.valido) return emptyList()
        val trozos = Rastreador(medido).rastrear()
        return quitarDuplicados(trozos).sortedWith(compareBy({ it.desdeM }, { it.tramoId }))
    }

    /** Racha provisional de un tramo: de una muestra a otra del camino y su progreso en la traza. */
    private class Racha(
        val indice: Int,
        val desdeM: Double,
        val progresoDesdeM: Double,
    ) {
        var hastaM = desdeM
        var progresoHastaM = progresoDesdeM
        var fallos = 0
    }

    private inner class Rastreador(
        private val camino: CaminoMedido,
    ) {
        private val rachas = HashMap<Int, Racha>()
        private val trozos = mutableListOf<TramoEnCamino>()

        fun rastrear(): List<TramoEnCamino> {
            var d = 0.0
            while (true) {
                muestra(d)
                if (d >= camino.totalM) break
                d = min(d + PASO_M, camino.totalM)
            }
            for (r in rachas.values.toList()) cerrar(r)
            return trozos
        }

        private fun muestra(d: Double) {
            val punto = camino.puntoEn(d)
            val rumbo = camino.rumboEn(d)
            val candidatos = indice.cercanos(punto)
            for (i in candidatos) {
                val progreso = progresoSiEnTramo(i, punto, rumbo)
                if (progreso != null) acierto(i, d, progreso) else fallo(i)
            }
            for (i in rachas.keys.toList()) if (i !in candidatos) fallo(i)
        }

        /** Progreso (m desde la salida del tramo) de [punto] si está en su traza y en su sentido; si no, null. */
        private fun progresoSiEnTramo(
            i: Int,
            punto: PuntoGeo,
            rumbo: Double?,
        ): Double? {
            val traza = indice.traza(i)
            val racha = rachas[i]
            val largoM = tramos[i].longitudM
            val topeM = largoM + MARGEN_EXTREMO_M
            val ventana =
                if (racha == null) {
                    -MARGEN_EXTREMO_M..topeM
                } else {
                    val desdeM = racha.progresoHastaM - VENTANA_ATRAS_M
                    desdeM..min(topeM, racha.progresoHastaM + VENTANA_ADELANTE_M)
                }
            val p =
                traza
                    .proyectar(punto, ventana)
                    ?.takeIf {
                        it.lateralM <= RADIO_M && it.progresoM in -LIMITE_EXTREMO_M..(largoM + LIMITE_EXTREMO_M)
                    }
            val mismoSentido =
                p != null && rumbo != null &&
                    Rumbo.diferencia(rumbo, traza.rumboEn(p.progresoM.coerceIn(0.0, largoM), SEMIVENTANA_RUMBO_M)) <
                    DetectorSubtramo.ANGULO_GRADOS
            return if (mismoSentido) p.progresoM.coerceIn(0.0, largoM) else null
        }

        private fun acierto(
            i: Int,
            d: Double,
            progresoM: Double,
        ) {
            val racha = rachas[i]
            if (racha != null && progresoM < racha.progresoHastaM - RETROCESO_MAX_M) {
                cerrar(racha)
                rachas[i] = Racha(i, d, progresoM)
            } else if (racha == null) {
                rachas[i] = Racha(i, d, progresoM)
            } else {
                racha.hastaM = d
                racha.progresoHastaM = max(racha.progresoHastaM, progresoM)
                racha.fallos = 0
            }
        }

        private fun fallo(i: Int) {
            val racha = rachas[i] ?: return
            racha.fallos++
            if (racha.fallos > FALLOS_MAX) cerrar(racha)
        }

        private fun cerrar(r: Racha) {
            rachas.remove(r.indice)
            val tramo = tramos[r.indice]
            val minimoM = min(LONGITUD_MIN_M, FRACCION_MIN * tramo.longitudM)
            if (r.hastaM - r.desdeM >= minimoM) trozos += camino.aTrozo(tramo, r)
        }
    }

    private fun CaminoMedido.aTrozo(
        tramo: Tramo,
        r: Racha,
    ): TramoEnCamino =
        TramoEnCamino(
            tramoId = tramo.id,
            desdeM = r.desdeM,
            hastaM = r.hastaM,
            geometria = trozo(r.desdeM, r.hastaM),
            salida = puntoEn(r.desdeM),
            meta = puntoEn(r.hastaM),
            empiezaEnSalida = r.progresoDesdeM <= EXTREMO_TOLERANCIA_M,
            acabaEnMeta = r.progresoHastaM >= tramo.metaM - EXTREMO_TOLERANCIA_M,
            tramoDesdeM = r.progresoDesdeM,
            tramoHastaM = r.progresoHastaM,
        )

    private fun longitudDe(id: String): Double = longitudes.getValue(id)

    /** Elimina los trozos que repiten el trecho de otro (ver la regla en la documentación de la clase). */
    private fun quitarDuplicados(trozos: List<TramoEnCamino>): List<TramoEnCamino> {
        val aceptados = mutableListOf<TramoEnCamino>()
        for (c in trozos.sortedByDescending { it.longitudM }) {
            val repetidos = aceptados.filter { esDuplicado(c, it) }
            if (repetidos.all { gana(c, it) }) {
                aceptados.removeAll(repetidos.toSet())
                aceptados += c
            }
        }
        return aceptados
    }

    private fun esDuplicado(
        a: TramoEnCamino,
        b: TramoEnCamino,
    ): Boolean {
        val comun = min(a.hastaM, b.hastaM) - max(a.desdeM, b.desdeM)
        return comun >= SOLAPE_DUPLICADO * max(a.longitudM, b.longitudM)
    }

    /** [c] (de extensión menor o igual que [otro]) lo sustituye: extensión equivalente y tramo más largo / menor id. */
    private fun gana(
        c: TramoEnCamino,
        otro: TramoEnCamino,
    ): Boolean {
        val equivalente = abs(c.longitudM - otro.longitudM) <= TOLERANCIA_EXTENSION_M
        val largoC = longitudDe(c.tramoId)
        val largoOtro = longitudDe(otro.tramoId)
        return equivalente && (largoC > largoOtro || (largoC == largoOtro && c.tramoId < otro.tramoId))
    }

    companion object {
        /** Distancia (m) a la traza para dar el camino por «sobre» un tramo. */
        const val RADIO_M = 25.0

        /** Longitud mínima (m) de camino sobre un tramo para que cuente. */
        const val LONGITUD_MIN_M = 300.0

        /** Fracción del tramo que basta si este es más corto que [LONGITUD_MIN_M] / [FRACCION_MIN]. */
        const val FRACCION_MIN = 0.8

        /** Separación (m) entre muestras del camino. */
        const val PASO_M = 10.0

        /** Fracción del trozo mayor que, solapada, hace duplicados a dos trozos. */
        const val SOLAPE_DUPLICADO = 0.8

        /** Diferencia (m) de extensión bajo la cual dos trozos duplicados se consideran equivalentes. */
        const val TOLERANCIA_EXTENSION_M = 25.0

        /** Margen (m) a la salida o a la meta del tramo para dar el extremo del trozo por la salida o la meta. */
        const val EXTREMO_TOLERANCIA_M = 30.0

        private const val FALLOS_MAX = 3
        private const val SEMIVENTANA_RUMBO_M = 10.0
        private const val VENTANA_ATRAS_M = 30.0
        private const val VENTANA_ADELANTE_M = 150.0
        private const val RETROCESO_MAX_M = 30.0
        private const val MARGEN_EXTREMO_M = 10.0

        /** La proyección acotada a la ventana cae justo en [MARGEN_EXTREMO_M]: se descarta un poco antes. */
        private const val LIMITE_EXTREMO_M = MARGEN_EXTREMO_M - 0.5
    }
}

/** Camino con metros acumulados: punto, rumbo y trozo a una distancia dada. */
private class CaminoMedido(
    original: List<PuntoGeo>,
) {
    private val puntos: List<PuntoGeo> = sinRepetidos(original)
    private val acumulada = DoubleArray(puntos.size)

    init {
        for (i in 1 until puntos.size) acumulada[i] = acumulada[i - 1] + puntos[i - 1].distanciaA(puntos[i])
    }

    val valido: Boolean get() = puntos.size >= 2
    val totalM: Double get() = if (valido) acumulada.last() else 0.0

    /** Punto a [d] metros del inicio (acotado). */
    fun puntoEn(d: Double): PuntoGeo {
        val i = segmentoDe(d)
        val largo = acumulada[i + 1] - acumulada[i]
        val t = if (largo > 0.0) ((d - acumulada[i]) / largo).coerceIn(0.0, 1.0) else 0.0
        val a = puntos[i]
        val b = puntos[i + 1]
        return PuntoGeo(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon))
    }

    /** Rumbo de la cuerda de ±[SEMIVENTANA_M] metros alrededor de [d]; null si el camino no tiene longitud útil. */
    fun rumboEn(d: Double): Double? {
        val a = puntoEn((d - SEMIVENTANA_M).coerceAtLeast(0.0))
        val b = puntoEn((d + SEMIVENTANA_M).coerceAtMost(totalM))
        return if (a.distanciaA(b) > RUMBO_MIN_M) Rumbo.entre(a, b) else null
    }

    /** Subpolilínea entre [desdeM] y [hastaM] metros con los vértices intermedios. */
    fun trozo(
        desdeM: Double,
        hastaM: Double,
    ): List<PuntoGeo> {
        val resultado = mutableListOf(puntoEn(desdeM))
        for (i in 1 until puntos.size) if (acumulada[i] > desdeM && acumulada[i] < hastaM) resultado += puntos[i]
        resultado += puntoEn(hastaM)
        return resultado
    }

    /** Índice del segmento que contiene [d] (búsqueda binaria; acotado a los extremos). */
    private fun segmentoDe(d: Double): Int {
        var bajo = 0
        var alto = acumulada.size - 2
        while (bajo < alto) {
            val medio = (bajo + alto) ushr 1
            if (acumulada[medio + 1] < d) bajo = medio + 1 else alto = medio
        }
        return bajo
    }

    private companion object {
        const val SEMIVENTANA_M = 10.0
        const val RUMBO_MIN_M = 1.0
        const val REPETIDO_M = 0.01

        fun sinRepetidos(puntos: List<PuntoGeo>): List<PuntoGeo> {
            val unicos = mutableListOf<PuntoGeo>()
            for (p in puntos) if (unicos.isEmpty() || unicos.last().distanciaA(p) > REPETIDO_M) unicos += p
            return unicos
        }
    }
}
