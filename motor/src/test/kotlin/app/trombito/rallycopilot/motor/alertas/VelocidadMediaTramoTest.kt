package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.voz.TipoFrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VelocidadMediaTramoTest {
    private val tramo = TramoRadar("t", 1000.0, 3000.0, 80)

    /** Conductor sintético: avanza `v` m/s y se consulta cada `pasoS` segundos con un reloj inyectado. */
    private class Coche(
        val medidor: VelocidadMediaTramo,
        var posicionM: Double = 0.0,
        var ahoraMs: Long = 0L,
    ) {
        fun avanzar(
            metros: Double,
            vMs: Double,
            pasoS: Double = 1.0,
        ): List<EstadoTramo> {
            val estados = ArrayList<EstadoTramo>()
            val destino = posicionM + metros
            while (posicionM < destino - 1e-9) {
                val d = minOf(vMs * pasoS, destino - posicionM)
                posicionM += d
                ahoraMs += Math.round(d / vMs * 1000)
                medidor.actualizar(posicionM, ahoraMs)?.let(estados::add)
            }
            return estados
        }
    }

    private fun coche(
        vararg tramos: TramoRadar,
        desdeM: Double = 0.0,
    ) = Coche(VelocidadMediaTramo(tramos.toList())).also {
        it.posicionM = desdeM
        it.medidor.actualizar(desdeM, 0L)
    }

    @Test
    fun `fuera de un tramo no hay estado`() {
        val c = coche(tramo)
        assertEquals(emptyList<EstadoTramo>(), c.avanzar(900.0, 20.0))
    }

    @Test
    fun `la media de un tramo a velocidad constante es esa velocidad`() {
        val c = coche(tramo)
        c.avanzar(990.0, 30.0)
        val estados = c.avanzar(2100.0, 30.0)
        val ultimo = estados.last()
        assertTrue(ultimo.terminado)
        assertEquals(108.0, ultimo.mediaKmh!!, 1e-6)
        assertEquals("t", ultimo.tramo.id)
    }

    @Test
    fun `la entrada y la salida se interpolan entre fijaciones, no se redondean a la fijacion`() {
        // Fijaciones cada 4 s a 30 m/s (120 m): el tramo empieza y acaba entre fijaciones.
        val c = coche(TramoRadar("t", 1050.0, 3050.0, 100))
        c.avanzar(960.0, 30.0, pasoS = 4.0)
        val ultimo = c.avanzar(2400.0, 30.0, pasoS = 4.0).last { it.terminado }
        assertEquals(108.0, ultimo.mediaKmh!!, 1e-6)
    }

    @Test
    fun `la media sigue lo recorrido dentro del tramo y no cuenta lo anterior`() {
        val c = coche(tramo)
        c.avanzar(1000.0, 10.0) // despacio antes de entrar: no cuenta
        val dentro = c.avanzar(1000.0, 20.0)
        assertEquals(72.0, dentro.last().mediaKmh!!, 1e-6)
        assertFalse(dentro.last().terminado)
    }

    @Test
    fun `hasta recorrer 100 m en el tramo no hay media`() {
        val c = coche(tramo)
        c.avanzar(1000.0, 20.0)
        val estados = c.avanzar(300.0, 20.0, pasoS = 1.0)
        assertTrue(estados.take(4).all { it.mediaKmh == null && it.frase == null })
        assertNotNull(estados.last().mediaKmh)
    }

    @Test
    fun `avisa una vez cuando la media pasa del limite, con id estable mientras siga por encima`() {
        val c = coche(tramo)
        c.avanzar(1000.0, 25.0)
        val estados = c.avanzar(1500.0, 25.0) // 90 km/h con limite 80
        val frases = estados.mapNotNull { it.frase }
        assertTrue(frases.isNotEmpty())
        assertEquals(setOf("tramo-media-t-1"), frases.map { it.id }.toSet())
        assertEquals(TipoFrase.AVISO_VELOCIDAD, frases.first().tipo)
        assertEquals("media alta", frases.first().texto)
        assertNull(frases.first().puntoM)
        assertTrue(estados.last().superaLimite)
    }

    @Test
    fun `una media igual al limite no avisa (el limite exacto no es exceso)`() {
        val c = coche(TramoRadar("t", 1000.0, 3000.0, 72))
        c.avanzar(1000.0, 20.0)
        val estados = c.avanzar(1500.0, 20.0) // 72 km/h exactos
        assertEquals(72.0, estados.last().mediaKmh!!, 1e-9)
        assertTrue(estados.all { it.frase == null && !it.superaLimite })
    }

    @Test
    fun `se rearma si la media baja del limite y vuelve a subir`() {
        val c = coche(TramoRadar("t", 1000.0, 5000.0, 80))
        c.avanzar(1000.0, 25.0)
        val a = c.avanzar(800.0, 100 / 3.6) // media 100: avisa (1)
        val b = c.avanzar(1200.0, 40 / 3.6) // la media cae por debajo de 80
        val d = c.avanzar(1990.0, 250 / 3.6) // y vuelve a subir por encima (separado de sobra del primer aviso)
        assertEquals(setOf("tramo-media-t-1"), a.mapNotNull { it.frase?.id }.toSet())
        assertTrue(b.any { !it.superaLimite && it.frase == null })
        assertEquals(setOf("tramo-media-t-2"), d.mapNotNull { it.frase?.id }.toSet())
    }

    @Test
    fun `si la media baja, la frase pendiente se retira`() {
        val c = coche(tramo)
        c.avanzar(1000.0, 25.0)
        c.avanzar(300.0, 30.0)
        val lento = c.avanzar(400.0, 5.0)
        assertNull(lento.last().frase)
        assertFalse(lento.last().superaLimite)
    }

    @Test
    fun `sin limite hay media pero nunca aviso`() {
        val c = coche(TramoRadar("t", 1000.0, 3000.0, null))
        c.avanzar(1000.0, 50.0)
        val estados = c.avanzar(1500.0, 50.0)
        assertEquals(180.0, estados.last().mediaKmh!!, 1e-6)
        assertTrue(estados.all { it.frase == null && !it.superaLimite })
    }

    @Test
    fun `terminar da el estado final una vez y luego nada`() {
        val c = coche(tramo)
        c.avanzar(1000.0, 25.0)
        val estados = c.avanzar(2000.0, 25.0)
        assertEquals(1, estados.count { it.terminado })
        assertTrue(estados.last().terminado)
        assertNull(estados.last().frase)
        assertEquals(emptyList<EstadoTramo>(), c.avanzar(500.0, 25.0))
    }

    @Test
    fun `empezar dentro del tramo no lo mide a medias`() {
        val c = coche(tramo, desdeM = 1500.0)
        assertEquals(emptyList<EstadoTramo>(), c.avanzar(1000.0, 25.0))
    }

    @Test
    fun `volver atras de la entrada cancela y se mide al volver a entrar`() {
        val medidor = VelocidadMediaTramo(listOf(tramo))
        medidor.actualizar(900.0, 0L)
        assertNotNull(medidor.actualizar(1100.0, 10_000L))
        assertNull(medidor.actualizar(950.0, 20_000L))
        assertNull(medidor.actualizar(990.0, 30_000L))
        val reentrada = medidor.actualizar(1010.0, 40_000L)
        assertNotNull(reentrada)
        // Entró a los 35 s (interpolado) y a los 40 s lleva 10 m: la media aún no se da.
        assertNull(reentrada!!.mediaKmh)
    }

    @Test
    fun `un salto que se salta el tramo entero lo cierra con la media interpolada`() {
        val medidor = VelocidadMediaTramo(listOf(tramo))
        medidor.actualizar(0.0, 0L)
        val estado = medidor.actualizar(4000.0, 100_000L)!! // 40 m/s de media
        assertTrue(estado.terminado)
        assertEquals(144.0, estado.mediaKmh!!, 1e-6)
    }

    @Test
    fun `dos tramos seguidos se miden por separado`() {
        val c = coche(TramoRadar("a", 1000.0, 2000.0, 80), TramoRadar("b", 2500.0, 3500.0, 50))
        val estados = c.avanzar(4000.0, 20.0)
        assertEquals(listOf("a", "b"), estados.filter { it.terminado }.map { it.tramo.id })
        assertTrue(estados.filter { it.tramo.id == "b" }.any { it.superaLimite })
        assertFalse(estados.filter { it.tramo.id == "a" }.any { it.superaLimite })
    }

    @Test
    fun `si el reloj no avanza no hay media ni se rompe`() {
        val medidor = VelocidadMediaTramo(listOf(tramo))
        medidor.actualizar(900.0, 5000L)
        val estado = medidor.actualizar(1200.0, 5000L)
        assertNull(estado?.mediaKmh)
    }

    @Test
    fun `sin tramos no hace nada`() {
        val medidor = VelocidadMediaTramo(emptyList())
        medidor.actualizar(0.0, 0L)
        assertNull(medidor.actualizar(100.0, 1000L))
    }

    // ---- Histéresis, separación y máximo por tramo ----

    @Test
    fun `una media que pasa del limite en menos de 3 km h se pone en rojo pero no avisa`() {
        val c = coche(tramo)
        c.avanzar(1000.0, 25.0)
        val estados = c.avanzar(1500.0, 82 / 3.6) // media 82 con limite 80
        assertEquals(82.0, estados.last().mediaKmh!!, 0.1)
        assertTrue(estados.last().superaLimite)
        assertTrue(estados.all { it.frase == null })
    }

    @Test
    fun `con 4 km h por encima del limite si avisa`() {
        val c = coche(tramo)
        c.avanzar(1000.0, 25.0)
        val estados = c.avanzar(1500.0, 84 / 3.6)
        assertEquals("tramo-media-t-1", estados.last().frase!!.id)
    }

    /** Tramo largo y muestras sueltas: la entrada es en la posición 1000 al instante 100 s; `en` da el estado. */
    private class Tramo2 {
        val medidor =
            VelocidadMediaTramo(listOf(TramoRadar("t", 1000.0, 100_000.0, 80))).also {
                it.actualizar(990.0, 99_000L)
                it.actualizar(1000.0, 100_000L)
            }

        fun en(
            segundos: Int,
            posicionM: Double,
        ) = medidor.actualizar(posicionM, 100_000L + segundos * 1000L)!!
    }

    @Test
    fun `no avisa antes de 60 s desde el aviso anterior aunque la media vuelva a subir`() {
        val t = Tramo2()
        assertEquals("tramo-media-t-1", t.en(10, 1300.0).frase!!.id) // media 108
        assertNull(t.en(40, 1400.0).frase) // media 36: se retira y se rearma
        assertNull(t.en(50, 2200.0).frase) // media 86 pero a 40 s del aviso
        assertNull(t.en(69, 2725.0).frase) // a 59 s del aviso: aun no
        assertEquals("tramo-media-t-2", t.en(70, 2750.0).frase!!.id) // a 60 s exactos
    }

    @Test
    fun `como mucho dos avisos por tramo`() {
        val t = Tramo2()
        assertEquals("tramo-media-t-1", t.en(10, 1300.0).frase!!.id)
        assertNull(t.en(100, 1400.0).frase)
        assertEquals("tramo-media-t-2", t.en(110, 3540.0).frase!!.id)
        assertNull(t.en(1000, 3600.0).frase)
        assertTrue(t.en(1010, 24_400.0).superaLimite) // media 83+ pero sin cupo
        assertNull(t.en(1010, 24_400.0).frase)
        assertNull(t.en(1100, 40_000.0).frase)
    }

    @Test
    fun `el cupo de dos avisos es por tramo, no global`() {
        val medidor =
            VelocidadMediaTramo(listOf(TramoRadar("a", 1000.0, 5000.0, 80), TramoRadar("b", 6000.0, 9000.0, 80)))
        medidor.actualizar(990.0, 99_000L)
        medidor.actualizar(1000.0, 100_000L)
        assertEquals("tramo-media-a-1", medidor.actualizar(1300.0, 110_000L)!!.frase!!.id)
        medidor.actualizar(5990.0, 299_000L) // cierra a
        medidor.actualizar(6000.0, 300_000L) // entra en b
        // Tramo nuevo: su propio contador empieza en uno.
        assertEquals("tramo-media-b-1", medidor.actualizar(6300.0, 310_000L)!!.frase!!.id)
    }
}
