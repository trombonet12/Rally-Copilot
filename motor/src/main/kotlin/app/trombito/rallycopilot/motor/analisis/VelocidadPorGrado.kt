package app.trombito.rallycopilot.motor.analisis

import app.trombito.rallycopilot.motor.notas.DetectorCurvas
import app.trombito.rallycopilot.motor.notas.GeneradorNotas
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.Nota

/** Número de grados de nota (1 la más cerrada, 6 la más suave). */
private const val NUM_GRADOS = 6

private const val KMH_POR_M_S = 3.6
private const val MS_POR_S = 1_000.0

/** Curvas con velocidad de paso medida ([curvas]) y la suma de sus velocidades mínimas ([sumaKmh]). */
data class PasoCurvas(
    val curvas: Int = 0,
    val sumaKmh: Double = 0.0,
) {
    /** Velocidad media de paso (km/h): media de las velocidades mínimas de cada curva; null sin curvas. */
    val velMediaKmh: Double? get() = if (curvas > 0) sumaKmh / curvas else null

    operator fun plus(otro: PasoCurvas) = PasoCurvas(curvas + otro.curvas, sumaKmh + otro.sumaKmh)
}

/**
 * Velocidad de paso por curva agregada por categoría de nota: [porGrado] tiene 6 valores (el índice 0 es el grado 1) y
 * [horquillas] las curvas de más de 180°. Las escuadras no entran (son un ángulo recto cerrado, no un «tres» típico).
 * Se guardan cuentas y sumas (no medias), así que es acumulable entre recorridos con [mas] o [combinar]; solo hay
 * números simples, listos para serializar.
 */
data class VelocidadPorGrado(
    val porGrado: List<PasoCurvas> = List(NUM_GRADOS) { PasoCurvas() },
    val horquillas: PasoCurvas = PasoCurvas(),
) {
    init {
        require(porGrado.size == NUM_GRADOS) { "Se esperaban $NUM_GRADOS grados: ${porGrado.size}" }
    }

    /** Todas las curvas medidas, de cualquier categoría. */
    val curvas: Int get() = porGrado.sumOf { it.curvas } + horquillas.curvas

    /** Paso de las curvas del [grado] (1..6). */
    fun deGrado(grado: Int): PasoCurvas = porGrado[grado - 1]

    /** Suma lo medido en [otra] a esto (acumula recorridos). */
    fun mas(otra: VelocidadPorGrado) =
        VelocidadPorGrado(
            porGrado = List(NUM_GRADOS) { porGrado[it] + otra.porGrado[it] },
            horquillas = horquillas + otra.horquillas,
        )

    operator fun plus(otra: VelocidadPorGrado) = mas(otra)

    companion object {
        val VACIO = VelocidadPorGrado()

        /** Velocidad mínima (km/h) por debajo de la cual la curva se descarta (parada o ruido, no es paso). */
        const val MIN_KMH_CURVA = 10.0

        /** Hueco de GPS (ms) entre dos fijaciones seguidas que descarta la curva donde cae. */
        const val MAX_HUECO_CURVA_MS = 5_000L

        /** Margen (m) a cada lado de la curva donde también se mira la velocidad. */
        const val MARGEN_M = 10.0

        private val DETECTOR = DetectorCurvas()
        private val GENERADOR = GeneradorNotas()

        /** Suma varios agregados en uno. */
        fun combinar(agregados: Iterable<VelocidadPorGrado>): VelocidadPorGrado =
            agregados.fold(VACIO) { acc, otro -> acc.mas(otro) }

        /**
         * Mide la velocidad mínima de paso de cada curva de [puntos] (la traza grabada, con tiempo) y la agrupa por
         * grado. Las curvas salen del mismo detector y generador que las notas. Se parte la traza donde el GPS saltó
         * más de [AgregadoCarreteras.MAX_SALTO_M] y se descartan los puntos con el tiempo parado o retrocediendo. La
         * velocidad de cada punto se suaviza con la mediana de tres puntos seguidos para matar picos del
         * GPS; una curva cuenta su mínima y se descarta si esta baja de [MIN_KMH_CURVA] o si hay un hueco de más de
         * [MAX_HUECO_CURVA_MS] en ella.
         */
        fun analizar(puntos: List<PuntoTiempo>): VelocidadPorGrado =
            combinar(partirEnSaltos(crecientes(puntos)).map { analizarTrozo(it) })

        /** Quita los puntos cuyo tiempo no avanza respecto al último que se conserva. */
        private fun crecientes(puntos: List<PuntoTiempo>): List<PuntoTiempo> {
            val resultado = ArrayList<PuntoTiempo>(puntos.size)
            for (p in puntos) if (resultado.isEmpty() || p.tiempoMs > resultado.last().tiempoMs) resultado += p
            return resultado
        }

        private fun partirEnSaltos(puntos: List<PuntoTiempo>): List<List<PuntoTiempo>> {
            val trozos = mutableListOf<List<PuntoTiempo>>()
            var actual = ArrayList<PuntoTiempo>()
            for (p in puntos) {
                val ultimo = actual.lastOrNull()
                if (ultimo != null && ultimo.punto.distanciaA(p.punto) > AgregadoCarreteras.MAX_SALTO_M) {
                    if (actual.size >= 2) trozos += actual
                    actual = ArrayList()
                }
                actual += p
            }
            if (actual.size >= 2) trozos += actual
            return trozos
        }

        private fun analizarTrozo(trozo: List<PuntoTiempo>): VelocidadPorGrado {
            val notas = GENERADOR.generar(DETECTOR.detectar(trozo.map { it.punto }))
            if (notas.isEmpty()) return VACIO
            val inicios = DoubleArray(trozo.size)
            for (i in 1 until trozo.size) inicios[i] = inicios[i - 1] + trozo[i - 1].punto.distanciaA(trozo[i].punto)
            val crudas = DoubleArray(trozo.size) { velocidadPunto(trozo, it) }
            val suaves = DoubleArray(trozo.size) { mediana3(crudas, it) }
            return notas
                .filter { Modificador.ESCUADRA !in it.modificadores }
                .mapNotNull { nota -> minimaEnCurva(nota, trozo, inicios, suaves)?.let { medida(nota.grado, it) } }
                .fold(VACIO) { acc, medida -> acc.mas(medida) }
        }

        /**
         * Velocidad mínima (km/h) de la [nota] según los puntos de [trozo] (con su distancia acumulada [inicios] y su
         * velocidad suavizada [suaves]); null si no hay puntos en la curva, hay un hueco de GPS o baja de
         * [MIN_KMH_CURVA].
         */
        private fun minimaEnCurva(
            nota: Nota,
            trozo: List<PuntoTiempo>,
            inicios: DoubleArray,
            suaves: DoubleArray,
        ): Double? {
            val rango = (nota.posicionM - MARGEN_M)..(nota.finM + MARGEN_M)
            val puntosEnCurva = trozo.indices.filter { inicios[it] in rango }
            val primero = puntosEnCurva.firstOrNull() ?: return null
            // Los pasos que tocan la curva: del punto anterior al primero al siguiente al último.
            val pasos = maxOf(0, primero - 1) until minOf(trozo.lastIndex, puntosEnCurva.last() + 1)
            val hayHueco = pasos.any { trozo[it + 1].tiempoMs - trozo[it].tiempoMs > MAX_HUECO_CURVA_MS }
            return puntosEnCurva.minOf { suaves[it] }.takeIf { !hayHueco && it >= MIN_KMH_CURVA }
        }

        private fun medida(
            grado: Int?,
            kmh: Double,
        ): VelocidadPorGrado {
            val paso = PasoCurvas(1, kmh)
            return if (grado == null) {
                VelocidadPorGrado(horquillas = paso)
            } else {
                VelocidadPorGrado(porGrado = List(NUM_GRADOS) { if (it == grado - 1) paso else PasoCurvas() })
            }
        }

        /**
         * Velocidad (km/h) en el punto [i]: la del GPS si la trae; si no, la diferencia central entre sus vecinos
         * (distancia entre tiempo; en los extremos, con el único vecino).
         */
        private fun velocidadPunto(
            puntos: List<PuntoTiempo>,
            i: Int,
        ): Double {
            puntos[i].velocidadMs?.let { return it * KMH_POR_M_S }
            val a = puntos[maxOf(0, i - 1)]
            val b = puntos[minOf(puntos.lastIndex, i + 1)]
            val dtS = (b.tiempoMs - a.tiempoMs) / MS_POR_S
            return a.punto.distanciaA(b.punto) / dtS * KMH_POR_M_S
        }

        /** Mediana del punto [i] y sus dos vecinos (en los bordes, la media de los dos que hay). */
        private fun mediana3(
            v: DoubleArray,
            i: Int,
        ): Double {
            val ventana = (maxOf(0, i - 1)..minOf(v.lastIndex, i + 1)).map { v[it] }.sorted()
            return if (ventana.size % 2 == 1) ventana[ventana.size / 2] else (ventana[0] + ventana[1]) / 2
        }
    }
}
