package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.notas.TrazasDePrueba
import app.trombito.rallycopilot.motor.tramos.Conduccion.desplazar
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorSubtramoTest {
    /** Carretera recta de 6 km hacia el norte. */
    private val carretera = TrazasDePrueba.recta(6000.0)

    private fun tramoDe(
        id: String,
        traza: List<PuntoGeo>,
    ) = Tramo(id, traza, traza.longitudMetros())

    /** Pasada a velocidad constante [kmh] a lo largo de todo el tramo. */
    private fun pasadaA(
        tramo: Tramo,
        kmh: Double,
    ): Pasada {
        val totalMs = Math.round(tramo.longitudM / (kmh / 3.6) * 1000.0)
        return Pasada(
            tramo.id,
            listOf(MarcaTiempo(0.0, 0L), MarcaTiempo(tramo.longitudM, totalMs)),
            totalMs,
        )
    }

    private fun conducir(
        d: DetectorSubtramo,
        fijaciones: List<Fijacion>,
    ): List<SubtramoHecho> = fijaciones.flatMap { d.alFijar(it).hechos } + d.cerrar()

    @Test
    fun `entrar a mitad y salir antes de la meta de un km y medio da un hecho con su tiempo`() {
        val tramo = tramoDe("A", carretera)
        val d = DetectorSubtramo(listOf(tramo), emptyMap())
        val hechos = conducir(d, Conduccion.muestrear(carretera, 72.0, t0Ms = 7_000, desdeM = 2000.0, hastaM = 3500.0))
        val h = hechos.single()
        assertEquals("A", h.tramoRefId)
        assertCerca(2000, h.desdeM, 3.0)
        assertCerca(3500, h.hastaM, 3.0)
        assertCerca(1500, h.longitudM, 5.0)
        assertCerca(75_000, h.tiempoMs, 600.0)
        assertCerca(7_000, h.inicioFijacionMs, 1.0)
        assertNull(h.mejorMs)
    }

    @Test
    fun `menos de un kilometro no da nada`() {
        val d = DetectorSubtramo(listOf(tramoDe("A", carretera)), emptyMap())
        assertTrue(conducir(d, Conduccion.muestrear(carretera, 72.0, desdeM = 2000.0, hastaM = 2900.0)).isEmpty())
    }

    @Test
    fun `en sentido contrario no abre`() {
        val d = DetectorSubtramo(listOf(tramoDe("A", carretera)), emptyMap())
        val alReves = carretera.reversed()
        assertTrue(conducir(d, Conduccion.muestrear(alReves, 72.0, desdeM = 500.0, hastaM = 4500.0)).isEmpty())
    }

    @Test
    fun `el rumbo del GPS manda sobre la posicion`() {
        val d = DetectorSubtramo(listOf(tramoDe("A", carretera)), emptyMap())
        val fijaciones =
            Conduccion.muestrear(carretera, 72.0, desdeM = 1000.0, hastaM = 3000.0).map {
                it.copy(velocidadMs = 20.0, rumboGrados = 180.0)
            }
        assertTrue(conducir(d, fijaciones).isEmpty())
        val buenas =
            Conduccion.muestrear(carretera, 72.0, desdeM = 1000.0, hastaM = 3000.0).map {
                it.copy(velocidadMs = 20.0, rumboGrados = 5.0)
            }
        assertEquals(1, conducir(DetectorSubtramo(listOf(tramoDe("A", carretera)), emptyMap()), buenas).size)
    }

    @Test
    fun `sin rumbo conocido o parado no se abre`() {
        val d = DetectorSubtramo(listOf(tramoDe("A", carretera)), emptyMap())
        val parado = Conduccion.fijacion(Conduccion.puntoA(carretera, 2000.0), 0)
        val resultados = (0 until 20).map { d.alFijar(parado.copy(tiempoMs = it * 1000L, velocidadMs = 0.0)) }
        assertTrue(resultados.all { it.enCurso == null && it.hechos.isEmpty() })
        assertTrue(d.cerrar().isEmpty())
    }

    @Test
    fun `la pasada completa de un tramo no se emite porque ya la cronometra DetectorTramo`() {
        val tramo = tramoDe("Z", Conduccion.subtraza(carretera, 2000.0, 5000.0))
        val d = DetectorSubtramo(listOf(tramo), emptyMap())
        assertTrue(conducir(d, Conduccion.muestrear(carretera, 72.0, desdeM = 1500.0, hastaM = 5500.0)).isEmpty())
    }

    @Test
    fun `salir por el final de la traza cierra el trozo en la meta`() {
        val tramo = tramoDe("T", Conduccion.subtraza(carretera, 0.0, 3000.0))
        val d = DetectorSubtramo(listOf(tramo), emptyMap())
        val fijaciones = Conduccion.muestrear(carretera, 72.0, desdeM = 1000.0, hastaM = 4000.0)
        val hechos = fijaciones.flatMap { d.alFijar(it).hechos }
        val h = hechos.single() // ya cerrado al pasar el final, sin esperar a cerrar()
        assertCerca(1000, h.desdeM, 3.0)
        assertCerca(tramo.longitudM, h.hastaM, 0.001)
        assertCerca(100_000, h.tiempoMs, 600.0)
        assertTrue(d.cerrar().isEmpty()) // y no reabre al seguir sobre la prolongacion
    }

    @Test
    fun `dos tramos solapados dan un solo hecho con el mejor de ambos`() {
        val largo = tramoDe("largo", carretera)
        val corto = tramoDe("corto", Conduccion.subtraza(carretera, 1000.0, 5000.0))
        val pasadas =
            mapOf(
                "largo" to listOf(pasadaA(largo, 36.0)), // 1500 m en 150 s
                "corto" to listOf(pasadaA(corto, 54.0)), // 1500 m en 100 s
            )
        val d = DetectorSubtramo(listOf(largo, corto), pasadas)
        val hechos = conducir(d, Conduccion.muestrear(carretera, 72.0, desdeM = 2000.0, hastaM = 3500.0))
        val h = hechos.single()
        assertCerca(100_000, h.mejorMs!!, 500.0)
        assertCerca(75_000, h.tiempoMs, 600.0)
    }

    @Test
    fun `mejorMs es el minimo entre todas las pasadas de un tramo`() {
        val tramo = tramoDe("A", carretera)
        val pasadas = mapOf("A" to listOf(pasadaA(tramo, 36.0), pasadaA(tramo, 90.0), pasadaA(tramo, 54.0)))
        val d = DetectorSubtramo(listOf(tramo), pasadas)
        val h = conducir(d, Conduccion.muestrear(carretera, 72.0, desdeM = 2000.0, hastaM = 3500.0)).single()
        assertCerca(60_000, h.mejorMs!!, 500.0) // 1500 m a 25 m/s
    }

    @Test
    fun `un tramo en sentido contrario no aporta mejor tiempo`() {
        val ida = tramoDe("ida", carretera)
        val vuelta = tramoDe("vuelta", carretera.reversed())
        val pasadas = mapOf("vuelta" to listOf(pasadaA(vuelta, 360.0)))
        val d = DetectorSubtramo(listOf(ida, vuelta), pasadas)
        val h = conducir(d, Conduccion.muestrear(carretera, 72.0, desdeM = 2000.0, hastaM = 3500.0)).single()
        assertEquals("ida", h.tramoRefId)
        assertNull(h.mejorMs)
    }

    @Test
    fun `delta en vivo positivo cuando se va mas lento que la mejor pasada`() {
        val tramo = tramoDe("A", carretera)
        val d = DetectorSubtramo(listOf(tramo), mapOf("A" to listOf(pasadaA(tramo, 36.0)))) // 10 m/s
        val resultados =
            Conduccion
                .muestrear(carretera, 18.0, desdeM = 2000.0, hastaM = 3000.0) // 5 m/s
                .map { d.alFijar(it) }
        assertTrue(resultados.take(10).all { it.enCurso == null }) // 50 m recorridos: aun no
        val en600 = resultados[120].enCurso!! // 600 m a 5 m/s: 120 s; la referencia tarda 60 s
        assertEquals("A", en600.tramoRefId)
        assertCerca(600, en600.recorridoM, 5.0)
        assertCerca(120_000, en600.transcurridoMs, 1.0)
        assertCerca(60_000, en600.deltaMs!!, 700.0)
    }

    @Test
    fun `delta en vivo negativo cuando se va mas rapido y empieza a los 300 metros`() {
        val tramo = tramoDe("A", carretera)
        val d = DetectorSubtramo(listOf(tramo), mapOf("A" to listOf(pasadaA(tramo, 36.0))))
        val resultados =
            Conduccion
                .muestrear(carretera, 72.0, desdeM = 2000.0, hastaM = 3000.0) // 20 m/s
                .map { d.alFijar(it) }
        val primera = resultados.indexOfFirst { it.enCurso != null }
        assertCerca(300, resultados[primera].enCurso!!.recorridoM, 21.0) // desde 300 m
        val en600 = resultados[30].enCurso!! // 30 s; la referencia tarda 60 s
        assertCerca(-30_000, en600.deltaMs!!, 700.0)
    }

    @Test
    fun `sin pasadas el subtramo en curso no tiene delta`() {
        val d = DetectorSubtramo(listOf(tramoDe("A", carretera)), emptyMap())
        val resultados = Conduccion.muestrear(carretera, 72.0, desdeM = 2000.0, hastaM = 3000.0).map { d.alFijar(it) }
        val enCurso = resultados[30].enCurso
        assertNotNull(enCurso)
        assertNull(enCurso!!.deltaMs)
    }

    @Test
    fun `un desvio lateral cierra el trozo y lo emite sin esperar al final`() {
        val d = DetectorSubtramo(listOf(tramoDe("A", carretera)), emptyMap())
        val enCarretera = Conduccion.muestrear(carretera, 72.0, desdeM = 2000.0, hastaM = 3200.0)
        val fuera =
            Conduccion
                .muestrear(carretera, 72.0, t0Ms = enCarretera.last().tiempoMs + 1000, desdeM = 3220.0, hastaM = 3600.0)
                .map { it.copy(punto = desplazar(it.punto, 0.0, 80.0)) }
        val hechos = (enCarretera + fuera).flatMap { d.alFijar(it).hechos }
        val h = hechos.single()
        assertCerca(3200, h.hastaM, 3.0)
        assertCerca(60_000, h.tiempoMs, 600.0)
        assertTrue(d.cerrar().isEmpty())
    }

    @Test
    fun `una vez cerrado por desvio el tramo vuelve a abrirse al reincorporarse`() {
        val d = DetectorSubtramo(listOf(tramoDe("A", carretera)), emptyMap())
        val primera = Conduccion.muestrear(carretera, 72.0, desdeM = 0.0, hastaM = 1500.0)
        val fuera =
            Conduccion
                .muestrear(carretera, 72.0, t0Ms = primera.last().tiempoMs + 1000, desdeM = 1520.0, hastaM = 3000.0)
                .map { it.copy(punto = desplazar(it.punto, 0.0, 100.0)) }
        val segunda =
            Conduccion.muestrear(
                carretera,
                72.0,
                t0Ms = fuera.last().tiempoMs + 1000,
                desdeM = 3000.0,
                hastaM = 4500.0,
            )
        val hechos = conducir(d, primera + fuera + segunda)
        assertEquals(2, hechos.size)
        assertCerca(0, hechos[0].desdeM, 3.0)
        assertCerca(1500, hechos[0].hastaM, 3.0)
        assertCerca(3000, hechos[1].desdeM, 25.0)
        assertCerca(4500, hechos[1].hastaM, 3.0)
    }

    @Test
    fun `un salto de mas de 150 metros cierra de inmediato`() {
        val d = DetectorSubtramo(listOf(tramoDe("A", carretera)), emptyMap())
        val enCarretera = Conduccion.muestrear(carretera, 72.0, desdeM = 2000.0, hastaM = 3200.0)
        val salto =
            Conduccion.fijacion(
                desplazar(Conduccion.puntoA(carretera, 3220.0), 0.0, 300.0),
                enCarretera.last().tiempoMs + 1000,
            )
        val hechos = (enCarretera + salto).flatMap { d.alFijar(it).hechos }
        assertEquals(1, hechos.size)
    }

    @Test
    fun `cerrar al terminar la conduccion emite lo abierto`() {
        val d = DetectorSubtramo(listOf(tramoDe("A", carretera)), emptyMap())
        val fijaciones = Conduccion.muestrear(carretera, 72.0, desdeM = 2000.0, hastaM = 3000.0)
        assertTrue(fijaciones.all { d.alFijar(it).hechos.isEmpty() })
        val h = d.cerrar().single()
        assertCerca(1000, h.longitudM, 5.0)
        assertTrue(d.cerrar().isEmpty())
    }

    @Test
    fun `sin tramos no hay nada`() {
        val d = DetectorSubtramo(emptyList(), emptyMap())
        assertTrue(conducir(d, Conduccion.muestrear(carretera, 72.0, desdeM = 0.0, hastaM = 3000.0)).isEmpty())
    }

    @Test
    fun `un tramo degenerado sin longitud se ignora`() {
        val punto = Conduccion.puntoA(carretera, 100.0)
        val quieto = Tramo("quieto", listOf(punto, punto), 50.0)
        val d = DetectorSubtramo(listOf(quieto, tramoDe("A", carretera)), emptyMap())
        assertEquals(1, conducir(d, Conduccion.muestrear(carretera, 72.0, desdeM = 0.0, hastaM = 1500.0)).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ids repetidos se rechazan`() {
        DetectorSubtramo(listOf(tramoDe("A", carretera), tramoDe("A", carretera)), emptyMap())
    }

    @Test
    fun `300 tramos dispersos y 3600 fijaciones se procesan en menos de dos segundos`() {
        val origen = carretera.first()
        val tramos =
            (0 until 300).map { k ->
                val base = desplazar(origen, (k / 20) * 3000.0, (k % 20) * 3000.0)
                tramoDe("t$k", (0..400).map { desplazar(base, it * 5.0, 0.0) })
            }
        val d = DetectorSubtramo(tramos, emptyMap())
        // Columna este = 0: sus 15 tramos de 2 km estan separados 1 km; se empieza a 500 m del primero.
        val largo = (0..15_000).map { desplazar(origen, it * 5.0, 0.0) }
        val fijaciones = Conduccion.muestrear(largo, 72.0, desdeM = 500.0, hastaM = 500.0 + 3599 * 20.0)
        assertTrue(fijaciones.size >= 3600)
        val t0 = System.nanoTime()
        val hechos = conducir(d, fijaciones)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("tardo $ms ms", ms < 2000)
        assertEquals("t0", hechos.first().tramoRefId) // el primero se recorre desde el 500: trozo, no pasada completa
        assertCerca(1500, hechos.first().longitudM, 10.0)
    }
}
