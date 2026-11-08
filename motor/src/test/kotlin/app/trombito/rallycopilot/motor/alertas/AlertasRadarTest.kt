package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.alertas.GeoPruebas.punto
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.voz.Frase
import app.trombito.rallycopilot.motor.voz.TipoFrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

class AlertasRadarTest {
    private val ruta = GeoPruebas.rutaNorte()

    /** 120 km/h: aviso a 500 m. */
    private val v120 = 33.34

    @Suppress("LongParameterList")
    private fun radar(
        id: String = "r1",
        tipo: TipoRadar = TipoRadar.FIJO,
        norteM: Double = 5000.0,
        esteM: Double = 0.0,
        rumbo: Double? = null,
        limite: Int? = 80,
        tramo: String? = null,
    ) = Radar(id, tipo, punto(norteM, esteM), rumbo, limite, tramo, "test")

    private fun enRuta(vararg radares: Radar) = AlertasRadar(radares.toList(), ruta)

    private fun textos(
        alertas: AlertasRadar,
        posicionM: Double,
        vMs: Double = v120,
    ) = alertas.candidatas(posicionM, vMs).map { it.texto }

    // ---- Fijos en ruta ----

    @Test
    fun `un radar fijo a la distancia de aviso suena con distancia y limite`() {
        val frase = enRuta(radar()).candidatas(4510.0, v120).single()
        assertEquals("radar a quinientos, ochenta", frase.texto)
        assertEquals("radar-r1", frase.id)
        assertEquals(TipoFrase.ALERTA, frase.tipo)
        assertEquals(5000.0, frase.puntoM!!, 0.5)
    }

    @Test
    fun `sin limite solo dice la distancia`() {
        assertEquals(listOf("radar a quinientos"), textos(enRuta(radar(limite = null)), 4510.0))
    }

    @Test
    fun `fuera de la distancia de aviso no suena`() {
        assertEquals(emptyList<String>(), textos(enRuta(radar()), 4480.0))
    }

    @Test
    fun `la distancia de aviso es v por 15 s acotada a 200-800 y redondeada a cien`() {
        val a = enRuta(radar())
        // Parado: 200 m.
        assertEquals(listOf("radar a doscientos, ochenta"), textos(a, 4810.0, 0.0))
        assertEquals(emptyList<String>(), textos(a, 4790.0, 0.0))
        // 10 m/s = 150 m, acotado a 200.
        assertEquals(emptyList<String>(), textos(a, 4790.0, 10.0))
        // 60 m/s = 900 m, acotado a 800.
        assertEquals(listOf("radar a ochocientos, ochenta"), textos(a, 4210.0, 60.0))
        assertEquals(emptyList<String>(), textos(a, 4190.0, 60.0))
        // 100 km/h = 27,78 m/s = 416,7 m → 400.
        assertEquals(listOf("radar a cuatrocientos, ochenta"), textos(a, 4610.0, 100 / 3.6))
        assertEquals(emptyList<String>(), textos(a, 4590.0, 100 / 3.6))
    }

    @Test
    fun `lo que se dice es la distancia que queda redondeada a cien si entras ya dentro de la ventana`() {
        val a = enRuta(radar())
        assertEquals(listOf("radar a doscientos, ochenta"), textos(a, 4760.0))
        assertEquals(listOf("radar, ochenta"), textos(a, 4970.0))
    }

    @Test
    fun `un radar detras o justo en la posicion no suena`() {
        val a = enRuta(radar())
        assertEquals(emptyList<String>(), textos(a, 5000.5))
        assertEquals(emptyList<String>(), textos(a, 5200.0))
    }

    @Test
    fun `solo se cuentan los radares a 30 m o menos del camino`() {
        assertEquals(1, enRuta(radar(esteM = 29.0)).candidatas(4510.0, v120).size)
        assertEquals(0, enRuta(radar(esteM = 31.0)).candidatas(4510.0, v120).size)
        assertEquals(0, enRuta(radar(esteM = -100.0)).candidatas(4510.0, v120).size)
    }

    @Test
    fun `un radar de un solo sentido solo cuenta si el rumbo de marcha difiere menos de 45 grados`() {
        fun cuenta(rumbo: Double?) = enRuta(radar(rumbo = rumbo)).candidatas(4510.0, v120).size
        assertEquals(1, cuenta(null))
        assertEquals(1, cuenta(0.0))
        assertEquals(1, cuenta(44.0))
        assertEquals(0, cuenta(46.0))
        assertEquals(1, cuenta(350.0))
        assertEquals(1, cuenta(316.0))
        assertEquals(0, cuenta(314.0))
        // De lado (46°–134°) no cuenta; opuesto (±45° de 180°) es «sentido contrario».
        assertEquals(0, cuenta(100.0))
        assertEquals(1, cuenta(180.0))
    }

    @Test
    fun `el rumbo del radar se compara con el de la ruta en ese punto (tramo hacia el este tras una esquina)`() {
        val este = (1..20).map { punto(2000.0, it * 100.0) }
        val enL = GeoPruebas.rutaNorte(largoM = 2000.0, pasoM = 250.0) + este
        val alRadar = punto(2000.0, 1000.0)
        val haciaEste = Radar("e", TipoRadar.FIJO, alRadar, 90.0, 50, null, null)
        val haciaNorte = Radar("n", TipoRadar.FIJO, alRadar, 0.0, 50, null, null)
        val textos = AlertasRadar(listOf(haciaEste), enL).candidatas(2510.0, v120).map { it.texto }
        assertEquals(listOf("radar a quinientos, cincuenta"), textos)
        assertEquals(0, AlertasRadar(listOf(haciaNorte), enL).candidatas(2510.0, v120).size)
    }

    @Test
    fun `varios radares salen en orden de ruta con ids propios`() {
        val a =
            enRuta(radar("lejos", norteM = 5400.0), radar("cerca", norteM = 5100.0), radar("fuera", norteM = 9000.0))
        val frases = a.candidatas(4950.0, v120)
        assertEquals(listOf("radar-cerca", "radar-lejos"), frases.map { it.id })
    }

    @Test
    fun `el id es estable aunque cambie la posicion o la velocidad`() {
        val a = enRuta(radar())
        assertEquals(a.candidatas(4510.0, v120).single().id, a.candidatas(4800.0, 20.0).single().id)
    }

    // ---- Segundo aviso y sentido contrario ----

    @Test
    fun `a 100 m o menos suena el segundo aviso con otro id y el primero deja de sonar`() {
        val a = enRuta(radar())
        val lejos = a.candidatas(4860.0, v120).single() // 140 m: primero
        assertEquals("radar-r1", lejos.id)
        assertEquals("radar a cien, ochenta", lejos.texto)
        val cerca = a.candidatas(4905.0, v120).single() // 95 m: segundo
        assertEquals("radar-r1-cerca", cerca.id)
        assertEquals("radar, ochenta", cerca.texto)
        assertEquals(5000.0, cerca.puntoM!!, 0.5)
        assertEquals("radar-r1-cerca", a.candidatas(4990.0, v120).single().id)
    }

    @Test
    fun `el segundo aviso sin limite dice solo radar`() {
        assertEquals(listOf("radar"), textos(enRuta(radar(limite = null)), 4950.0))
    }

    @Test
    fun `un fijo del sentido contrario se avisa como radar sentido contrario con su id`() {
        val a = enRuta(radar(rumbo = 180.0))
        val frase = a.candidatas(4510.0, v120).single()
        assertEquals("radar, sentido contrario", frase.texto)
        assertEquals("radar-r1-contra", frase.id)
        assertEquals(TipoFrase.ALERTA, frase.tipo)
        // Una sola frase (un solo id) hasta pasarlo: no hay segundo aviso a 100 m.
        assertEquals("radar-r1-contra", a.candidatas(4950.0, v120).single().id)
        assertEquals(emptyList<String>(), textos(a, 4480.0))
        assertEquals(emptyList<String>(), textos(a, 5010.0))
    }

    @Test
    fun `un fijo de lado o un tramo o zona del otro sentido no se avisa`() {
        assertEquals(0, enRuta(radar(rumbo = 90.0)).candidatas(4510.0, v120).size)
        assertEquals(0, enRuta(radar(tipo = TipoRadar.ZONA_MOVIL, rumbo = 180.0)).candidatas(4900.0, 10.0).size)
        val tramo = radar("t", TipoRadar.TRAMO_INICIO, rumbo = 180.0, tramo = "t")
        assertEquals(0, enRuta(tramo).candidatas(4900.0, 10.0).size)
        // El tramo del otro sentido: la ruta pasa antes por su fin que por su inicio.
        val contra = radar("t", TipoRadar.TRAMO_INICIO, 7000.0, rumbo = 180.0, tramo = "t")
        val contraFin = radar("f", TipoRadar.TRAMO_FIN, 5000.0, rumbo = 180.0, tramo = "t")
        assertEquals(emptyList<TramoRadar>(), enRuta(contra, contraFin).tramos)
        assertEquals(0, enRuta(contra, contraFin).candidatas(6900.0, 10.0).size)
    }

    @Test
    fun `un tramo con curvas empareja aunque el rumbo de su recta no sea el de la ruta`() {
        // Ruta en L: 3 km al norte y 3 km al este. La recta inicio-fin va a 63 grados y la ruta lleva 0 en el inicio
        // (mas de 45 de diferencia): antes se descartaba el par.
        val enL = GeoPruebas.rutaNorte(3000.0) + (1..6).map { punto(3000.0, it * 500.0) }
        val ini = radar("i", TipoRadar.TRAMO_INICIO, 2000.0, rumbo = 63.0, tramo = "t", limite = 80)
        val fin = radar("f", TipoRadar.TRAMO_FIN, 3000.0, esteM = 2000.0, rumbo = 63.0, tramo = "t", limite = 80)
        val a = AlertasRadar(listOf(ini, fin), enL)
        val tramo = a.tramos.single()
        assertEquals(2000.0, tramo.inicioM, 0.5)
        assertEquals(5000.0, tramo.finM, 2.0)
        assertEquals(listOf("tramo de radar, límite ochenta"), textos(a, 1900.0, 20.0))
    }

    @Test
    fun `en libre un fijo opuesto por delante se avisa como sentido contrario`() {
        val a = AlertasRadar(listOf(radar(norteM = 400.0, rumbo = 180.0)))
        val frase = a.candidatasLibre(punto(0.0), 0.0, v120).single()
        assertEquals("radar-r1-contra", frase.id)
        assertEquals("radar, sentido contrario", frase.texto)
    }

    @Test
    fun `idDeRadar y esContrario leen los ids de las frases`() {
        assertEquals("osm-n1-r2", AlertasRadar.idDeRadar("radar-osm-n1-r2"))
        assertEquals("a", AlertasRadar.idDeRadar("radar-a-cerca"))
        assertEquals("a", AlertasRadar.idDeRadar("radar-a-contra"))
        assertNull(AlertasRadar.idDeRadar("tramo-media-x-1"))
        assertTrue(AlertasRadar.esContrario("radar-a-contra"))
        assertEquals(false, AlertasRadar.esContrario("radar-a-cerca"))
    }

    // ---- Zonas móviles y tramos ----

    @Test
    fun `una zona de radar movil suena al entrar`() {
        val a = enRuta(radar("z", TipoRadar.ZONA_MOVIL, norteM = 5000.0, limite = null))
        // 20 m/s: entrada a 6 s = 120 m (mínimo 80, máximo 250).
        assertEquals(listOf("zona de radar móvil"), textos(a, 4885.0, 20.0))
        assertEquals(emptyList<String>(), textos(a, 4870.0, 20.0))
        assertEquals(emptyList<String>(), textos(a, 5010.0, 20.0))
        assertEquals(TipoFrase.ALERTA, a.candidatas(4885.0, 20.0).single().tipo)
    }

    @Test
    fun `la ventana de entrada de zonas y tramos esta acotada a 80-250 m`() {
        val a = enRuta(radar("z", TipoRadar.ZONA_MOVIL, limite = null))
        assertEquals(1, a.candidatas(4925.0, 0.0).size)
        assertEquals(0, a.candidatas(4905.0, 0.0).size)
        assertEquals(1, a.candidatas(4760.0, 60.0).size)
        assertEquals(0, a.candidatas(4740.0, 60.0).size)
    }

    @Test
    fun `un tramo suena con su limite al entrar y el final no dice nada`() {
        val a =
            enRuta(
                radar("t-ini", TipoRadar.TRAMO_INICIO, norteM = 5000.0, limite = 80, tramo = "t"),
                radar("t-fin", TipoRadar.TRAMO_FIN, norteM = 7000.0, limite = 80, tramo = "t"),
            )
        assertEquals(listOf("tramo de radar, límite ochenta"), textos(a, 4885.0, 20.0))
        assertEquals(emptyList<String>(), textos(a, 6900.0, 20.0))
    }

    @Test
    fun `un tramo sin limite no lo dice`() {
        val a =
            enRuta(
                radar("t-ini", TipoRadar.TRAMO_INICIO, limite = null, tramo = "t"),
                radar("t-fin", TipoRadar.TRAMO_FIN, norteM = 7000.0, limite = null, tramo = "t"),
            )
        assertEquals(listOf("tramo de radar"), textos(a, 4885.0, 20.0))
    }

    @Test
    fun `los tramos de la ruta empareja inicio y fin por tramo_id y solo si van en ese orden`() {
        val a =
            enRuta(
                radar("t1-ini", TipoRadar.TRAMO_INICIO, norteM = 2000.0, limite = 80, tramo = "t1"),
                radar("t1-fin", TipoRadar.TRAMO_FIN, norteM = 6000.0, limite = 80, tramo = "t1"),
                // Al revés (el tramo se recorre en el otro sentido): no cuenta.
                radar("t2-ini", TipoRadar.TRAMO_INICIO, norteM = 9000.0, limite = 60, tramo = "t2"),
                radar("t2-fin", TipoRadar.TRAMO_FIN, norteM = 8000.0, limite = 60, tramo = "t2"),
                // Sin pareja.
                radar("t3-ini", TipoRadar.TRAMO_INICIO, norteM = 3000.0, limite = 60, tramo = "t3"),
            )
        val tramo = a.tramos.single()
        assertEquals("t1", tramo.id)
        assertEquals(2000.0, tramo.inicioM, 0.5)
        assertEquals(6000.0, tramo.finM, 0.5)
        assertEquals(80, tramo.limiteKmh)
    }

    @Test
    fun `el limite del tramo cae al del final si el inicio no lo trae`() {
        val a =
            enRuta(
                radar("i", TipoRadar.TRAMO_INICIO, norteM = 2000.0, limite = null, tramo = "t"),
                radar("f", TipoRadar.TRAMO_FIN, norteM = 6000.0, limite = 70, tramo = "t"),
            )
        assertEquals(70, a.tramos.single().limiteKmh)
    }

    // ---- Modo libre ----

    private val libre = AlertasRadar(listOf(radar(norteM = 400.0)))

    private fun libre(
        rumbo: Double?,
        v: Double = v120,
        pos: PuntoGeo = punto(0.0),
        posicionM: Double? = null,
    ): List<Frase> = libre.candidatasLibre(pos, rumbo, v, posicionM)

    @Test
    fun `en libre un radar por delante en el rumbo de marcha suena`() {
        assertEquals(listOf("radar a cuatrocientos, ochenta"), libre(0.0).map { it.texto })
    }

    @Test
    fun `en libre el radar detras, de lado o en sentido contrario no suena`() {
        assertEquals(0, libre(180.0).size)
        assertEquals(0, libre(90.0).size)
        assertEquals(0, libre(270.0).size)
        assertEquals(0, libre(0.0, pos = punto(500.0)).size)
    }

    @Test
    fun `en libre sin rumbo no hay nada que decir`() {
        assertEquals(0, libre(null).size)
    }

    @Test
    fun `en libre respeta la tolerancia lateral de 30 m`() {
        fun cuenta(esteM: Double) =
            AlertasRadar(listOf(radar(norteM = 400.0, esteM = esteM))).candidatasLibre(punto(0.0), 0.0, v120).size
        assertEquals(1, cuenta(29.0))
        assertEquals(0, cuenta(31.0))
    }

    @Test
    fun `en libre el rumbo del radar tambien filtra`() {
        fun cuenta(rumbo: Double?) =
            AlertasRadar(listOf(radar(norteM = 400.0, rumbo = rumbo))).candidatasLibre(punto(0.0), 0.0, v120).size
        assertEquals(1, cuenta(null))
        assertEquals(1, cuenta(44.0))
        assertEquals(0, cuenta(46.0))
        assertEquals(0, cuenta(90.0))
        assertEquals(1, cuenta(190.0))
    }

    @Test
    fun `en libre la ventana depende de la velocidad`() {
        assertEquals(0, libre(0.0, v = 0.0).size) // 400 m > 300 m
        assertEquals(0, libre(0.0, v = 0.0, pos = punto(150.0)).size) // 250 m > 200 m
        assertEquals(1, libre(0.0, v = 0.0, pos = punto(250.0)).size) // 150 m
    }

    @Test
    fun `en libre el puntoM solo existe si se da la posicion en la traza`() {
        assertNull(libre(0.0).single().puntoM)
        assertEquals(1400.0, libre(0.0, posicionM = 1000.0).single().puntoM!!, 0.5)
    }

    @Test
    fun `sin ruta el modo ruta no devuelve nada`() {
        assertEquals(emptyList<Frase>(), AlertasRadar(listOf(radar())).candidatas(4500.0, v120))
        assertEquals(emptyList<TramoRadar>(), AlertasRadar(listOf(radar())).tramos)
    }

    @Test
    fun `una ruta de un solo punto es un error de uso`() {
        assertFailsWith<IllegalArgumentException> { AlertasRadar(listOf(radar()), listOf(punto(0.0))) }
    }

    @Test
    fun `sin radares no hay frases`() {
        assertTrue(AlertasRadar(emptyList(), ruta).candidatas(100.0, v120).isEmpty())
    }
}
