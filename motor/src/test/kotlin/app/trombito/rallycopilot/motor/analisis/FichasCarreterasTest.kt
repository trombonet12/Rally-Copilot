package app.trombito.rallycopilot.motor.analisis

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.EjesCarreteras
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.SentidoVia
import app.trombito.rallycopilot.motor.vias.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class FichasCarreterasTest {
    private fun via(
        id: Long,
        ref: String?,
        nombre: String? = null,
    ) = Via(id, "secondary", null, null, null, SentidoUnico.NO, ref, nombre, emptyList())

    private val a = via(1, "Ma-10", "Carretera de Valldemossa")
    private val b = via(2, "MA 10")
    private val c = via(3, "Ma-11")
    private val anonima = via(4, null)

    private fun curvas(
        vararg porGrado: Int,
        horquillas: Int = 0,
    ) = CurvasPorGrado(porGrado.toList(), horquillas = horquillas)

    private fun viaje(
        inicioMs: Long,
        usos: List<UsoVia>,
        marcha: List<UsoViaMarcha> = emptyList(),
    ) = ViajeParaFichas(
        inicioMs,
        AnalisisRecorrido(usos.sumOf { it.metros }, usos, CurvasPorGrado.VACIO),
        AnalisisMarcha(marcha),
    )

    private fun uso(
        via: Via,
        metros: Double,
        grados: CurvasPorGrado = CurvasPorGrado.VACIO,
    ) = UsoVia(via, metros, grados.total, grados)

    /** 100 m en [segundos] s: a 36 km/h con 10 s. */
    private fun marcha(
        metros: Double,
        segundos: Int,
    ) = Marcha(metros, segundos * 1_000L)

    @Test
    fun `sin viajes no hay fichas`() {
        assertTrue(FichasCarreteras.agregar(emptyList()).isEmpty())
    }

    @Test
    fun `suma kilometros, viajes, curvas por grado y ultima vez de la misma carretera`() {
        val fichas =
            FichasCarreteras.agregar(
                listOf(
                    viaje(
                        1_000L,
                        listOf(uso(a, 800.0, curvas(0, 0, 2, 0, 0, 0)), uso(b, 200.0, curvas(1, 0, 0, 0, 0, 0))),
                    ),
                    viaje(5_000L, listOf(uso(a, 500.0, curvas(0, 0, 1, 0, 0, 1, horquillas = 1)))),
                ),
            )
        val ficha = fichas.single()
        assertEquals("MA10", ficha.id)
        assertEquals("Ma-10", ficha.ref)
        assertEquals("Carretera de Valldemossa", ficha.nombre)
        assertEquals(2, ficha.viajes)
        assertEquals(1_500.0, ficha.metros, 0.001)
        assertEquals(listOf(1, 0, 3, 0, 0, 1), ficha.curvas.porGrado)
        assertEquals(1, ficha.curvas.horquillas)
        assertEquals(6, ficha.curvas.total)
        assertEquals(5_000L, ficha.ultimaVezMs)
        assertEquals(4.0, ficha.curvasPorKm!!, 0.001)
    }

    @Test
    fun `una via sin ref ni nombre no es una carretera`() {
        val fichas = FichasCarreteras.agregar(listOf(viaje(1L, listOf(uso(anonima, 900.0), uso(c, 900.0)))))
        assertEquals(listOf("MA11"), fichas.map { it.id })
    }

    @Test
    fun `sin ref la carretera se identifica por su nombre`() {
        val camino = via(5, null, "Camí de Sa Calobra")
        val ficha = FichasCarreteras.agregar(listOf(viaje(1L, listOf(uso(camino, 900.0))))).single()
        assertNull(ficha.ref)
        assertEquals("~CAMÍDESACALOBRA", ficha.id)
        assertEquals("Camí de Sa Calobra", ficha.nombre)
    }

    @Test
    fun `la marcha se reparte por sentido con las vias orientadas de forma coherente`() {
        // La vía a está dibujada en el sentido de los km crecientes y la b al revés. Se conduce hacia los km
        // crecientes: en a es «adelante» y en b es «atrás».
        val orientacion = OrientacionVia { if (it.id == 1L) true else false }
        val viajes =
            listOf(
                viaje(
                    1L,
                    listOf(uso(a, 1_000.0), uso(b, 1_000.0)),
                    listOf(
                        UsoViaMarcha(a, adelante = marcha(1_000.0, 50), atras = Marcha()),
                        UsoViaMarcha(b, adelante = Marcha(), atras = marcha(1_000.0, 50)),
                    ),
                ),
                // Vuelta: en a es «atrás» (decreciente) y en b «adelante» (decreciente), más lenta.
                viaje(
                    2L,
                    listOf(uso(a, 1_000.0), uso(b, 1_000.0)),
                    listOf(
                        UsoViaMarcha(a, adelante = Marcha(), atras = marcha(1_000.0, 100)),
                        UsoViaMarcha(b, adelante = marcha(1_000.0, 100), atras = Marcha()),
                    ),
                ),
            )
        val ficha = FichasCarreteras.agregar(viajes, orientacion).single()
        assertEquals(2_000.0, ficha.creciente.marcha.metros, 0.001)
        assertEquals(72.0, ficha.creciente.velMediaKmh!!, 0.01)
        assertEquals(36.0, ficha.decreciente.velMediaKmh!!, 0.01)
        assertEquals(1, ficha.creciente.viajes)
        assertEquals(1, ficha.decreciente.viajes)
        assertNull(ficha.sinSentido.velMediaKmh)
        assertEquals(48.0, ficha.velMediaKmh!!, 0.01)
        assertEquals(4_000.0, ficha.marcha.metros, 0.001)
    }

    @Test
    fun `una via que no se puede orientar suma a sin sentido y no a los otros`() {
        val ficha =
            FichasCarreteras
                .agregar(
                    listOf(
                        viaje(
                            1L,
                            listOf(uso(a, 1_000.0)),
                            listOf(UsoViaMarcha(a, marcha(600.0, 30), marcha(400.0, 20))),
                        ),
                    ),
                ).single()
        assertEquals(1_000.0, ficha.sinSentido.marcha.metros, 0.001)
        assertEquals(1, ficha.sinSentido.viajes)
        assertEquals(0, ficha.creciente.viajes)
        assertNull(ficha.creciente.velMediaKmh)
        assertEquals(72.0, ficha.velMediaKmh!!, 0.01)
    }

    @Test
    fun `una marcha de menos de una pasada en un sentido no cuenta como viaje en ese sentido`() {
        val ficha =
            FichasCarreteras
                .agregar(
                    listOf(
                        viaje(
                            1L,
                            listOf(uso(a, 1_000.0)),
                            listOf(UsoViaMarcha(a, marcha(1_000.0, 50), marcha(20.0, 2))),
                        ),
                    ),
                    OrientacionVia { true },
                ).single()
        assertEquals(1, ficha.creciente.viajes)
        assertEquals(0, ficha.decreciente.viajes)
    }

    @Test
    fun `sin tiempos la ficha existe pero sin ritmo`() {
        val ficha = FichasCarreteras.agregar(listOf(viaje(1L, listOf(uso(a, 900.0))))).single()
        assertNull(ficha.velMediaKmh)
        assertEquals(0, ficha.creciente.viajes + ficha.decreciente.viajes + ficha.sinSentido.viajes)
    }

    @Test
    fun `la marcha de una carretera sin uso en ese viaje se ignora`() {
        val fichas =
            FichasCarreteras.agregar(
                listOf(viaje(1L, listOf(uso(a, 900.0)), listOf(UsoViaMarcha(c, marcha(900.0, 45), Marcha())))),
            )
        assertEquals(listOf("MA10"), fichas.map { it.id })
        assertNull(fichas.single().velMediaKmh)
    }

    @Test
    fun `deSentido da el sentido pedido`() {
        val ficha =
            FichaCarretera(
                "MA10",
                "Ma-10",
                null,
                1,
                1_000.0,
                CurvasPorGrado.VACIO,
                1L,
                creciente = SentidoCarretera(2, marcha(1_000.0, 50)),
                decreciente = SentidoCarretera(1, marcha(1_000.0, 100)),
            )
        assertEquals(2, ficha.deSentido(SentidoVia.CRECIENTE).viajes)
        assertEquals(1, ficha.deSentido(SentidoVia.DECRECIENTE).viajes)
        assertEquals(0, ficha.deSentido(SentidoVia.DESCONOCIDO).viajes)
    }

    private fun ficha(
        id: String,
        metros: Double,
        curvas: Int,
        ultimaVezMs: Long?,
        viajes: Int = 1,
    ) = FichaCarretera(id, id, null, viajes, metros, curvas(curvas), ultimaVezMs)

    private fun curvas(total: Int) = CurvasPorGrado(listOf(0, 0, total, 0, 0, 0))

    @Test
    fun `por uso va primero la que mas kilometros tiene`() {
        val fichas =
            listOf(ficha("A", 5_000.0, 5, 1L), ficha("B", 20_000.0, 5, 2L), ficha("C", 20_000.0, 5, 3L, viajes = 4))
        assertEquals(listOf("C", "B", "A"), fichas.ordenadasPor(OrdenCarreteras.USO).map { it.id })
    }

    @Test
    fun `por curvas manda la densidad y no el total, y las cortas van detras`() {
        val fichas =
            listOf(
                // 40 curvas en 20 km = 2/km: la que más curvas suma, pero la menos revirada.
                ficha("LARGA", 20_000.0, 40, 1L),
                // 30 curvas en 3 km = 10/km.
                ficha("REVIRADA", 3_000.0, 30, 2L),
                // 2 curvas en 200 m = 10/km, pero demasiado corta para fiarse.
                ficha("CORTA", 200.0, 2, 3L),
            )
        assertEquals(listOf("REVIRADA", "LARGA", "CORTA"), fichas.ordenadasPor(OrdenCarreteras.CURVAS).map { it.id })
    }

    @Test
    fun `por reciente va primero la ultima y sin fecha al final`() {
        val fichas = listOf(ficha("A", 1_000.0, 1, 10L), ficha("B", 1_000.0, 1, null), ficha("C", 1_000.0, 1, 30L))
        assertEquals(listOf("C", "A", "B"), fichas.ordenadasPor(OrdenCarreteras.RECIENTE).map { it.id })
    }

    @Test
    fun `agregar devuelve las fichas por uso`() {
        val fichas =
            FichasCarreteras.agregar(
                listOf(viaje(1L, listOf(uso(c, 5_000.0), uso(a, 9_000.0)))),
            )
        assertEquals(listOf("MA10", "MA11"), fichas.map { it.id })
    }

    // --- Orientación por ejes ---

    private val latBase = 39.6
    private val lonBase = 2.9
    private val metrosPorGradoLon = 111_195.0 * cos(Math.toRadians(latBase))

    private fun punto(x: Double) = PuntoGeo(latBase, lonBase + x / metrosPorGradoLon)

    private fun recta(
        id: Long,
        desdeX: Double,
        hastaX: Double,
        ref: String? = "Ma-10",
    ): Via {
        val n = (kotlin.math.abs(hastaX - desdeX) / 100.0).toInt()
        val paso = if (hastaX >= desdeX) 100.0 else -100.0
        return Via(
            id,
            "secondary",
            null,
            null,
            null,
            SentidoUnico.NO,
            ref,
            null,
            (0..n).map { punto(desdeX + it * paso) },
        )
    }

    @Test
    fun `las vias de una carretera se orientan hacia el mismo extremo del eje`() {
        // Dos vías encadenadas: una dibujada hacia el este y otra hacia el oeste. El eje sale de Palma (al oeste).
        val este = recta(1, 0.0, 1_000.0)
        val oeste = recta(2, 2_000.0, 1_000.0)
        val orientacion = OrientacionPorEjes(EjesCarreteras(listOf(este, oeste)))
        assertEquals(true, orientacion.adelanteCreciente(este))
        assertEquals(false, orientacion.adelanteCreciente(oeste))
    }

    @Test
    fun `una via sin ref o sin eje no se orienta`() {
        val sinRef = recta(3, 0.0, 1_000.0, ref = null)
        val orientacion = OrientacionPorEjes(EjesCarreteras(listOf(sinRef, recta(1, 0.0, 1_000.0))))
        assertNull(orientacion.adelanteCreciente(sinRef))
        assertNull(orientacion.adelanteCreciente(recta(9, 0.0, 1_000.0, ref = "Ma-99")))
    }

    @Test
    fun `una via casi sin recorrido no se orienta`() {
        val larga = recta(1, 0.0, 2_000.0)
        val corta =
            Via(7, "secondary", null, null, null, SentidoUnico.NO, "Ma-10", null, listOf(punto(500.0), punto(520.0)))
        assertNull(OrientacionPorEjes(EjesCarreteras(listOf(larga))).adelanteCreciente(corta))
    }

    @Test
    fun `el id de carretera ignora guiones, espacios y mayusculas`() {
        assertEquals(FichasCarreteras.idCarretera(a), FichasCarreteras.idCarretera(b))
        assertFalse(FichasCarreteras.idCarretera(a) == FichasCarreteras.idCarretera(c))
        assertNull(FichasCarreteras.idCarretera(anonima))
    }
}
