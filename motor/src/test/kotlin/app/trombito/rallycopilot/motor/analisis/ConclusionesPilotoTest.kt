package app.trombito.rallycopilot.motor.analisis

import app.trombito.rallycopilot.motor.tramos.MarcaTiempo
import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ConclusionesPilotoTest {
    private val dia = 86_400_000L

    private fun epoch(
        anio: Int,
        mes: Int,
        diaDelMes: Int,
    ) = LocalDate
        .of(anio, mes, diaDelMes)
        .atStartOfDay()
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()

    private fun via(
        id: Long,
        ref: String? = "Ma-$id",
    ) = Via(id, "secondary", null, null, null, SentidoUnico.NO, ref, null, emptyList())

    /** [km] kilómetros en marcha a [kmh] en la vía [id], todos hacia delante, en [pasadas] pasadas. */
    private fun velocidad(
        id: Long,
        km: Double,
        kmh: Double,
        pasadas: Int = 1,
    ): VelocidadVia {
        val metros = km * 1_000.0
        val marcha = Marcha(metros, (metros / (kmh / 3.6) * 1_000.0).toLong())
        return VelocidadVia(via(id), pasadas, marcha, Marcha())
    }

    private fun ultimoViaje(
        km: Double,
        kmh: Double,
    ): AnalisisMarcha {
        val metros = km * 1_000.0
        val marcha = Marcha(metros, (metros / (kmh / 3.6) * 1_000.0).toLong())
        return AnalisisMarcha(listOf(UsoViaMarcha(via(1), marcha, Marcha())))
    }

    private fun carretera(
        ref: String?,
        pasadas: Int,
        km: Double,
        curvas: Int,
        nombre: String? = null,
    ) = Carretera(ref, nombre, pasadas, km * 1_000.0, curvas)

    private fun conducida(
        id: Long,
        pasadas: Int,
        km: Double,
    ) = ViaConducida(via(id), pasadas, km * 1_000.0, 0)

    private fun resumen(
        recorridos: Int = 10,
        carreteras: List<Carretera> = emptyList(),
        vias: List<ViaConducida> = emptyList(),
        curvas: CurvasPorGrado = CurvasPorGrado.VACIO,
    ) = ResumenCarreteras(recorridos, 0.0, curvas, vias, carreteras)

    private val perfilVacio = PerfilPiloto.de(emptyList(), emptyList())

    @Suppress("LongParameterList")
    private fun conclusiones(
        perfil: PerfilPiloto = perfilVacio,
        carreteras: ResumenCarreteras = resumen(),
        velocidades: List<VelocidadVia> = emptyList(),
        porGrado: VelocidadPorGrado = VelocidadPorGrado.VACIO,
        ultimo: AnalisisMarcha? = null,
        ahoraMs: Long = epoch(2026, 11, 23),
        primerViajeMs: Long? = null,
    ) = ConclusionesPiloto.de(perfil, carreteras, velocidades, porGrado, ultimo, ahoraMs, primerViajeMs)

    private fun List<Conclusion>.de(tipo: TipoConclusion) = firstOrNull { it.tipo == tipo }

    @Test
    fun `sin datos no sale ninguna tarjeta`() {
        assertTrue(conclusiones().isEmpty())
    }

    // Ritmo

    @Test
    fun `el ritmo es la media en marcha de todas las vias sumando metros y tiempos`() {
        // 30 km a 90 y 30 km a 45: la media real es 60 km/h (no 67,5).
        val v = listOf(velocidad(1, 30.0, 90.0), velocidad(2, 30.0, 45.0))
        val ritmo = conclusiones(velocidades = v).de(TipoConclusion.RITMO)!!
        assertEquals("60 km/h", ritmo.cifra)
        assertEquals("Vas a 60 km/h de media en marcha, sin contar las paradas.", ritmo.frase)
    }

    @Test
    fun `el ritmo no sale con menos de 5 km en marcha`() {
        assertNull(conclusiones(velocidades = listOf(velocidad(1, 4.9, 70.0))).de(TipoConclusion.RITMO))
        assertEquals(
            "70 km/h",
            conclusiones(velocidades = listOf(velocidad(1, 5.0, 70.0))).de(TipoConclusion.RITMO)!!.cifra,
        )
    }

    // Último viaje

    @Test
    fun `el ultimo viaje se compara con el resto de viajes sin contarse a si mismo`() {
        // Resto: 50 km a 70. Último: 10 km a 80. Global = 60 km, que no es 70.
        val v = listOf(velocidad(1, 50.0, 70.0), velocidad(1, 10.0, 80.0))
        val c = conclusiones(velocidades = v, ultimo = ultimoViaje(10.0, 80.0)).de(TipoConclusion.ULTIMO_VIAJE)!!
        assertEquals("80 km/h", c.cifra)
        assertEquals("En tu último viaje fuiste a 80 km/h en marcha: 10 por encima de tu ritmo habitual (70).", c.frase)
    }

    @Test
    fun `el ultimo viaje mas lento dice por debajo y uno igual dice como siempre`() {
        val lento =
            conclusiones(
                velocidades = listOf(velocidad(1, 50.0, 70.0), velocidad(1, 10.0, 60.0)),
                ultimo = ultimoViaje(10.0, 60.0),
            ).de(TipoConclusion.ULTIMO_VIAJE)!!
        assertEquals(
            "En tu último viaje fuiste a 60 km/h en marcha: 10 por debajo de tu ritmo habitual (70).",
            lento.frase,
        )
        val igual =
            conclusiones(
                velocidades = listOf(velocidad(1, 50.0, 70.0), velocidad(1, 10.0, 71.0)),
                ultimo = ultimoViaje(10.0, 71.0),
            ).de(TipoConclusion.ULTIMO_VIAJE)!!
        assertEquals("En tu último viaje fuiste a 71 km/h en marcha: como siempre (70).", igual.frase)
    }

    @Test
    fun `el ultimo viaje exige un viaje de 2 km y 5 km de historia`() {
        val corto =
            conclusiones(
                velocidades = listOf(velocidad(1, 50.0, 70.0), velocidad(1, 1.5, 80.0)),
                ultimo = ultimoViaje(1.5, 80.0),
            )
        assertNull(corto.de(TipoConclusion.ULTIMO_VIAJE))
        val sinHistoria =
            conclusiones(
                velocidades = listOf(velocidad(1, 3.0, 70.0), velocidad(1, 10.0, 80.0)),
                ultimo = ultimoViaje(10.0, 80.0),
            )
        assertNull(sinHistoria.de(TipoConclusion.ULTIMO_VIAJE))
        assertNull(conclusiones(velocidades = listOf(velocidad(1, 50.0, 70.0))).de(TipoConclusion.ULTIMO_VIAJE))
    }

    // Carretera más conducida

    @Test
    fun `la carretera que mas conduces es la de mas viajes y se explica`() {
        val r =
            resumen(
                recorridos = 10,
                carreteras = listOf(carretera("Ma-10", 6, 86.4, 300), carretera("Ma-13", 4, 120.0, 100)),
            )
        val c = conclusiones(carreteras = r).de(TipoConclusion.CARRETERA_FAVORITA)!!
        assertEquals("Ma-10", c.cifra)
        assertEquals("Tu carretera es la Ma-10: has pasado en 6 de tus 10 viajes y llevas 86 km en ella.", c.frase)
    }

    @Test
    fun `a igualdad de viajes gana la de mas kilometros y una sin ref lleva su nombre`() {
        val r =
            resumen(
                recorridos = 5,
                carreteras =
                    listOf(
                        carretera("Ma-10", 5, 7.0, 10),
                        carretera(null, 5, 8.0, 10, nombre = "Camí de Sa Pobla"),
                    ),
            )
        val c = conclusiones(carreteras = r).de(TipoConclusion.CARRETERA_FAVORITA)!!
        assertEquals(
            "Tu carretera es Camí de Sa Pobla: has pasado en todos tus 5 viajes y llevas 8 km en ella.",
            c.frase,
        )
    }

    @Test
    fun `la carretera favorita exige 3 viajes 2 pasadas y 5 km`() {
        fun frase(
            recorridos: Int,
            pasadas: Int,
            km: Double,
        ) = conclusiones(carreteras = resumen(recorridos, listOf(carretera("Ma-10", pasadas, km, 50))))
            .de(TipoConclusion.CARRETERA_FAVORITA)
        assertNull(frase(recorridos = 2, pasadas = 2, km = 20.0))
        assertNull(frase(recorridos = 10, pasadas = 1, km = 20.0))
        assertNull(frase(recorridos = 10, pasadas = 3, km = 4.9))
        assertTrue(frase(recorridos = 3, pasadas = 2, km = 5.0) != null)
    }

    // Más revirada

    @Test
    fun `la mas revirada se elige por curvas por km y no por total`() {
        val r =
            resumen(
                carreteras =
                    listOf(
                        carretera("Ma-10", 6, 86.0, 300), // 3,5 por km
                        carretera("Ma-2141", 2, 12.0, 108), // 9 por km
                    ),
            )
        val c = conclusiones(carreteras = r).de(TipoConclusion.CARRETERA_REVIRADA)!!
        assertEquals("9 curvas/km", c.cifra)
        assertEquals("La más revirada que has hecho: Ma-2141, 9 curvas por kilómetro.", c.frase)
    }

    @Test
    fun `la mas revirada usa un decimal con coma por debajo de 10`() {
        val c =
            conclusiones(carreteras = resumen(carreteras = listOf(carretera("Ma-1", 1, 10.0, 75))))
                .de(TipoConclusion.CARRETERA_REVIRADA)!!
        assertEquals("7,5 curvas/km", c.cifra)
    }

    @Test
    fun `la mas revirada exige 2 km conducidos y 2 curvas por km`() {
        // 1,9 km con 30 curvas (muy revirada pero poco conducida) y 20 km con 39 curvas (1,95 por km).
        val r = resumen(carreteras = listOf(carretera("Ma-1", 1, 1.9, 30), carretera("Ma-2", 3, 20.0, 39)))
        assertNull(conclusiones(carreteras = r).de(TipoConclusion.CARRETERA_REVIRADA))
        val justa = resumen(carreteras = listOf(carretera("Ma-2", 3, 20.0, 40)))
        assertEquals("2 curvas/km", conclusiones(carreteras = justa).de(TipoConclusion.CARRETERA_REVIRADA)!!.cifra)
    }

    // Curva tipo

    private fun curvas(
        porGrado: List<Int>,
        escuadras: Int = 0,
        horquillas: Int = 0,
    ) = CurvasPorGrado(porGrado, escuadras, horquillas)

    @Test
    fun `la curva tipo reparte 10 curvas entre rapidas medias lentas y horquillas`() {
        // 50 rápidas (5-6), 30 medias (3-4), 10 lentas (1-2) y 10 horquillas.
        val c = curvas(listOf(5, 5, 15, 15, 25, 25), horquillas = 10)
        val tipo = conclusiones(carreteras = resumen(curvas = c)).de(TipoConclusion.CURVA_TIPO)!!
        assertEquals("5 de 10", tipo.cifra)
        assertEquals(
            "De cada 10 curvas, 5 son rápidas (grados 5 y 6), 3 medias (3 y 4), 1 lenta (1 y 2) y 1 horquilla.",
            tipo.frase,
        )
    }

    @Test
    fun `la curva tipo suma 10 aunque los porcentajes no sean redondos`() {
        // Un tercio de cada categoría: 10 se reparte 4, 3, 3 (el resto mayor empata y gana la primera).
        val c = curvas(listOf(10, 0, 10, 0, 0, 10))
        val tipo = conclusiones(carreteras = resumen(curvas = c)).de(TipoConclusion.CURVA_TIPO)!!
        assertEquals("4 de 10", tipo.cifra)
        assertEquals(
            "De cada 10 curvas, 4 son rápidas (grados 5 y 6), 3 medias (3 y 4) y 3 lentas (1 y 2).",
            tipo.frase,
        )
    }

    @Test
    fun `la curva tipo cuenta las escuadras como medias`() {
        val c = curvas(listOf(0, 0, 0, 0, 20, 0), escuadras = 20)
        val tipo = conclusiones(carreteras = resumen(curvas = c)).de(TipoConclusion.CURVA_TIPO)!!
        assertEquals("De cada 10 curvas, 5 son rápidas (grados 5 y 6) y 5 medias (3 y 4).", tipo.frase)
    }

    @Test
    fun `la curva tipo calla las categorias en cero`() {
        val c = curvas(listOf(0, 0, 0, 0, 0, 30))
        val tipo = conclusiones(carreteras = resumen(curvas = c)).de(TipoConclusion.CURVA_TIPO)!!
        assertEquals("De cada 10 curvas, 10 son rápidas (grados 5 y 6).", tipo.frase)
    }

    @Test
    fun `la curva tipo exige 20 curvas`() {
        assertNull(
            conclusiones(
                carreteras = resumen(curvas = curvas(listOf(0, 0, 0, 0, 0, 19))),
            ).de(TipoConclusion.CURVA_TIPO),
        )
        assertTrue(
            conclusiones(
                carreteras = resumen(curvas = curvas(listOf(0, 0, 0, 0, 0, 20))),
            ).de(TipoConclusion.CURVA_TIPO) !=
                null,
        )
    }

    // Km estrenados

    @Test
    fun `los km estrenados suman solo las vias con una pasada`() {
        val r = resumen(vias = listOf(conducida(1, 1, 8.0), conducida(2, 1, 6.2), conducida(3, 4, 50.0)))
        val c =
            conclusiones(
                carreteras = r,
                ahoraMs = epoch(2026, 11, 23),
                primerViajeMs = epoch(2026, 11, 2),
            ).de(TipoConclusion.KM_ESTRENADOS)!!
        assertEquals("14 km", c.cifra)
        assertEquals("Este mes has estrenado 14 km de carretera.", c.frase)
    }

    @Test
    fun `si el primer viaje es de antes del mes no se dice este mes`() {
        val r = resumen(vias = listOf(conducida(1, 1, 8.0)))
        val c =
            conclusiones(carreteras = r, ahoraMs = epoch(2026, 11, 23), primerViajeMs = epoch(2026, 10, 30))
                .de(TipoConclusion.KM_ESTRENADOS)!!
        assertEquals("Tienes 8 km de carretera estrenada: solo has pasado una vez por ellos.", c.frase)
        val sinFecha = conclusiones(carreteras = r).de(TipoConclusion.KM_ESTRENADOS)!!
        assertEquals(c.frase, sinFecha.frase)
    }

    @Test
    fun `los km estrenados exigen 5 km`() {
        assertNull(
            conclusiones(carreteras = resumen(vias = listOf(conducida(1, 1, 4.9)))).de(TipoConclusion.KM_ESTRENADOS),
        )
        assertNull(
            conclusiones(carreteras = resumen(vias = listOf(conducida(1, 2, 40.0)))).de(TipoConclusion.KM_ESTRENADOS),
        )
    }

    @Test
    fun `un decimal por debajo de 10 km estrenados`() {
        val c =
            conclusiones(
                carreteras = resumen(vias = listOf(conducida(1, 1, 6.25))),
            ).de(TipoConclusion.KM_ESTRENADOS)!!
        assertEquals("6,3 km", c.cifra)
    }

    // Récords

    private fun pasada(
        tramoId: String,
        id: String,
        totalMs: Long,
        dias: Int,
    ) = PasadaRegistrada(
        id,
        Pasada(tramoId, listOf(MarcaTiempo(0.0, 0L), MarcaTiempo(1000.0, totalMs)), totalMs),
        dias * dia,
    )

    private fun perfil(vararg tramos: TramoRegistrado) = PerfilPiloto.de(tramos.toList(), emptyList())

    @Test
    fun `la primera pasada de un tramo no es un record`() {
        val solo = TramoRegistrado("a", "Ma-10 Sóller", 1000.0, listOf(pasada("a", "a1", 100_000L, 1)))
        assertNull(conclusiones(perfil = perfil(solo)).de(TipoConclusion.RECORDS))
    }

    @Test
    fun `una pasada peor que la anterior tampoco`() {
        val peor =
            TramoRegistrado(
                "a",
                "Ma-10 Sóller",
                1000.0,
                listOf(pasada("a", "a1", 100_000L, 1), pasada("a", "a2", 105_000L, 2)),
            )
        assertNull(conclusiones(perfil = perfil(peor)).de(TipoConclusion.RECORDS))
    }

    @Test
    fun `un record de verdad dice cuanto mejoro`() {
        val mejor =
            TramoRegistrado(
                "a",
                "Ma-10 Sóller",
                1000.0,
                listOf(pasada("a", "a1", 100_000L, 1), pasada("a", "a2", 97_700L, 2)),
            )
        val c = conclusiones(perfil = perfil(mejor)).de(TipoConclusion.RECORDS)!!
        assertEquals("1", c.cifra)
        assertEquals("Has mejorado tu mejor tiempo en Ma-10 Sóller: 2,3 s menos que el anterior.", c.frase)
    }

    @Test
    fun `varios records cuentan los de todos los tramos y nombran el mas reciente`() {
        val a =
            TramoRegistrado(
                "a",
                "Ma-10 Sóller",
                1000.0,
                listOf(pasada("a", "a1", 100_000L, 1), pasada("a", "a2", 95_000L, 2), pasada("a", "a3", 96_000L, 3)),
            )
        val b =
            TramoRegistrado(
                "b",
                "Ma-2141 Escorca",
                1000.0,
                listOf(pasada("b", "b1", 200_000L, 4), pasada("b", "b2", 135_000L, 5)),
            )
        val c = conclusiones(perfil = perfil(a, b)).de(TipoConclusion.RECORDS)!!
        assertEquals("2", c.cifra)
        assertEquals("Has mejorado tu mejor tiempo 2 veces; la última, en Ma-2141 Escorca (1 min 05 s menos).", c.frase)
    }

    // Velocidad por grado

    private fun porGrado(vararg datos: Triple<Int, Int, Double>): VelocidadPorGrado {
        var total = VelocidadPorGrado.VACIO
        for ((grado, curvas, kmh) in datos) {
            val paso = PasoCurvas(curvas, curvas * kmh)
            total =
                total.mas(
                    if (grado == 0) {
                        VelocidadPorGrado(horquillas = paso)
                    } else {
                        VelocidadPorGrado(porGrado = List(6) { if (it == grado - 1) paso else PasoCurvas() })
                    },
                )
        }
        return total
    }

    @Test
    fun `la velocidad por grado con un solo grado con datos`() {
        val c = conclusiones(porGrado = porGrado(Triple(3, 12, 48.4))).de(TipoConclusion.VELOCIDAD_POR_GRADO)!!
        assertEquals("48 km/h", c.cifra)
        assertEquals("En las curvas de grado 3 pasas a 48 km/h de media.", c.frase)
    }

    @Test
    fun `la velocidad por grado enseña hasta 3 grados los de mas curvas de cerrada a suave`() {
        val medida =
            porGrado(
                Triple(0, 6, 17.0),
                Triple(2, 30, 31.0),
                Triple(3, 40, 48.0),
                Triple(5, 20, 79.0),
                Triple(6, 5, 95.0),
            )
        val c = conclusiones(porGrado = medida).de(TipoConclusion.VELOCIDAD_POR_GRADO)!!
        // Los tres con más curvas (3, 2 y 5), ordenados del más cerrado al más suave; la cifra es la del más frecuente.
        assertEquals("48 km/h", c.cifra)
        assertEquals("En las de grado 2 pasas a 31 km/h de media; en las de 3, a 48; en las de 5, a 79.", c.frase)
    }

    @Test
    fun `la velocidad por grado nombra las horquillas`() {
        val c =
            conclusiones(
                porGrado = porGrado(Triple(0, 8, 19.0), Triple(4, 10, 55.0)),
            ).de(TipoConclusion.VELOCIDAD_POR_GRADO)!!
        assertEquals("En las horquillas pasas a 19 km/h de media; en las de 4, a 55.", c.frase)
    }

    @Test
    fun `la velocidad por grado exige 5 curvas medidas en el grado`() {
        assertNull(conclusiones(porGrado = porGrado(Triple(3, 4, 48.0))).de(TipoConclusion.VELOCIDAD_POR_GRADO))
        val c =
            conclusiones(
                porGrado = porGrado(Triple(3, 5, 48.0), Triple(2, 4, 30.0)),
            ).de(TipoConclusion.VELOCIDAD_POR_GRADO)!!
        assertEquals("En las curvas de grado 3 pasas a 48 km/h de media.", c.frase)
    }

    // Conjunto

    @Test
    fun `las tarjetas salen en el orden del enum y sin repetirse`() {
        val perfilConRecord =
            perfil(
                TramoRegistrado(
                    "a",
                    "Ma-10",
                    1000.0,
                    listOf(pasada("a", "a1", 100_000L, 1), pasada("a", "a2", 90_000L, 2)),
                ),
            )
        val r =
            resumen(
                recorridos = 10,
                carreteras = listOf(carretera("Ma-10", 6, 86.0, 600)),
                vias = listOf(conducida(1, 1, 9.0)),
                curvas = curvas(listOf(0, 0, 10, 10, 10, 10)),
            )
        val todas =
            conclusiones(
                perfil = perfilConRecord,
                carreteras = r,
                velocidades = listOf(velocidad(1, 50.0, 70.0), velocidad(1, 10.0, 80.0)),
                porGrado = porGrado(Triple(3, 12, 48.0)),
                ultimo = ultimoViaje(10.0, 80.0),
                primerViajeMs = epoch(2026, 11, 2),
            )
        assertEquals(TipoConclusion.values().toList(), todas.map { it.tipo })
    }

    @Test
    fun `es determinista`() {
        val r =
            resumen(
                carreteras = listOf(carretera("Ma-10", 6, 86.0, 600)),
                curvas = curvas(listOf(0, 0, 10, 10, 10, 10)),
            )
        val v = listOf(velocidad(1, 50.0, 70.0))
        assertEquals(conclusiones(carreteras = r, velocidades = v), conclusiones(carreteras = r, velocidades = v))
    }
}
