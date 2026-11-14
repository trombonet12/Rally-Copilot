package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TroceadorViasTest {
    private val base = PuntoGeo(39.70, 2.70)

    /** Punto [metros] al este de [base]. */
    private fun alEste(
        metros: Double,
        norteM: Double = 0.0,
    ): PuntoGeo {
        val grados = Math.toDegrees(metros / (PuntoGeo.RADIO_TIERRA_M * Math.cos(Math.toRadians(base.lat))))
        return PuntoGeo(base.lat + Math.toDegrees(norteM / PuntoGeo.RADIO_TIERRA_M), base.lon + grados)
    }

    /** Vía recta entre [desdeM] y [hastaM] (al este de la base); con `desdeM > hastaM` se dibuja hacia el oeste. */
    @Suppress("LongParameterList") // un parámetro por etiqueta de OSM que importa en cada prueba
    private fun via(
        id: Long,
        ref: String?,
        desdeM: Double,
        hastaM: Double,
        nombre: String? = null,
        norteM: Double = 0.0,
    ): Via {
        val pasos = 10
        val puntos = (0..pasos).map { alEste(desdeM + (hastaM - desdeM) * it / pasos, norteM) }
        return Via(id, "secondary", null, null, null, SentidoUnico.NO, ref, nombre, puntos)
    }

    /** Traza hacia el este (o el oeste si [alReves]) entre 0 y [largoM] cada [pasoM]. */
    private fun traza(
        largoM: Double,
        pasoM: Double = 100.0,
        alReves: Boolean = false,
    ): List<PuntoTraza> =
        (0..(largoM / pasoM).toInt()).map {
            val d = it * pasoM
            PuntoTraza(alEste(if (alReves) largoM - d else d), d)
        }

    /** Traza hacia el oeste (de [largoM] a 0) por una calzada [norteM] al norte de la base. */
    private fun trazaPorCalzadaNorte(
        largoM: Double,
        norteM: Double,
        pasoM: Double = 100.0,
    ): List<PuntoTraza> =
        (0..(largoM / pasoM).toInt()).map {
            val d = it * pasoM
            PuntoTraza(alEste(largoM - d, norteM), d)
        }

    /** Hitos cada 500 m en [desdeM]..[hastaM]; el km vale `km0 + sentido * metros / 1000`. */
    private fun hitos(
        ref: String,
        km0: Double,
        desdeM: Int,
        hastaM: Int,
        sentido: Double = 1.0,
    ) = (desdeM..hastaM step 500).map { HitoKm(ref, km0 + sentido * (it - desdeM) / 1000.0, alEste(it.toDouble())) }

    @Test
    fun `un viaje por tres carreteras da tres tramos`() {
        val vias =
            listOf(
                via(1, "Ma-30", 0.0, 3_000.0),
                via(2, "Ma-15", 3_000.0, 7_000.0),
                via(3, "Ma-3220", 7_000.0, 10_000.0),
            )
        val tramos = TroceadorVias(IndiceVias(vias)).trocear(traza(10_000.0))

        assertEquals(listOf("Ma-30", "Ma-15", "Ma-3220"), tramos.map { it.ref })
        assertEquals(0.0, tramos[0].desdeM, 1e-9)
        assertEquals(3_000.0, tramos[0].hastaM, 200.0)
        assertEquals(3_000.0, tramos[1].desdeM, 200.0)
        assertEquals(10_000.0, tramos[2].hastaM, 1e-9)
        assertTrue(tramos.zipWithNext().all { (a, b) -> a.hastaM <= b.desdeM })
    }

    @Test
    fun `una rotonda corta entre dos trozos de la misma carretera no corta`() {
        val vias =
            listOf(
                via(1, "Ma-15", 0.0, 3_000.0),
                via(2, null, 3_000.0, 3_100.0).copy(junction = "roundabout"),
                via(3, "Ma-15", 3_100.0, 6_000.0),
            )
        val tramos = TroceadorVias(IndiceVias(vias)).trocear(traza(6_000.0, 50.0))

        assertEquals(1, tramos.size)
        assertEquals(0.0, tramos[0].desdeM, 1e-9)
        assertEquals(6_000.0, tramos[0].hastaM, 1e-9)
    }

    @Test
    fun `un cambio breve de carretera de menos de 150 m no corta`() {
        val vias =
            listOf(via(1, "Ma-15", 0.0, 3_000.0), via(2, "Ma-3", 3_000.0, 3_090.0), via(3, "Ma-15", 3_090.0, 6_000.0))
        val tramos = TroceadorVias(IndiceVias(vias)).trocear(traza(6_000.0, 30.0))

        assertEquals(listOf("Ma-15"), tramos.map { it.ref })
        assertEquals(6_000.0, tramos[0].hastaM, 1e-9)
    }

    @Test
    fun `una via sin ref ni nombre no forma tramo`() {
        val vias = listOf(via(1, "Ma-15", 0.0, 3_000.0), via(2, null, 3_000.0, 6_000.0))
        val tramos = TroceadorVias(IndiceVias(vias)).trocear(traza(6_000.0))

        assertEquals(listOf("Ma-15"), tramos.map { it.ref })
    }

    @Test
    fun `descarta los trozos de menos de un kilometro`() {
        val vias =
            listOf(via(1, "Ma-30", 0.0, 3_000.0), via(2, "Ma-3", 3_000.0, 3_800.0), via(3, "Ma-15", 3_800.0, 7_000.0))
        val tramos = TroceadorVias(IndiceVias(vias)).trocear(traza(7_000.0))

        assertEquals(listOf("Ma-30", "Ma-15"), tramos.map { it.ref })
    }

    @Test
    fun `una traza de menos de dos puntos no da tramos`() {
        val troceador = TroceadorVias(IndiceVias(listOf(via(1, "Ma-15", 0.0, 3_000.0))))

        assertTrue(troceador.trocear(emptyList()).isEmpty())
        assertTrue(troceador.trocear(listOf(PuntoTraza(base, 0.0))).isEmpty())
    }

    @Test
    fun `el sentido sale del PK aunque la via se dibuje al contrario`() {
        val vias = listOf(via(1, "Ma-15", 0.0, 5_000.0))
        val pk = IndicePuntosKm(hitos("Ma-15", 32.0, 0, 5_000, sentido = -1.0))
        val tramos = TroceadorVias(IndiceVias(vias), pk).trocear(traza(5_000.0))

        assertEquals(1, tramos.size)
        assertEquals(SentidoVia.DECRECIENTE, tramos[0].sentido)
        assertEquals(32.0, tramos[0].pkDesde!!, 0.2)
        assertEquals(27.0, tramos[0].pkHasta!!, 0.2)
    }

    @Test
    fun `el sentido por PK es creciente si el kilometro sube`() {
        val vias = listOf(via(1, "Ma-15", 5_000.0, 0.0))
        val pk = IndicePuntosKm(hitos("Ma-15", 20.0, 0, 5_000))
        val tramos = TroceadorVias(IndiceVias(vias), pk).trocear(traza(5_000.0))

        assertEquals(SentidoVia.CRECIENTE, tramos.single().sentido)
        assertEquals(20.0, tramos.single().pkDesde!!, 0.2)
        assertEquals(25.0, tramos.single().pkHasta!!, 0.2)
    }

    @Test
    fun `sin hitos el PK se estima desde Palma y el sentido sale del eje, no del dibujo`() {
        // La via se dibuja hacia el este y Palma queda al oeste: el origen es el extremo oeste.
        val troceador = TroceadorVias(IndiceVias(listOf(via(1, "Ma-15", 0.0, 5_000.0))), IndicePuntosKm(emptyList()))
        val ida = troceador.trocear(traza(5_000.0)).single()
        val vuelta = troceador.trocear(traza(5_000.0, alReves = true)).single()

        assertEquals(0.0, ida.pkDesde!!, 0.05)
        assertEquals(5.0, ida.pkHasta!!, 0.05)
        assertTrue(ida.pkEstimado)
        assertEquals(SentidoVia.CRECIENTE, ida.sentido)
        assertEquals(5.0, vuelta.pkDesde!!, 0.05)
        assertEquals(0.0, vuelta.pkHasta!!, 0.05)
        assertEquals(SentidoVia.DECRECIENTE, vuelta.sentido)
    }

    @Test
    fun `el PK de hitos no es estimado`() {
        val pk = IndicePuntosKm(hitos("Ma-15", 20.0, 0, 5_000))
        val tramo =
            TroceadorVias(
                IndiceVias(listOf(via(1, "Ma-15", 0.0, 5_000.0))),
                pk,
            ).trocear(traza(5_000.0)).single()

        assertTrue(!tramo.pkEstimado)
    }

    @Test
    fun `una autovia con un way por calzada da ida creciente y vuelta decreciente`() {
        val vias =
            listOf(
                via(1, "Ma-15", 0.0, 10_000.0).copy(sentidoUnico = SentidoUnico.ADELANTE),
                via(2, "Ma-15", 10_000.0, 0.0, norteM = 25.0).copy(sentidoUnico = SentidoUnico.ADELANTE),
            )
        val troceador = TroceadorVias(IndiceVias(vias))
        val ida = troceador.trocear(traza(10_000.0)).single()
        // Vuelta por la otra calzada, que se dibuja hacia el oeste (a favor de su orden de dibujo).
        val vuelta = troceador.trocear(trazaPorCalzadaNorte(10_000.0, 25.0)).single()

        assertEquals(SentidoVia.CRECIENTE, ida.sentido)
        assertEquals(SentidoVia.DECRECIENTE, vuelta.sentido)
        assertEquals(0.0, ida.pkDesde!!, 0.1)
        assertEquals(10.0, ida.pkHasta!!, 0.1)
        assertEquals(10.0, vuelta.pkDesde!!, 0.1)
        assertEquals(0.0, vuelta.pkHasta!!, 0.1)
        assertTrue(!mismoTramoVia(ida, vuelta))
    }

    @Test
    fun `el nombre lleva el PK del eje con hitos y el destino`() {
        val vias = listOf(via(1, "Ma-15", 0.0, 10_000.0))
        val pk = IndicePuntosKm(hitos("Ma-15", 20.1, 0, 10_000))
        val tramo =
            TroceadorVias(IndiceVias(vias), pk, destino = { "Algaida" }).trocear(traza(9_700.0)).single()

        assertEquals("Ma-15 · km 20,1→29,8 · hacia Algaida", nombreTramoVia(tramo))
        assertEquals(SentidoVia.CRECIENTE, tramo.sentido)
    }

    @Test
    fun `una calle con nombre y sin ref no forma tramo`() {
        val vias =
            listOf(
                via(1, null, 0.0, 2_000.0, nombre = "Carrer de Palma"),
                via(2, null, 2_000.0, 4_000.0, nombre = "Carrer de Palma"),
            )

        assertTrue(TroceadorVias(IndiceVias(vias)).trocear(traza(4_000.0)).isEmpty())
    }

    @Test
    fun `sin eje por ser un enlace el sentido sale del orden de dibujo`() {
        val vias = listOf(via(1, "Ma-15", 0.0, 5_000.0).copy(clase = "motorway_link"))
        val troceador = TroceadorVias(IndiceVias(vias), IndicePuntosKm(emptyList()))

        val ida = troceador.trocear(traza(5_000.0)).single()
        val vuelta = troceador.trocear(traza(5_000.0, alReves = true)).single()

        assertNull(ida.pkDesde)
        assertNull(ida.pkHasta)
        assertEquals(SentidoVia.CRECIENTE, ida.sentido)
        assertEquals(SentidoVia.DECRECIENTE, vuelta.sentido)
    }

    @Test
    fun `solo se agrupa una carretera con varias vias del mismo ref y Ma-13A no es Ma-13`() {
        val vias =
            listOf(via(1, "Ma-13", 0.0, 2_000.0), via(2, "Ma-13", 2_000.0, 4_000.0), via(3, "Ma-13A", 4_000.0, 6_500.0))
        val tramos = TroceadorVias(IndiceVias(vias)).trocear(traza(6_500.0))

        assertEquals(listOf("Ma-13", "Ma-13A"), tramos.map { it.ref })
    }

    @Test
    fun `usa el resolutor de destino y por defecto no inventa ninguno`() {
        val indice = IndiceVias(listOf(via(1, "Ma-15", 0.0, 5_000.0)))

        assertNull(TroceadorVias(indice).trocear(traza(5_000.0)).single().destino)

        val vistos = mutableListOf<TramoVia>()
        val tramo =
            TroceadorVias(indice, destino = { vistos.add(it).let { _ -> "Manacor" } }).trocear(traza(5_000.0)).single()

        assertEquals("Manacor", tramo.destino)
        assertNull(vistos.single().destino)
        assertEquals("Ma-15 · km ~0,0→~5,0 · hacia Manacor", nombreTramoVia(tramo))
    }

    @Test
    fun `el nombre lleva ref, kilometros con coma y destino`() {
        val tramo = tramo(pkDesde = 20.1, pkHasta = 32.4, destino = "Manacor")

        assertEquals("Ma-15 · km 20,1→32,4 · hacia Manacor", nombreTramoVia(tramo))
        assertEquals("Ma-15 · km 20,1→32,4", nombreTramoVia(tramo.copy(destino = null)))
        assertEquals("Ma-15 · km 32,4→20,1", nombreTramoVia(tramo.copy(pkDesde = 32.4, pkHasta = 20.1, destino = " ")))
    }

    @Test
    fun `el PK estimado lleva tilde en el nombre y el de mojones reales no`() {
        val tramo = tramo(pkDesde = 20.1, pkHasta = 32.4, destino = "Manacor")

        assertEquals("Ma-15 · km 20,1→32,4 · hacia Manacor", nombreTramoVia(tramo.copy(pkEstimado = false)))
        assertEquals("Ma-15 · km ~20,1→~32,4 · hacia Manacor", nombreTramoVia(tramo.copy(pkEstimado = true)))
        val inverso = tramo.copy(pkDesde = 32.4, pkHasta = 20.1, destino = null, pkEstimado = true)
        assertEquals("Ma-15 · km ~32,4→~20,1", nombreTramoVia(inverso))
    }

    @Test
    fun `sin PK el nombre da la longitud y sin ref ni nombre usa el de reserva`() {
        val tramo = tramo(pkDesde = null, pkHasta = null).copy(desdeM = 1_000.0, hastaM = 13_300.0)

        assertEquals("Ma-15 · 12,3 km", nombreTramoVia(tramo))
        assertEquals("Ma-15 · 12,3 km · hacia Inca", nombreTramoVia(tramo.copy(destino = "Inca")))
        assertEquals("Ma-13 · 12,3 km", nombreTramoVia(tramo.copy(ref = "Ma-13;Ma-13A")))
        assertEquals("Vía sin nombre · 12,3 km", nombreTramoVia(tramo.copy(ref = null, nombreVia = null)))
    }

    @Test
    fun `la clave es la carretera normalizada y el sentido`() {
        val a = tramo(ref = "Ma-15")
        assertEquals(ClaveTramoVia("MA15", SentidoVia.CRECIENTE), a.clave)
        assertEquals(a.clave, tramo(ref = "ma 15").clave)
        assertEquals(
            ClaveTramoVia("~CARRERMAJOR", SentidoVia.CRECIENTE),
            tramo(ref = null).copy(nombreVia = "Carrer Major").clave,
        )
        assertNull(tramo(ref = null).clave)
    }

    @Test
    fun `el solape por PK es la interseccion entre el intervalo menor`() {
        val a = tramo(pkDesde = 10.0, pkHasta = 20.0)

        assertEquals(1.0, solapeTramosVia(a, tramo(pkDesde = 12.0, pkHasta = 18.0)), 1e-9)
        assertEquals(0.5, solapeTramosVia(a, tramo(pkDesde = 15.0, pkHasta = 25.0)), 1e-9)
        assertEquals(0.5, solapeTramosVia(a, tramo(pkDesde = 25.0, pkHasta = 15.0)), 1e-9)
        assertEquals(0.0, solapeTramosVia(a, tramo(pkDesde = 20.0, pkHasta = 30.0)), 1e-9)
        assertEquals(0.0, solapeTramosVia(a, tramo(pkDesde = 5.0, pkHasta = 5.0)), 1e-9)
    }

    @Test
    fun `el solape por geometria sin PK mira los puntos cercanos`() {
        fun conTrazado(
            desdeM: Double,
            hastaM: Double,
        ) = tramo(pkDesde = null, pkHasta = null).copy(
            trazado =
                (0..10).map {
                    alEste(desdeM + (hastaM - desdeM) * it / 10)
                },
        )
        val a = conTrazado(0.0, 5_000.0)

        assertEquals(1.0, solapeTramosVia(a, conTrazado(1_000.0, 3_000.0)), 1e-9)
        assertEquals(1.0, solapeTramosVia(conTrazado(1_000.0, 3_000.0), a), 1e-9)
        assertEquals(0.0, solapeTramosVia(a, conTrazado(8_000.0, 9_000.0)), 1e-9)
        assertEquals(0.0, solapeTramosVia(a, tramo(pkDesde = null, pkHasta = null)), 1e-9)
        // Uno con PK y el otro sin: se compara por geometría.
        assertEquals(1.0, solapeTramosVia(a.copy(pkDesde = 1.0, pkHasta = 2.0), conTrazado(0.0, 2_000.0)), 1e-9)
    }

    @Test
    fun `mismo tramo exige misma carretera, mismo sentido y solape suficiente`() {
        val a = tramo(pkDesde = 10.0, pkHasta = 20.0)

        assertTrue(mismoTramoVia(a, tramo(pkDesde = 12.0, pkHasta = 30.0)))
        assertTrue(!mismoTramoVia(a, tramo(pkDesde = 18.0, pkHasta = 30.0)))
        assertTrue(mismoTramoVia(a, tramo(pkDesde = 18.0, pkHasta = 30.0), umbral = 0.1))
        assertTrue(!mismoTramoVia(a, tramo(ref = "Ma-13", pkDesde = 10.0, pkHasta = 20.0)))
        assertTrue(!mismoTramoVia(a, tramo(sentido = SentidoVia.DECRECIENTE, pkDesde = 10.0, pkHasta = 20.0)))
        assertTrue(!mismoTramoVia(tramo(ref = null), tramo(ref = null)))
    }

    @Test
    fun `dos viajes por la misma carretera dan tramos que se reconocen como el mismo`() {
        val vias = listOf(via(1, "Ma-15", 0.0, 5_000.0))
        val pk = IndicePuntosKm(hitos("Ma-15", 20.0, 0, 5_000))
        val troceador = TroceadorVias(IndiceVias(vias), pk)
        val viaje1 = troceador.trocear(traza(5_000.0)).single()
        val viaje2 = troceador.trocear(traza(5_000.0, pasoM = 60.0)).single()
        val vuelta = troceador.trocear(traza(5_000.0, alReves = true)).single()

        assertTrue(mismoTramoVia(viaje1, viaje2))
        assertTrue(!mismoTramoVia(viaje1, vuelta))
    }

    private fun tramo(
        ref: String? = "Ma-15",
        pkDesde: Double? = 20.1,
        pkHasta: Double? = 32.4,
        sentido: SentidoVia = SentidoVia.CRECIENTE,
        destino: String? = null,
    ) = TramoVia(ref, null, 0.0, 12_300.0, pkDesde, pkHasta, sentido, destino)
}
