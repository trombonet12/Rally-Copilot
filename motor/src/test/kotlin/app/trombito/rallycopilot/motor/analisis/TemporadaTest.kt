package app.trombito.rallycopilot.motor.analisis

import app.trombito.rallycopilot.motor.tramos.MarcaTiempo
import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class TemporadaTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val utc = ZoneId.of("UTC")

    /** Epoch ms de una fecha-hora local ("2026-11-02T10:00") en [zona]. */
    private fun ms(
        local: String,
        zona: ZoneId = madrid,
    ): Long {
        val instante = LocalDateTime.parse(local).atZone(zona).toInstant()
        return instante.toEpochMilli()
    }

    private fun via(
        id: Long,
        ref: String?,
        nombre: String? = null,
    ) = Via(id, "secondary", null, null, null, SentidoUnico.NO, ref, nombre, emptyList())

    private fun uso(
        id: Long,
        ref: String?,
        nombre: String? = null,
        metros: Double = 1_000.0,
    ) = UsoVia(via(id, ref, nombre), metros, 0)

    private fun viaje(
        local: String,
        km: Double = 10.0,
        curvas: CurvasPorGrado = CurvasPorGrado.VACIO,
        usos: List<UsoVia> = emptyList(),
        zona: ZoneId = madrid,
    ) = RecorridoTemporada(ms(local, zona), km * 1_000.0, curvas, usos)

    private fun curvas(
        total: Int = 0,
        horquillas: Int = 0,
    ) = CurvasPorGrado(listOf(total, 0, 0, 0, 0, 0), 0, horquillas)

    private fun pasada(
        id: String,
        local: String,
        totalMs: Long,
    ) = PasadaRegistrada(
        id,
        Pasada("t", listOf(MarcaTiempo(0.0, 0L), MarcaTiempo(1_000.0, totalMs)), totalMs),
        ms(local),
    )

    private fun tramo(vararg pasadas: PasadaRegistrada) =
        TramoRegistrado("t", "Ma-10 · Sóller", 1_000.0, pasadas.toList())

    private val ahora = ms("2026-11-25T12:00") // miércoles de la semana ISO 48 (lunes 2026-11-23)

    private fun temporada(
        recorridos: List<RecorridoTemporada>,
        tramos: List<TramoRegistrado> = emptyList(),
        zona: ZoneId = madrid,
        ahoraMs: Long = ahora,
    ) = Temporada.de(recorridos, tramos, zona, ahoraMs)

    private fun hito(
        t: Temporada,
        id: String,
    ) = t.hitos.first { it.id == id }

    @Test
    fun `sin datos todo es cero y el nivel es debutante`() {
        val t = temporada(emptyList())
        assertEquals(0.0, t.kmTotales, 0.0)
        assertEquals(0, t.viajes)
        assertTrue(t.kmPorSemana.isEmpty())
        assertTrue(t.kmPorMes.isEmpty())
        assertEquals(Racha(0, 0), t.racha)
        assertTrue(t.records.isEmpty())
        assertEquals(NivelPiloto.DEBUTANTE, t.nivel.nivel)
        assertEquals(0.0, t.nivel.progreso, 0.0)
        assertTrue(t.hitos.none { it.conseguido })
        assertEquals(0, t.resumenMes.viajes)
        assertEquals(YearMonth.of(2026, 11), t.resumenMes.mes)
    }

    @Test
    fun `km por semana ISO con semanas vacias intermedias y hasta la semana actual`() {
        val t =
            temporada(
                listOf(
                    viaje("2026-11-02T10:00", km = 20.0), // lunes semana 45
                    viaje("2026-11-04T10:00", km = 5.0),
                    viaje("2026-11-17T10:00", km = 7.0), // semana 47
                ),
            )
        assertEquals(4, t.kmPorSemana.size) // 45, 46 (vacía), 47, 48 (actual, vacía)
        assertEquals(listOf(45, 46, 47, 48), t.kmPorSemana.map { it.semana })
        assertEquals(listOf(25.0, 0.0, 7.0, 0.0), t.kmPorSemana.map { it.km })
        assertEquals(listOf(2, 0, 1, 0), t.kmPorSemana.map { it.viajes })
        assertEquals(2026, t.kmPorSemana.first().anio)
    }

    @Test
    fun `el anio ISO cambia en la semana 53 de 2026 a la 1 de 2027`() {
        val recorridos = listOf(viaje("2026-12-31T10:00"), viaje("2027-01-04T10:00"))
        val t = temporada(recorridos, ahoraMs = ms("2027-01-05T10:00"))
        assertEquals(listOf(2026 to 53, 2027 to 1), t.kmPorSemana.map { it.anio to it.semana })
        assertEquals(2, t.racha.actual)
    }

    @Test
    fun `frontera de semana depende de la zona horaria`() {
        // Domingo 23:30 y lunes 00:30 en Madrid: dos semanas. En UTC el segundo es aún domingo (23:30Z).
        val domingo = ms("2026-11-22T23:30", madrid)
        val lunes = ms("2026-11-23T00:30", madrid)
        val recorridos =
            listOf(domingo, lunes).map { RecorridoTemporada(it, 1_000.0, CurvasPorGrado.VACIO, emptyList()) }
        val enMadrid = temporada(recorridos, zona = madrid, ahoraMs = lunes)
        val enUtc = temporada(recorridos, zona = utc, ahoraMs = lunes)
        assertEquals(2, enMadrid.kmPorSemana.size)
        assertEquals(2, enMadrid.racha.mejor)
        assertEquals(1, enUtc.kmPorSemana.size)
        assertEquals(1, enUtc.racha.mejor)
    }

    @Test
    fun `km por mes separa meses y los ordena`() {
        val t =
            temporada(
                listOf(
                    viaje("2026-11-02T10:00", km = 4.0),
                    viaje("2026-09-30T23:59", km = 6.0),
                    viaje("2026-11-30T23:59", km = 1.0),
                    viaje("2026-10-01T00:00", km = 2.0),
                ),
            )
        assertEquals(
            listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)),
            t.kmPorMes.map { it.mes },
        )
        assertEquals(listOf(6.0, 2.0, 5.0), t.kmPorMes.map { it.km })
        assertEquals(listOf(1, 1, 2), t.kmPorMes.map { it.viajes })
        assertEquals(13.0, t.kmTotales, 1e-9)
    }

    @Test
    fun `racha de semanas seguidas con ahora en la semana del ultimo viaje`() {
        val t =
            temporada(
                listOf(
                    viaje("2026-11-03T10:00"), // semana 45
                    viaje("2026-11-12T10:00"), // 46
                    viaje("2026-11-18T10:00"), // 47
                    viaje("2026-11-25T08:00"), // 48 (la actual)
                ),
            )
        assertEquals(Racha(actual = 4, mejor = 4), t.racha)
    }

    @Test
    fun `la racha sigue viva si esta semana aun no hay viaje pero si la pasada`() {
        val t = temporada(listOf(viaje("2026-11-10T10:00"), viaje("2026-11-17T10:00")))
        assertEquals(Racha(actual = 2, mejor = 2), t.racha)
    }

    @Test
    fun `racha rota deja la actual en cero y conserva la mejor`() {
        val t =
            temporada(
                listOf(
                    viaje("2026-10-05T10:00"), // semana 41
                    viaje("2026-10-12T10:00"), // 42
                    viaje("2026-10-19T10:00"), // 43
                    // 44 y 45 vacías
                    viaje("2026-11-09T10:00"), // 46
                ),
                ahoraMs = ms("2026-11-25T12:00"), // semana 48: la 47 está vacía
            )
        assertEquals(Racha(actual = 0, mejor = 3), t.racha)
    }

    @Test
    fun `racha rota y retomada cuenta la nueva como actual`() {
        val t =
            temporada(
                listOf(
                    viaje("2026-10-05T10:00"),
                    viaje("2026-10-12T10:00"),
                    viaje("2026-10-19T10:00"),
                    viaje("2026-11-16T10:00"), // 47
                    viaje("2026-11-24T10:00"), // 48
                ),
            )
        assertEquals(Racha(actual = 2, mejor = 3), t.racha)
    }

    @Test
    fun `varios viajes en la misma semana cuentan una sola semana de racha`() {
        val t = temporada(listOf(viaje("2026-11-23T08:00"), viaje("2026-11-24T08:00"), viaje("2026-11-25T08:00")))
        assertEquals(Racha(actual = 1, mejor = 1), t.racha)
    }

    @Test
    fun `hito de km conseguido en el viaje que cruza el umbral`() {
        val t =
            temporada(
                listOf(
                    viaje("2026-11-02T10:00", km = 60.0),
                    viaje("2026-11-09T10:00", km = 40.0),
                    viaje("2026-11-16T10:00", km = 90.0),
                ),
            )
        val h100 = hito(t, "km-100")
        assertTrue(h100.conseguido)
        assertEquals(ms("2026-11-09T10:00"), h100.conseguidoMs)
        assertEquals(1.0, h100.progreso, 0.0)
        assertEquals("100 km", h100.titulo)
        val h250 = hito(t, "km-250")
        assertFalse(h250.conseguido)
        assertEquals(190.0 / 250.0, h250.progreso, 1e-9)
        assertEquals(190.0, h250.valor, 1e-9)
    }

    @Test
    fun `hitos de curvas y horquillas suman en el catalogo`() {
        val t =
            temporada(
                listOf(
                    viaje("2026-11-02T10:00", curvas = curvas(total = 90, horquillas = 6)),
                    viaje("2026-11-09T10:00", curvas = curvas(total = 20, horquillas = 5)),
                ),
            )
        // 96 curvas en el primer viaje y 121 al acabar el segundo (las horquillas cuentan como curvas)
        assertEquals(121.0, hito(t, "curvas-100").valor, 0.0)
        assertEquals(ms("2026-11-09T10:00"), hito(t, "curvas-100").conseguidoMs)
        assertEquals("100 curvas", hito(t, "curvas-100").titulo)
        assertEquals("10 horquillas", hito(t, "horquillas-10").titulo)
        assertEquals(ms("2026-11-09T10:00"), hito(t, "horquillas-10").conseguidoMs)
        assertEquals(11.0 / 50.0, hito(t, "horquillas-50").progreso, 1e-9)
        assertNull(hito(t, "horquillas-50").conseguidoMs)
    }

    @Test
    fun `hito de carreteras distintas agrupa por ref o nombre sin repetir`() {
        val t =
            temporada(
                listOf(
                    viaje(
                        "2026-11-02T10:00",
                        usos = listOf(uso(1, "Ma-10"), uso(2, "ma-10 "), uso(3, null, "Camí Vell")),
                    ),
                    viaje(
                        "2026-11-09T10:00",
                        usos = listOf(uso(1, "Ma-10"), uso(4, "Ma-11"), uso(5, null, "camí vell")),
                    ),
                    viaje("2026-11-16T10:00", usos = listOf(uso(6, "Ma-13"), uso(8, null, null))),
                ),
            )
        assertEquals(4, t.carreterasDistintas)
        val h5 = hito(t, "carreteras-5")
        assertEquals(4.0, h5.valor, 0.0)
        assertEquals(0.8, h5.progreso, 1e-9)
        assertFalse(h5.conseguido)
    }

    @Test
    fun `hito de racha usa la mejor racha y su fecha es la del primer viaje de la semana que la completa`() {
        val t =
            temporada(
                listOf(
                    viaje("2026-10-05T10:00"),
                    viaje("2026-10-14T10:00"), // semana 42: completa 2 semanas
                    viaje("2026-10-16T10:00"),
                    viaje("2026-11-23T10:00"), // racha rota; la actual es 1
                ),
            )
        val h2 = hito(t, "racha-2")
        assertEquals(ms("2026-10-14T10:00"), h2.conseguidoMs)
        assertEquals("2 semanas seguidas", h2.titulo)
        assertEquals(0.5, hito(t, "racha-4").progreso, 1e-9)
    }

    @Test
    fun `siguiente hito es el pendiente mas avanzado`() {
        val t = temporada(listOf(viaje("2026-11-02T10:00", km = 120.0, curvas = curvas(total = 99))))
        // km-100 conseguido; km-250 = 0,48; curvas-100 = 0,99 -> la mas avanzada
        assertEquals("curvas-100", t.siguienteHito?.id)
    }

    @Test
    fun `nivel solo cuenta km curvas carreteras y semanas activas`() {
        // 300 km + 0,25 * 200 curvas + 10 * 3 carreteras + 10 * 2 semanas = 300 + 50 + 30 + 20 = 400
        val t =
            temporada(
                listOf(
                    viaje(
                        "2026-11-02T10:00",
                        km = 150.0,
                        curvas = curvas(total = 100),
                        usos = listOf(uso(1, "Ma-10"), uso(2, "Ma-11")),
                    ),
                    viaje("2026-11-09T10:00", km = 150.0, curvas = curvas(total = 100), usos = listOf(uso(3, "Ma-13"))),
                ),
            )
        assertEquals(400, t.puntos)
        assertEquals(NivelPiloto.PILOTO_DE_CLUB, t.nivel.nivel)
        assertEquals(NivelPiloto.PILOTO_NACIONAL, t.nivel.siguiente)
        assertEquals(1_200, t.nivel.umbralSiguiente)
        assertEquals(0.0, t.nivel.progreso, 1e-9)
    }

    @Test
    fun `nivel justo bajo el umbral y progreso intermedio`() {
        val bajo = temporada(listOf(viaje("2026-11-02T10:00", km = 89.0))) // 89 + 10 (una semana) = 99
        assertEquals(99, bajo.puntos)
        assertEquals(NivelPiloto.DEBUTANTE, bajo.nivel.nivel)
        assertEquals(0.99, bajo.nivel.progreso, 1e-9)
        val medio = temporada(listOf(viaje("2026-11-02T10:00", km = 240.0))) // 250 puntos: mitad entre 100 y 400
        assertEquals(NivelPiloto.AFICIONADO, medio.nivel.nivel)
        assertEquals(0.5, medio.nivel.progreso, 1e-9)
    }

    @Test
    fun `el ultimo nivel no tiene siguiente y su progreso es 1`() {
        val nivel = Progreso.nivel(25_000)
        assertEquals(NivelPiloto.LEYENDA, nivel.nivel)
        assertNull(nivel.siguiente)
        assertNull(nivel.umbralSiguiente)
        assertEquals(1.0, nivel.progreso, 0.0)
        assertEquals(6, NivelPiloto.entries.size)
    }

    @Test
    fun `la velocidad de las pasadas no cambia el nivel ni los puntos`() {
        val viajes = listOf(viaje("2026-11-02T10:00", km = 50.0))
        val lenta = temporada(viajes, listOf(tramo(pasada("a", "2026-11-02T10:00", 300_000L))))
        val rapida = temporada(viajes, listOf(tramo(pasada("a", "2026-11-02T10:00", 30_000L))))
        assertEquals(lenta.puntos, rapida.puntos)
        assertEquals(lenta.nivel, rapida.nivel)
    }

    @Test
    fun `la primera pasada de un tramo no es record`() {
        val t = temporada(emptyList(), listOf(tramo(pasada("p1", "2026-11-02T10:00", 100_000L))))
        assertTrue(t.records.isEmpty())
    }

    @Test
    fun `records solo cuando mejoran estrictamente el mejor anterior con su mejora en ms`() {
        val t =
            temporada(
                emptyList(),
                listOf(
                    tramo(
                        pasada("p3", "2026-11-20T10:00", 95_000L), // mejora 100 -> 95: record, 5 s
                        pasada("p1", "2026-11-02T10:00", 100_000L), // primera: no cuenta
                        pasada("p2", "2026-11-10T10:00", 110_000L), // peor: no
                        pasada("p4", "2026-11-22T10:00", 95_000L), // empate con el mejor: no
                        pasada("p5", "2026-11-23T10:00", 94_000L), // record, 1 s
                    ),
                ),
            )
        assertEquals(listOf("p5", "p3"), t.records.map { it.pasadaId }) // el mas reciente primero
        assertEquals(listOf(1_000L, 5_000L), t.records.map { it.mejoraMs })
        assertEquals(95_000L, t.records.first().anteriorMejorMs)
        assertEquals(94_000L, t.records.first().tiempoMs)
        assertEquals("Ma-10 · Sóller", t.records.first().nombreTramo)
        assertEquals(ms("2026-11-23T10:00"), t.records.first().fechaMs)
    }

    @Test
    fun `records de varios tramos son independientes`() {
        val a =
            TramoRegistrado(
                "a",
                "A",
                1_000.0,
                listOf(pasada("a1", "2026-11-02T10:00", 100_000L), pasada("a2", "2026-11-03T10:00", 90_000L)),
            )
        val b = TramoRegistrado("b", "B", 1_000.0, listOf(pasada("b1", "2026-11-04T10:00", 50_000L)))
        val t = temporada(emptyList(), listOf(a, b))
        assertEquals(listOf("a2"), t.records.map { it.pasadaId })
        assertEquals("a", t.records.single().tramoId)
    }

    @Test
    fun `resumen del mes cuenta solo el mes de ahora y marca carreteras hitos y records nuevos`() {
        val t =
            temporada(
                listOf(
                    viaje("2026-10-20T10:00", km = 90.0, usos = listOf(uso(1, "Ma-10"))), // octubre
                    // cruza 100 km
                    viaje("2026-11-03T10:00", km = 30.0, usos = listOf(uso(1, "Ma-10"), uso(2, "Ma-11"))),
                    viaje("2026-11-24T10:00", km = 5.0, usos = listOf(uso(3, null, "Camí Vell"))),
                ),
                listOf(tramo(pasada("p1", "2026-10-21T10:00", 100_000L), pasada("p2", "2026-11-05T10:00", 98_000L))),
            )
        val mes = t.resumenMes
        assertEquals(YearMonth.of(2026, 11), mes.mes)
        assertEquals(35.0, mes.km, 1e-9)
        assertEquals(2, mes.viajes)
        assertEquals(listOf("Ma-11", "Camí Vell"), mes.carreterasNuevas)
        assertEquals(listOf("km-100"), mes.hitosConseguidos.map { it.id })
        assertEquals(listOf("p2"), mes.records.map { it.pasadaId })
        assertEquals(t.nivel, mes.nivel)
    }

    @Test
    fun `el nivel del resumen de un mes pasado es el de fin de ese mes`() {
        val recorridos = listOf(viaje("2026-09-10T10:00", km = 100.0), viaje("2026-11-03T10:00", km = 500.0))
        val octubre = temporada(recorridos, ahoraMs = ms("2026-10-15T10:00"))
        assertEquals(YearMonth.of(2026, 10), octubre.resumenMes.mes)
        assertEquals(0, octubre.resumenMes.viajes)
        assertEquals(NivelPiloto.AFICIONADO, octubre.resumenMes.nivel.nivel) // 100 km + 10 = 110
        val noviembre = temporada(recorridos, ahoraMs = ahora)
        assertEquals(NivelPiloto.PILOTO_DE_CLUB, noviembre.resumenMes.nivel.nivel) // 600 km + 20 = 620
    }

    @Test
    fun `se construye desde un AnalisisRecorrido usando su distancia salvo que se indique otra`() {
        val analisis = AnalisisRecorrido(2_500.0, listOf(uso(1, "Ma-10")), curvas(total = 3, horquillas = 1))
        val r = RecorridoTemporada.de(ms("2026-11-02T10:00"), analisis)
        assertEquals(2_500.0, r.distanciaM, 0.0)
        assertEquals(4, r.curvas.total)
        assertEquals(1, r.usos.size)
        assertEquals(3_000.0, RecorridoTemporada.de(0L, analisis, 3_000.0).distanciaM, 0.0)
    }

    @Test
    fun `el orden de entrada de los recorridos no cambia el resultado`() {
        val recorridos =
            listOf(
                viaje("2026-11-16T10:00", km = 90.0),
                viaje("2026-11-02T10:00", km = 60.0),
                viaje("2026-11-09T10:00", km = 40.0),
            )
        assertEquals(temporada(recorridos), temporada(recorridos.reversed()))
        assertEquals(ms("2026-11-09T10:00"), hito(temporada(recorridos), "km-100").conseguidoMs)
    }
}
