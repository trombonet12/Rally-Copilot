package app.trombito.rallycopilot.data.tramos

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.data.db.RecorridoEntity
import app.trombito.rallycopilot.data.db.RecorridoPuntoEntity
import app.trombito.rallycopilot.data.db.SubtramoEntity
import app.trombito.rallycopilot.data.db.TIPO_VIA
import app.trombito.rallycopilot.data.db.TIPO_VIAJE
import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.domain.busqueda.Buscador
import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.domain.tramos.OrigenPasada
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.MarcaTiempo
import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.motor.vias.HitoKm
import app.trombito.rallycopilot.motor.vias.IndicePuntosKm
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/** Mundo de prueba: carreteras rectas hacia el este desde [BASE]; el viaje las recorre de 0 a 10 km. */
private val BASE = PuntoGeo(39.70, 2.70)

private fun alEste(metros: Double): PuntoGeo {
    val grados = Math.toDegrees(metros / (PuntoGeo.RADIO_TIERRA_M * Math.cos(Math.toRadians(BASE.lat))))
    return PuntoGeo(BASE.lat, BASE.lon + grados)
}

private fun via(
    id: Long,
    ref: String,
    desdeM: Double,
    hastaM: Double,
) = Via(
    id,
    "secondary",
    null,
    null,
    null,
    SentidoUnico.NO,
    ref,
    null,
    (0..10).map { alEste(desdeM + (hastaM - desdeM) * it / 10) },
)

private fun vias() =
    IndiceVias(
        listOf(
            via(1, "Ma-30", 0.0, 3_000.0),
            via(2, "Ma-15", 3_000.0, 7_000.0),
            via(3, "Ma-3220", 7_000.0, 10_000.0),
        ),
    )

private fun hitosMa15() =
    (3_000..7_000 step 500).map { HitoKm("Ma-15", 20.0 + (it - 3_000) / 1000.0, alEste(it.toDouble())) }

/** Un viaje hacia el este a [metrosPorSegundo] con un punto por segundo, de [desdeM] a [hastaM], desde [inicioMs]. */
private fun viaje(
    inicioMs: Long,
    metrosPorSegundo: Double = 20.0,
    desdeM: Double = 0.0,
    hastaM: Double = 10_000.0,
): List<PuntoRecorrido> {
    val pasos = ((hastaM - desdeM) / metrosPorSegundo).toInt()
    return (0..pasos).map {
        val p = alEste(desdeM + it * metrosPorSegundo)
        PuntoRecorrido(p.lat, p.lon, inicioMs + it * 1_000L, metrosPorSegundo)
    }
}

private class BuscadorFalso(
    private val lugar: String?,
) : Buscador {
    override suspend fun buscar(
        texto: String,
        cerca: PuntoGeo?,
        limite: Int,
    ): List<ResultadoBusqueda> = emptyList()

    override suspend fun lugarMasCercano(
        punto: PuntoGeo,
        radioM: Double,
    ): ResultadoBusqueda? = lugar?.let { ResultadoBusqueda(it, "pueblo", null, punto) }
}

@RunWith(RobolectricTestRunner::class)
class TramosViaTest {
    private lateinit var base: RallyDatabase
    private var conIndices = true
    private var ids = 0

    private var fallosDelMotor = 0

    private val fuente =
        FuenteIndicesVias {
            if (fallosDelMotor > 0) {
                fallosDelMotor--
                throw IllegalArgumentException("require del motor")
            }
            if (conIndices) IndicesDeVias(vias(), IndicePuntosKm(hitosMa15())) else null
        }

    private fun guardador() =
        GuardadorTramosVia(
            base.tramosDao(),
            base.recorridosDao(),
            fuente,
            BuscadorFalso("Manacor"),
            Dispatchers.Unconfined,
            { "via-${++ids}" },
            { 1_000L },
        )

    private fun migrador(guardador: GuardadorTramosVia = guardador()) =
        MigradorTramosVia(
            base.recorridosDao(),
            base.tramosDao(),
            base.subtramosDao(),
            guardador,
            fuente,
            Dispatchers.Unconfined,
        )

    @Before
    fun preparar() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        base = Room.inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun cerrar() = base.close()

    private suspend fun recorridoConPuntos(
        puntos: List<PuntoRecorrido>,
        terminado: Boolean = true,
    ): Long {
        val dao = base.recorridosDao()
        val id =
            dao.insertar(
                RecorridoEntity(
                    inicioMs = puntos.first().tiempoMs,
                    finMs = if (terminado) puntos.last().tiempoMs else null,
                    distanciaM = 10_000.0,
                    destinoNombre = null,
                ),
            )
        dao.insertarPuntos(
            puntos.mapIndexed { i, p -> RecorridoPuntoEntity(id, i, p.lat, p.lon, p.tiempoMs, p.velocidadMs) },
        )
        return id
    }

    private suspend fun tramosVia() = base.tramosDao().tramosPorTipo(TIPO_VIA)

    /** Deja un tramo-viaje con una pasada del recorrido: lo que queda de haberlo cronometrado. */
    private suspend fun sembrarCronometrado(
        recorridoId: Long,
        inicioMs: Long,
    ) {
        val tramo = Tramo("viaje-$recorridoId", listOf(alEste(0.0), alEste(10_000.0)), 10_000.0)
        val marcas = Pasada(tramo.id, listOf(MarcaTiempo(0.0, 0L), MarcaTiempo(tramo.metaM, 500_000L)), 500_000L)
        base
            .tramosDao()
            .crear(
                tramo.aEntidad(null, 500L, null),
                marcas.aEntidad(OrigenPasada(inicioMs, recorridoId), tramo.cortesExactosM()),
            )
    }

    @Test
    fun `un viaje por tres carreteras se guarda como tres tramos de via con su pasada`() =
        runBlocking {
            val puntos = viaje(1_000_000L)
            val id = recorridoConPuntos(puntos)

            val resultado = guardador().trocearYMarcar(id, puntos)

            assertEquals(ResultadoTroceado.HECHO, resultado)
            val tramos = tramosVia()
            assertEquals(3, tramos.size)
            assertEquals(listOf("MA30", "MA15", "MA3220"), tramos.map { it.viaRef })
            assertTrue(tramos.all { it.sentido == "CRECIENTE" && it.recorridoOrigenId == id && it.nombre == null })
            for (t in tramos) {
                val pasadas = base.tramosDao().pasadas(t.id)
                assertEquals(1, pasadas.size)
                assertEquals(id, pasadas.single().recorridoId)
                assertTrue(pasadas.single().totalMs > 0)
            }
            assertTrue(base.recorridosDao().recorridosSinTrocear().isEmpty())
        }

    @Test
    fun `el nombre automatico lleva carretera kilometros y destino`() =
        runBlocking {
            val puntos = viaje(1_000_000L)
            guardador().trocear(recorridoConPuntos(puntos), puntos)

            val ma15 = tramosVia().single { it.viaRef == "MA15" }

            val nombre = checkNotNull(ma15.nombreAutomatico)
            assertTrue(nombre, nombre.startsWith("Ma-15 · km 20,"))
            assertTrue(nombre, nombre.endsWith(" · hacia Manacor"))
            assertEquals("Manacor", ma15.destino)
            assertNotNull(ma15.pkDesde)
            assertNotNull(ma15.pkHasta)
            // Las carreteras sin hitos llevan un PK estimado desde el extremo más cercano a Palma.
            val ma30 = tramosVia().single { it.viaRef == "MA30" }
            val nombre30 = checkNotNull(ma30.nombreAutomatico)
            assertTrue(nombre30, nombre30.startsWith("Ma-30 · km ~0,"))
            assertTrue(nombre30, nombre30.contains("→~3,"))
        }

    @Test
    fun `una segunda pasada se anade al mismo tramo de via`() =
        runBlocking {
            val g = guardador()
            val primero = viaje(1_000_000L, 20.0)
            val segundo = viaje(90_000_000L, 25.0)
            g.trocearYMarcar(recorridoConPuntos(primero), primero)
            val id2 = recorridoConPuntos(segundo)

            g.trocearYMarcar(id2, segundo)

            val tramos = tramosVia()
            assertEquals(3, tramos.size)
            for (t in tramos) {
                val pasadas = base.tramosDao().pasadas(t.id)
                assertEquals(2, pasadas.size)
                val nueva = pasadas.first { it.recorridoId == id2 }
                assertTrue(nueva.totalMs < pasadas.first { it.recorridoId != id2 }.totalMs)
            }
        }

    @Test
    fun `trocear dos veces el mismo recorrido no duplica pasadas`() =
        runBlocking {
            val g = guardador()
            val puntos = viaje(1_000_000L)
            val id = recorridoConPuntos(puntos)

            g.trocear(id, puntos)
            g.trocear(id, puntos)

            assertEquals(3, tramosVia().size)
            assertTrue(tramosVia().all { base.tramosDao().pasadas(it.id).size == 1 })
        }

    @Test
    fun `sin vias instaladas no se trocea ni se marca`() =
        runBlocking {
            conIndices = false
            val puntos = viaje(1_000_000L)
            val id = recorridoConPuntos(puntos)

            val resultado = guardador().trocearYMarcar(id, puntos)

            assertEquals(ResultadoTroceado.SIN_DATOS, resultado)
            assertTrue(tramosVia().isEmpty())
            assertEquals(listOf(id), base.recorridosDao().recorridosSinTrocear().map { it.id })
        }

    /** Un tramo-viaje con su pasada y sus subtramos, como lo dejaba la v20. */
    private suspend fun sembrarVieja(recorridoId: Long) {
        val tramo = Tramo("viaje-viejo", listOf(alEste(0.0), alEste(10_000.0)), 10_000.0)
        val marcas = Pasada("viaje-viejo", listOf(MarcaTiempo(0.0, 0L), MarcaTiempo(tramo.metaM, 500_000L)), 500_000L)
        base
            .tramosDao()
            .crear(
                tramo.aEntidad("Mi viaje", 500L, "Ma-30 / Ma-15"),
                marcas.aEntidad(OrigenPasada(1_000_000L, recorridoId), tramo.cortesExactosM()),
            )
        for ((desde, hasta) in listOf(3_500.0 to 6_500.0, 1_000.0 to 5_000.0)) {
            base.subtramosDao().insertar(
                SubtramoEntity(
                    recorridoId = recorridoId,
                    tramoRefId = "viaje-viejo",
                    desdeM = desde,
                    hastaM = hasta,
                    longitudM = hasta - desde,
                    inicio = 1_000_000L,
                    tiempoMs = 100_000L,
                    mejorMs = null,
                    latDesde = BASE.lat,
                    lonDesde = BASE.lon,
                    latHasta = BASE.lat,
                    lonHasta = BASE.lon,
                ),
            )
        }
    }

    @Test
    fun `la migracion trocea lo guardado, es idempotente y no toca los tramos-viaje`() =
        runBlocking {
            val a = viaje(1_000_000L, 20.0)
            val b = viaje(90_000_000L, 25.0)
            val ida = recorridoConPuntos(a)
            val idb = recorridoConPuntos(b)
            recorridoConPuntos(viaje(200_000_000L), terminado = false)
            sembrarVieja(ida)
            sembrarCronometrado(idb, 90_000_000L)
            val viajesAntes = base.tramosDao().tramosPorTipo(TIPO_VIAJE)
            val pasadasViajeAntes = base.tramosDao().pasadas("viaje-viejo")

            val primera = migrador().migrar()
            val vias1 = tramosVia().map { it.id to base.tramosDao().pasadas(it.id).size }
            val segunda = migrador().migrar()
            val vias2 = tramosVia().map { it.id to base.tramosDao().pasadas(it.id).size }

            assertEquals(2, primera.recorridos)
            assertEquals(false, primera.pendiente)
            assertEquals(0, segunda.recorridos)
            assertEquals(vias1, vias2)
            assertEquals(3, vias2.size)
            assertTrue(vias2.all { it.second == 2 })
            // Los tramos-viaje y sus pasadas siguen intactos.
            assertEquals(viajesAntes, base.tramosDao().tramosPorTipo(TIPO_VIAJE))
            assertEquals(pasadasViajeAntes, base.tramosDao().pasadas("viaje-viejo"))
            // El recorrido sin terminar no se trocea.
            assertTrue(base.recorridosDao().recorridosSinTrocear().isEmpty())
            val origenes = tramosVia().flatMap { base.tramosDao().pasadas(it.id) }.mapNotNull { it.recorridoId }
            assertEquals(setOf(ida, idb), origenes.toSet())
        }

    @Test
    fun `la migracion situa los subtramos que caen en una sola carretera`() =
        runBlocking {
            val a = viaje(1_000_000L)
            sembrarVieja(recorridoConPuntos(a))

            val resumen = migrador().migrar()

            val porTramo = base.subtramosDao().observarDeTramo("viaje-viejo").first()
            val enMa15 = porTramo.single { it.desdeM == 3_500.0 }
            assertEquals("MA15", enMa15.viaRef)
            assertEquals("CRECIENTE", enMa15.sentido)
            assertNotNull(enMa15.pkDesde)
            // El que cruza dos carreteras se queda sin situar.
            assertNull(porTramo.single { it.desdeM == 1_000.0 }.viaRef)
            assertEquals(1, resumen.subtramos)
            // Repetirlo no cambia nada: el ya situado no se toca y el otro sigue sin carretera.
            assertEquals(0, migrador().migrar().subtramos)
            val despues = base.subtramosDao().observarDeTramo("viaje-viejo").first()
            assertEquals("MA15", despues.single { it.desdeM == 3_500.0 }.viaRef)
        }

    @Test
    fun `sin vias la migracion queda pendiente y no marca nada`() =
        runBlocking {
            conIndices = false
            val id = recorridoConPuntos(viaje(1_000_000L))
            sembrarCronometrado(id, 1_000_000L)

            val resumen = migrador().migrar()

            assertTrue(resumen.pendiente)
            assertEquals(0, resumen.recorridos)
            assertEquals(listOf(id), base.recorridosDao().recorridosSinTrocear().map { it.id })
            assertTrue(tramosVia().isEmpty())
        }

    @Test
    fun `un fallo del motor en un recorrido no se propaga, lo marca y no impide trocear los demas`() =
        runBlocking {
            val a = viaje(1_000_000L)
            val b = viaje(90_000_000L)
            val ida = recorridoConPuntos(a)
            val idb = recorridoConPuntos(b)
            sembrarCronometrado(ida, 1_000_000L)
            sembrarCronometrado(idb, 90_000_000L)
            fallosDelMotor = 1

            val resumen = migrador().migrar()

            // El primero (ida) falla con una IllegalArgumentException del motor: se marca y no se reintenta.
            assertTrue(base.recorridosDao().recorridosSinTrocear().isEmpty())
            assertEquals(1, resumen.recorridos)
            assertTrue(tramosVia().isNotEmpty())
            val origenes = tramosVia().flatMap { base.tramosDao().pasadas(it.id) }.mapNotNull { it.recorridoId }
            assertEquals(setOf(idb), origenes.toSet())
        }

    @Test
    fun `la migracion no trocea los recorridos que no se cronometraron y los marca`() =
        runBlocking {
            val cronometrado = recorridoConPuntos(viaje(1_000_000L))
            val sinCrono = recorridoConPuntos(viaje(90_000_000L))
            sembrarCronometrado(cronometrado, 1_000_000L)

            val resumen = migrador().migrar()

            assertEquals(1, resumen.recorridos)
            assertTrue(base.recorridosDao().recorridosSinTrocear().isEmpty())
            val origenes = tramosVia().flatMap { base.tramosDao().pasadas(it.id) }.mapNotNull { it.recorridoId }
            assertEquals(setOf(cronometrado), origenes.toSet())
            assertTrue(sinCrono !in origenes)
        }

    @Test
    fun `una segunda pasada mas larga no duplica el tramo y suma pasada al existente`() =
        runBlocking {
            val g = guardador()
            val corto = viaje(1_000_000L, 20.0, 0.0, 6_000.0)
            val largo = viaje(90_000_000L, 25.0)
            g.trocearYMarcar(recorridoConPuntos(corto), corto)
            val id2 = recorridoConPuntos(largo)

            g.trocearYMarcar(id2, largo)

            val tramos = tramosVia()
            assertEquals(listOf("MA30", "MA15", "MA3220"), tramos.map { it.viaRef })
            val ma15 = tramos.single { it.viaRef == "MA15" }
            assertEquals(2, base.tramosDao().pasadas(ma15.id).size)
            assertEquals(2, base.tramosDao().pasadas(tramos.single { it.viaRef == "MA30" }.id).size)
        }

    @Test
    fun `un trozo que solo cubre medio tramo no crea otro ni se anade`() =
        runBlocking {
            val g = guardador()
            val completo = viaje(1_000_000L)
            g.trocearYMarcar(recorridoConPuntos(completo), completo)
            val medio = viaje(90_000_000L, 20.0, 4_000.0, 6_000.0)

            g.trocearYMarcar(recorridoConPuntos(medio), medio)

            val tramos = tramosVia()
            assertEquals(3, tramos.size)
            assertTrue(tramos.all { base.tramosDao().pasadas(it.id).size == 1 })
        }

    @Test
    fun `la pasada llega a todos los tramos de via solapados que se cubren entero`() =
        runBlocking {
            val g = guardador()
            val primero = viaje(1_000_000L)
            g.trocearYMarcar(recorridoConPuntos(primero), primero)
            // Un duplicado heredado: otro tramo de vía de la misma carretera y sentido, con su pasada.
            val ma15 = tramosVia().single { it.viaRef == "MA15" }
            val pasada = base.tramosDao().pasadas(ma15.id).single()
            base.tramosDao().crear(ma15.copy(id = "duplicado"), pasada.copy(id = 0, tramoId = "duplicado"))
            val segundo = viaje(90_000_000L, 25.0)
            val id2 = recorridoConPuntos(segundo)

            g.trocearYMarcar(id2, segundo)

            for (id in listOf(ma15.id, "duplicado")) {
                assertTrue(id, base.tramosDao().pasadas(id).any { it.recorridoId == id2 })
            }
        }

    @Test
    fun `dos pasadas por el mismo trozo en un viaje guardan las dos`() =
        runBlocking {
            val g = guardador()
            // Ida por las tres carreteras, vuelta al principio sin recorrerlas (salto) y segunda ida más tarde.
            val primera = viaje(1_000_000L, 20.0)
            val segunda = viaje(5_000_000L, 25.0)
            val puntos = primera + segunda
            val id = recorridoConPuntos(puntos)

            g.trocearYMarcar(id, puntos)

            val crecientes = tramosVia().filter { it.sentido == "CRECIENTE" }
            assertEquals(listOf("MA30", "MA15", "MA3220"), crecientes.map { it.viaRef })
            for (t in crecientes) {
                val pasadas = base.tramosDao().pasadas(t.id)
                assertEquals(t.viaRef, 2, pasadas.size)
                assertTrue(pasadas.all { it.recorridoId == id })
                assertEquals(2, pasadas.map { it.puntosDesdeMs }.toSet().size)
            }
        }

    @Test
    fun `una pasada ya guardada por la conduccion en vivo no se repite al trocear`() =
        runBlocking {
            val g = guardador()
            val puntos = viaje(1_000_000L)
            val id = recorridoConPuntos(puntos)
            g.trocear(id, puntos)
            // La misma pasada con un intervalo que solapa casi todo (la detección en vivo no empieza justo ahí).
            val ma15 = tramosVia().single { it.viaRef == "MA15" }
            val vivo = base.tramosDao().pasadas(ma15.id).single()
            val desde = checkNotNull(vivo.puntosDesdeMs) + 1_000
            val hasta = checkNotNull(vivo.puntosHastaMs) + 1_000
            base.tramosDao().insertarPasada(vivo.copy(id = 0, puntosDesdeMs = desde, puntosHastaMs = hasta))

            g.trocear(id, puntos)

            assertEquals(2, base.tramosDao().pasadas(ma15.id).size)
        }

    @Test
    fun `un trozo que no cruza la salida y la meta del tramo guardado se registra sin coordenadas`() =
        runBlocking {
            val g = guardador()
            val completo = viaje(1_000_000L)
            g.trocearYMarcar(recorridoConPuntos(completo), completo)
            ShadowLog.clear()
            val medio = viaje(90_000_000L, 20.0, 4_000.0, 6_000.0)

            g.trocearYMarcar(recorridoConPuntos(medio), medio)

            val avisos = ShadowLog.getLogsForTag("GuardadorTramosVia").filter { it.type == Log.WARN }
            assertTrue(avisos.toString(), avisos.isNotEmpty())
            assertTrue(avisos.none { Regex("""\d+\.\d{3,}""").containsMatchIn(it.msg) })
        }

    @Test
    fun `los subtramos que no se pueden situar se marcan y no se reintentan en cada arranque`() =
        runBlocking {
            sembrarVieja(recorridoConPuntos(viaje(1_000_000L)))

            val primera = migrador().migrar()

            assertEquals(1, primera.subtramos)
            assertTrue("el no situable ya está intentado", base.subtramosDao().sinVia().isEmpty())
            val subtramos = base.subtramosDao().observarDeTramo("viaje-viejo").first()
            val intentado = subtramos.single { it.desdeM == 1_000.0 }
            assertNull(intentado.viaRef)
            assertEquals(0, migrador().migrar().subtramos)
        }

    /** El mismo viaje que [viaje] pero hacia el oeste (de [hastaM] a [desdeM]). */
    private fun viajeAlOeste(
        inicioMs: Long,
        metrosPorSegundo: Double = 20.0,
    ): List<PuntoRecorrido> =
        viaje(inicioMs, metrosPorSegundo).let { ida ->
            ida.reversed().mapIndexed { i, p -> p.copy(tiempoMs = inicioMs + i * 1_000L) }
        }

    @Test
    fun `un trozo con el eje invertido se anade al tramo existente con su sentido`() =
        runBlocking {
            val g = guardador()
            val primero = viaje(1_000_000L)
            g.trocearYMarcar(recorridoConPuntos(primero), primero)
            // El eje de OSM cambió: lo guardado como CRECIENTE ahora sería DECRECIENTE; se simula guardándolo así.
            base.openHelper.writableDatabase.execSQL("UPDATE tramo SET sentido = 'DECRECIENTE' WHERE tipo = 'VIA'")
            val segundo = viaje(90_000_000L, 25.0)
            val id2 = recorridoConPuntos(segundo)

            g.trocearYMarcar(id2, segundo)

            val tramos = tramosVia()
            assertEquals(3, tramos.size)
            assertTrue(tramos.all { it.sentido == "DECRECIENTE" })
            for (t in tramos) {
                assertTrue(t.viaRef, base.tramosDao().pasadas(t.id).any { it.recorridoId == id2 })
            }
        }

    @Test
    fun `el sentido contrario real crea un tramo nuevo con el sentido opuesto`() =
        runBlocking {
            val g = guardador()
            val ida = viaje(1_000_000L)
            g.trocearYMarcar(recorridoConPuntos(ida), ida)
            val vuelta = viajeAlOeste(90_000_000L)
            val id2 = recorridoConPuntos(vuelta)

            g.trocearYMarcar(id2, vuelta)

            val ma15 = tramosVia().filter { it.viaRef == "MA15" }
            assertEquals(2, ma15.size)
            assertEquals(setOf("CRECIENTE", "DECRECIENTE"), ma15.map { it.sentido }.toSet())
            val contrario = ma15.single { it.sentido == "DECRECIENTE" }
            assertEquals(listOf(id2), base.tramosDao().pasadas(contrario.id).map { it.recorridoId })
            assertEquals(1, base.tramosDao().pasadas(ma15.single { it.sentido == "CRECIENTE" }.id).size)
        }
}
