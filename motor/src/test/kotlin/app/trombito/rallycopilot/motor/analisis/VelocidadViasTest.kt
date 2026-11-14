package app.trombito.rallycopilot.motor.analisis

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class VelocidadViasTest {
    private val latBase = 39.6
    private val lonBase = 2.9
    private val metrosPorGradoLat = 111_195.0
    private val metrosPorGradoLon = metrosPorGradoLat * cos(Math.toRadians(latBase))

    private fun punto(x: Double) = PuntoGeo(latBase, lonBase + x / metrosPorGradoLon)

    private fun rectaVia(
        id: Long,
        desdeX: Double,
        hastaX: Double,
    ) = Via(
        id,
        "secondary",
        null,
        null,
        null,
        SentidoUnico.NO,
        "Ma-$id",
        null,
        (0..((hastaX - desdeX) / 100.0).toInt()).map { punto(desdeX + it * 100.0) },
    )

    private val viaA = rectaVia(1, 0.0, 1_000.0)
    private val viaB = rectaVia(2, 1_000.0, 2_000.0)
    private val indice = IndiceVias(listOf(viaA, viaB))
    private val coincidenciaDe = VelocidadVias.CoincidenciaDe { p, r -> indice.cercana(p, r) }

    /**
     * Recorrido por el eje x a [kmh] constante de [desdeX] a [hastaX] (hacia el este si desde < hasta, si no al oeste),
     * con un punto cada [cadaS] segundos y el reloj empezando en [t0Ms].
     */
    private fun conducir(
        desdeX: Double,
        hastaX: Double,
        kmh: Double = 60.0,
        cadaS: Int = 1,
        t0Ms: Long = 0L,
    ): List<PuntoTiempo> {
        val ms = kmh / 3.6
        val total = kotlin.math.abs(hastaX - desdeX)
        val signo = if (hastaX >= desdeX) 1.0 else -1.0
        val n = (total / ms / cadaS).toInt()
        return (0..n).map {
            val t = it * cadaS
            PuntoTiempo(punto(desdeX + signo * ms * t), t0Ms + t * 1_000L, null)
        }
    }

    private fun resumen(vararg recorridos: List<PuntoTiempo>) =
        VelocidadVias.resumir(recorridos.toList(), coincidenciaDe)

    @Test
    fun `una via a 60 kmh constantes da 60 kmh y sus kilometros`() {
        val vias = resumen(conducir(0.0, 1_000.0))
        val a = vias.single()
        assertEquals(1L, a.via.id)
        assertEquals(1, a.pasadas)
        assertEquals(60.0, a.velMediaKmh!!, 0.5)
        assertEquals(1.0, a.km, 0.03)
    }

    @Test
    fun `con velocidad del GPS el resultado es el mismo`() {
        val ms = 60.0 / 3.6
        val conVelocidad = conducir(0.0, 1_000.0).map { it.copy(velocidadMs = ms) }
        assertEquals(60.0, resumen(conVelocidad).single().velMediaKmh!!, 0.5)
    }

    @Test
    fun `cada via lleva su propia velocidad`() {
        val rapido = conducir(0.0, 1_000.0, kmh = 90.0)
        val lento = conducir(1_000.0, 2_000.0, kmh = 45.0, t0Ms = rapido.last().tiempoMs + 1_000L)
        val vias = resumen(rapido + lento)
        assertEquals(90.0, vias.first { it.via.id == 1L }.velMediaKmh!!, 1.0)
        assertEquals(45.0, vias.first { it.via.id == 2L }.velMediaKmh!!, 1.0)
    }

    @Test
    fun `una parada en medio no cuenta ni metros ni tiempo`() {
        val antes = conducir(0.0, 400.0)
        val finAntes = antes.last()
        // Parado 60 s (un punto por segundo, mismo sitio) y luego sigue.
        val parado = (1..60).map { PuntoTiempo(finAntes.punto, finAntes.tiempoMs + it * 1_000L) }
        val despues = conducir(400.0, 1_000.0, t0Ms = parado.last().tiempoMs)
        val a = resumen(antes + parado + despues).single()
        assertEquals(60.0, a.velMediaKmh!!, 0.5)
        assertEquals(1.0, a.km, 0.04)
        // Solo el tiempo de conducir: ~60 s, no 120.
        assertEquals(60_000.0, a.total.tiempoMs.toDouble(), 2_000.0)
    }

    @Test
    fun `un hueco de GPS de mas de 30 segundos no suma ni metros ni tiempo`() {
        val antes = conducir(0.0, 300.0)
        // 45 s sin fijaciones y reaparece 100 m más allá (a menos de 250 m: solo el hueco de tiempo lo descarta).
        val despues = conducir(400.0, 1_000.0, t0Ms = antes.last().tiempoMs + 45_000L)
        val a = resumen(antes + despues).single()
        assertEquals(60.0, a.velMediaKmh!!, 0.5)
        assertEquals(0.9, a.km, 0.04)
    }

    @Test
    fun `dos recorridos acumulan metros y tiempos y no medias`() {
        // 500 m a 90 km/h (20 s) y 1 000 m a 30 km/h (120 s): la media real es 1,5 km / 140 s = 38,6 km/h.
        val r1 = conducir(0.0, 500.0, kmh = 90.0)
        val r2 = conducir(0.0, 1_000.0, kmh = 30.0, cadaS = 2)
        val vias = resumen(r1, r2)
        val a = vias.single()
        assertEquals(2, a.pasadas)
        val esperada = (500.0 + 1_000.0) / (500.0 / 25.0 + 1_000.0 / (30.0 / 3.6)) * 3.6
        assertEquals(esperada, a.velMediaKmh!!, 1.5)
        assertTrue(kotlin.math.abs(a.velMediaKmh!! - 60.0) > 15.0) // no es la media de medias (60)
    }

    @Test
    fun `agregar suma a lo ya acumulado`() {
        val r1 = VelocidadVias.analizar(conducir(0.0, 1_000.0, kmh = 60.0), coincidenciaDe)
        val r2 = VelocidadVias.analizar(conducir(0.0, 1_000.0, kmh = 30.0), coincidenciaDe)
        val enDos = VelocidadVias.agregar(listOf(r2), base = VelocidadVias.agregar(listOf(r1)))
        val deGolpe = VelocidadVias.agregar(listOf(r1, r2))
        assertEquals(2, enDos.single().pasadas)
        assertEquals(deGolpe.single().total.metros, enDos.single().total.metros, 1e-9)
        assertEquals(deGolpe.single().total.tiempoMs, enDos.single().total.tiempoMs)
    }

    @Test
    fun `el sentido sale del orden de los puntos de la via`() {
        val ida = conducir(0.0, 1_000.0, kmh = 60.0)
        val vuelta = conducir(1_000.0, 0.0, kmh = 30.0, cadaS = 2, t0Ms = 10_000_000L)
        val a = resumen(ida, vuelta).single()
        assertEquals(2, a.pasadas)
        assertEquals(60.0, a.velMediaKmh(sentidoAdelante = true)!!, 1.0)
        assertEquals(30.0, a.velMediaKmh(sentidoAdelante = false)!!, 1.0)
    }

    @Test
    fun `un sentido sin conducir no tiene velocidad`() {
        val a = resumen(conducir(0.0, 1_000.0)).single()
        assertNull(a.velMediaKmh(sentidoAdelante = false))
        assertEquals(0.0, a.atras.metros, 0.0)
    }

    @Test
    fun `cruzar una via menos de 40 metros no es pasada`() {
        val corto = conducir(980.0, 1_015.0)
        assertTrue(resumen(corto).isEmpty())
        assertTrue(resumen(conducir(0.0, 30.0)).isEmpty())
    }

    @Test
    fun `sin vias cercanas o sin puntos no hay nada`() {
        assertTrue(VelocidadVias.resumir(emptyList(), coincidenciaDe).isEmpty())
        assertTrue(VelocidadVias.resumir(listOf(conducir(0.0, 1_000.0))) { _, _ -> null }.isEmpty())
        assertTrue(VelocidadVias.resumir(listOf(conducir(0.0, 1_000.0).take(1)), coincidenciaDe).isEmpty())
    }

    @Test
    fun `tiempos que no avanzan se descartan`() {
        val p = conducir(0.0, 1_000.0)
        val repetido = p.flatMap { listOf(it, it) }
        assertEquals(60.0, resumen(repetido).single().velMediaKmh!!, 0.5)
    }
}
