package app.trombito.rallycopilot.data.tramos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.data.db.RecorridoEntity
import app.trombito.rallycopilot.data.db.RecorridoPuntoEntity
import app.trombito.rallycopilot.data.detalle.FalsaPrecarga
import app.trombito.rallycopilot.domain.tramos.EstadisticasPerfil
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.NombreAutomatico
import app.trombito.rallycopilot.domain.tramos.OrigenPasada
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.MarcaTiempo
import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.tramos.Tramo
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

@RunWith(RobolectricTestRunner::class)
class RepositorioTramosRoomTest {
    private lateinit var base: RallyDatabase
    private lateinit var repositorio: RepositorioTramosRoom

    @Before
    fun preparar() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        base = Room.inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java).allowMainThreadQueries().build()
        repositorio = RepositorioTramosRoom(base.tramosDao(), Dispatchers.Unconfined)
    }

    @After
    fun cerrar() = base.close()

    @Test
    fun `crear un tramo con o sin pasadas encola el detalle de su corredor`() =
        runBlocking {
            val precarga = FalsaPrecarga()
            val conAviso = RepositorioTramosRoom(base.tramosDao(), Dispatchers.Unconfined, precarga)
            val t = tramo("a")

            conAviso.crear(t, null, null, 5_000L, pasada(t, 100.0), OrigenPasada(4_000L))
            conAviso.crearSinPasadas(tramo("b"), null, null, 6_000L)

            assertEquals(2, precarga.tramos)
        }

    /** Tramo de unos 1,9 km hacia el norte con cortes por defecto (3 sectores y 1 micro). */
    private fun tramo(id: String = "t1"): Tramo {
        val traza = List(21) { PuntoGeo(39.6 + it * 0.00085, 2.9) }
        return Tramo(id, traza, traza.zipWithNext { a, b -> a.distanciaA(b) }.sum())
    }

    /** Pasada a ritmo constante de [msPorM] ms por metro: una marca cada 5 m más las de los cortes y la meta. */
    private fun pasada(
        tramo: Tramo,
        msPorM: Double,
    ): Pasada {
        val cadaCinco = (0 until (tramo.metaM / 5).toInt()).map { it * 5.0 }
        val distancias = (cadaCinco + tramo.sectores + tramo.microsectores + tramo.metaM).distinct().sorted()
        val marcas = distancias.map { MarcaTiempo(it, Math.round(it * msPorM)) }
        return Pasada(tramo.id, marcas, marcas.last().tMs)
    }

    @Test
    fun `los cortes guardados vuelven y uno invalido no se guarda`() =
        runBlocking {
            val t = tramo()
            repositorio.crear(t, null, null, 5_000L, pasada(t, 100.0), OrigenPasada(4_000L))

            repositorio.guardarCortes("t1", listOf(300.0, 900.0), listOf(500.0, 1_000.0, 1_500.0))

            val guardado = checkNotNull(repositorio.tramo("t1"))
            assertEquals(listOf(300.0, 900.0), guardado.sectores)
            assertEquals(listOf(500.0, 1_000.0, 1_500.0), guardado.microsectores)
            repositorio.guardarCortes("t1", listOf(300.0), emptyList())
            assertEquals(emptyList<Double>(), repositorio.tramo("t1")?.microsectores)
            val fallo = runCatching { repositorio.guardarCortes("t1", listOf(900.0, 300.0), emptyList()) }
            assertTrue(fallo.exceptionOrNull() is IllegalArgumentException)
            assertEquals(listOf(300.0), repositorio.tramo("t1")?.sectores)
            assertTrue(runCatching { repositorio.guardarCortes("no", emptyList(), emptyList()) }.isFailure)
        }

    @Test
    fun `un tramo creado vuelve igual con su primera pasada como mejor`() =
        runBlocking {
            val t = tramo()
            val primera = pasada(t, 100.0)

            repositorio.crear(t, "Sa Calobra", null, 5_000L, primera, OrigenPasada(4_000L))

            val resumen = repositorio.observarResumenes().first().single()
            assertEquals(t.id, resumen.id)
            assertEquals("Sa Calobra", resumen.nombre)
            assertEquals(1, resumen.pasadas)
            assertEquals(primera.totalMs, resumen.mejorTotalMs)
            assertEquals(t.longitudM, resumen.longitudM, 0.0)
            val guardado = checkNotNull(repositorio.tramo(t.id))
            assertEquals(t.longitudM, guardado.longitudM, 0.0)
            assertEquals(t.sectores, guardado.sectores)
            assertEquals(t.microsectores, guardado.microsectores)
            val traza = guardado.traza
            assertEquals(t.traza.size, traza.size)
            assertEquals(t.traza.first().lat, traza.first().lat, 1e-6)
            assertEquals(t.traza.last().lon, traza.last().lon, 1e-6)
        }

    @Test
    fun `la mejor pasada es la de menor tiempo y se guardan marcas compactas que interpolan igual`() =
        runBlocking {
            val t = tramo()
            repositorio.crear(t, null, null, 1L, pasada(t, 100.0), OrigenPasada(10L))
            val lenta = pasada(t, 120.0)
            val rapida = pasada(t, 80.0)
            val idLenta = repositorio.guardarPasada(lenta, OrigenPasada(20L))
            val idRapida = repositorio.guardarPasada(rapida, OrigenPasada(30L))
            assertTrue(idRapida > idLenta)

            val mejor = checkNotNull(repositorio.mejorPasada(t.id))

            assertEquals(rapida.totalMs, mejor.totalMs)
            assertTrue("marcas ${mejor.tiempos.size}", mejor.tiempos.size < rapida.tiempos.size)
            for (d in listOf(0.0, 333.0, t.sectores[0], 1234.5, t.metaM)) {
                assertEquals(checkNotNull(rapida.tiempoEn(d)), checkNotNull(mejor.tiempoEn(d)), 1.0)
            }
            val resumen = repositorio.observarResumenes().first().single()
            assertEquals(3, resumen.pasadas)
            val observada = repositorio.observarMejorPasada(t.id).first()
            assertEquals(rapida.totalMs, observada?.totalMs)
        }

    @Test
    fun `a igualdad de tiempo gana la primera y cronometrables trae cada tramo con su mejor`() =
        runBlocking {
            val a = tramo("a")
            val b = tramo("b")
            repositorio.crear(a, null, null, 1L, pasada(a, 100.0), OrigenPasada(10L))
            repositorio.crear(b, null, null, 2L, pasada(b, 90.0), OrigenPasada(10L))
            repositorio.guardarPasada(pasada(a, 100.0), OrigenPasada(99L))
            repositorio.guardarPasada(pasada(a, 95.0), OrigenPasada(100L))

            val lista = repositorio.cronometrables().associateBy { it.tramo.id }

            assertEquals(setOf("a", "b"), lista.keys)
            assertEquals(pasada(a, 95.0).totalMs, lista.getValue("a").mejor?.totalMs)
            assertEquals(pasada(b, 90.0).totalMs, lista.getValue("b").mejor?.totalMs)
            // El de uso más reciente primero en el flujo de la lista.
            val ids = repositorio.observarResumenes().first().map { it.id }
            assertEquals(listOf("a", "b"), ids)
        }

    @Test
    fun `borrar el tramo borra sus pasadas y guardar en un tramo inexistente falla`() =
        runBlocking {
            val t = tramo()
            repositorio.crear(t, null, null, 1L, pasada(t, 100.0), OrigenPasada(10L))
            repositorio.borrar(t.id)

            assertTrue(repositorio.observarResumenes().first().isEmpty())
            assertNull(repositorio.mejorPasada(t.id))
            val bd = base.openHelper.writableDatabase
            assertTrue(bd.query("SELECT * FROM pasada").use { it.count == 0 })
            val fallo = runCatching { repositorio.guardarPasada(pasada(t, 100.0), OrigenPasada(5L)) }.exceptionOrNull()
            assertNotNull(fallo)
            assertTrue(fallo is IllegalStateException)
        }

    @Test
    fun `una fila de tramo danada se ignora sin romper el resto`() =
        runBlocking {
            val t = tramo()
            repositorio.crear(t, null, null, 1L, pasada(t, 100.0), OrigenPasada(10L))
            val bd = base.openHelper.writableDatabase
            bd.execSQL(
                "INSERT INTO tramo (id, nombre, traza, longitudM, sectores, microsectores, creadoEn) " +
                    "VALUES ('malo', NULL, '!!', 1000.0, 'x', '', 2)",
            )

            assertEquals(listOf(t.id), repositorio.cronometrables().map { it.tramo.id })
        }

    @Test
    fun `mis tramos va por ultimo uso, filtra repetidos y favoritos y muestra el nombre efectivo`() =
        runBlocking {
            val a = tramo("a")
            val b = tramo("b")
            val c = tramo("c")
            repositorio.crear(a, null, "Ma-2141 · Escorca", 1L, pasada(a, 100.0), OrigenPasada(100L))
            repositorio.crear(b, "Mi favorita", "Ma-10", 2L, pasada(b, 90.0), OrigenPasada(300L))
            repositorio.crear(c, null, null, 3L, pasada(c, 90.0), OrigenPasada(200L))
            repositorio.guardarPasada(pasada(c, 85.0), OrigenPasada(250L))

            val todos = repositorio.observarResumenes(FiltroTramos.TODOS).first()
            assertEquals(listOf("b", "c", "a"), todos.map { it.id })
            assertEquals(listOf(300L, 250L, 100L), todos.map { it.ultimoUsoMs })
            assertEquals(listOf("Mi favorita", "Tramo sin nombre", "Ma-2141 · Escorca"), todos.map { it.nombre })
            assertEquals("Mi favorita", todos[0].nombrePropio)
            assertNull(todos[2].nombrePropio)
            assertEquals(listOf(1, 2, 1), todos.map { it.pasadas })
            assertEquals(pasada(c, 85.0).totalMs, todos[1].mejorTotalMs)
            assertEquals(a.longitudM, todos[2].longitudM, 0.0)

            assertEquals(listOf("c"), repositorio.observarResumenes(FiltroTramos.REPETIDOS).first().map { it.id })
            assertTrue(repositorio.observarResumenes(FiltroTramos.FAVORITOS).first().isEmpty())

            repositorio.marcarFavorito("a", true)
            val favoritos = repositorio.observarResumenes(FiltroTramos.FAVORITOS).first()
            assertEquals(listOf("a"), favoritos.map { it.id })
            assertTrue(favoritos.single().favorito)
            repositorio.marcarFavorito("a", false)
            assertTrue(repositorio.observarResumenes(FiltroTramos.FAVORITOS).first().isEmpty())

            // Una pasada nueva lo lleva arriba; una más antigua no baja su último uso.
            repositorio.guardarPasada(pasada(a, 99.0), OrigenPasada(400L))
            repositorio.guardarPasada(pasada(a, 98.0), OrigenPasada(50L))
            val tras = repositorio.observarResumenes().first()
            assertEquals(listOf("a", "b", "c"), tras.map { it.id })
            assertEquals(400L, tras.first().ultimoUsoMs)
            assertEquals(listOf("a", "c"), repositorio.observarResumenes(FiltroTramos.REPETIDOS).first().map { it.id })
        }

    @Test
    fun `el nombre automatico con carreteras separa titulo y subtitulo y el propio no los pisa`() =
        runBlocking {
            val a = tramo("a")
            val b = tramo("b")
            repositorio.crearSinPasadas(a, null, NombreAutomatico.componer("Palma → Montuïri", "Ma-13 / Ma-15"), 1L)
            repositorio.crearSinPasadas(b, "Mi tramo", "Ma-13 · Santa María", 2L)

            val resumenes = repositorio.observarResumenes(FiltroTramos.TODOS).first().associateBy { it.id }
            assertEquals("Palma → Montuïri", resumenes.getValue("a").nombre)
            assertEquals("Ma-13 / Ma-15", resumenes.getValue("a").subtitulo)
            assertNull(resumenes.getValue("a").nombrePropio)
            // Un nombre antiguo («Ma-13 · Santa María») no tiene subtítulo y uno propio manda en el título.
            assertEquals("Mi tramo", resumenes.getValue("b").nombre)
            assertNull(resumenes.getValue("b").subtitulo)
        }

    @Test
    fun `renombrar cambia el nombre, vacio vuelve al automatico y un tramo inexistente falla`() =
        runBlocking {
            val t = tramo()
            repositorio.crear(t, null, "Ma-2141", 1L, pasada(t, 100.0), OrigenPasada(10L))

            repositorio.renombrar(t.id, "  La curva de Joan  ")
            assertEquals(
                "La curva de Joan",
                repositorio
                    .observarResumenes()
                    .first()
                    .single()
                    .nombre,
            )
            assertEquals("La curva de Joan", base.tramosDao().tramo(t.id)?.nombre)

            repositorio.renombrar(t.id, "   ")
            val vuelto = repositorio.observarResumenes().first().single()
            assertEquals("Ma-2141", vuelto.nombre)
            assertNull(vuelto.nombrePropio)

            val alRenombrar = runCatching { repositorio.renombrar("no-existe", "x") }.exceptionOrNull()
            assertTrue(alRenombrar is IllegalStateException)
            val alMarcar = runCatching { repositorio.marcarFavorito("no-existe", true) }.exceptionOrNull()
            assertTrue(alMarcar is IllegalStateException)
        }

    @Test
    fun `borrar quita el tramo de la lista y de los filtros con sus pasadas`() =
        runBlocking {
            val t = tramo()
            repositorio.crear(t, null, null, 1L, pasada(t, 100.0), OrigenPasada(10L))
            repositorio.guardarPasada(pasada(t, 90.0), OrigenPasada(20L))
            repositorio.marcarFavorito(t.id, true)

            repositorio.borrar(t.id)

            for (filtro in FiltroTramos.entries) assertTrue(repositorio.observarResumenes(filtro).first().isEmpty())
            assertTrue(repositorio.pasadasDetalladas(t.id).isEmpty())
            assertNull(repositorio.tramo(t.id))
        }

    @Test
    fun `las pasadas traen sus puntos recortados al intervalo y sin recorrido salen sin puntos`() =
        runBlocking {
            val t = tramo()
            val recorrido =
                base.recorridosDao().insertar(
                    RecorridoEntity(inicioMs = 500L, finMs = 10_000L, distanciaM = 2_000.0, destinoNombre = null),
                )
            base.recorridosDao().anadirPuntos(
                recorrido,
                (1..10).map { RecorridoPuntoEntity(recorrido, 0, 39.6 + it * 0.001, 2.9, it * 1_000L, it * 2.0) },
            )
            val origen = OrigenPasada(1_700_000_003_000L, recorrido, puntosDesdeMs = 3_000L, puntosHastaMs = 7_000L)
            repositorio.crear(t, null, null, 1L, pasada(t, 100.0), origen)
            repositorio.guardarPasada(pasada(t, 90.0), OrigenPasada(inicioMs = 1_700_000_100_000L))

            val detalladas = repositorio.pasadasDetalladas(t.id)

            assertEquals(2, detalladas.size)
            // La más reciente primero, y la mejor es la de 90 ms/m.
            val sinRecorrido = detalladas[0]
            val conRecorrido = detalladas[1]
            assertEquals(1_700_000_100_000L, sinRecorrido.inicioMs)
            assertTrue(sinRecorrido.puntos.isEmpty())
            assertTrue(sinRecorrido.esMejor)
            assertEquals(pasada(t, 90.0).totalMs, sinRecorrido.totalMs)
            assertEquals(listOf(3_000L, 4_000L, 5_000L, 6_000L, 7_000L), conRecorrido.puntos.map { it.tiempoMs })
            assertEquals(39.603, conRecorrido.puntos.first().lat, 1e-9)
            assertEquals(6.0, checkNotNull(conRecorrido.puntos.first().velocidadMs), 0.0)
            assertTrue(!conRecorrido.esMejor)
            assertEquals(sinRecorrido.id, repositorio.mejorPasadaDetallada(t.id)?.id)
            assertEquals(t.id, repositorio.tramo(t.id)?.id)

            // Si se borra el recorrido, la pasada se queda sin puntos pero no desaparece.
            base.recorridosDao().borrar(recorrido)
            val tras = repositorio.pasadasDetalladas(t.id)
            assertEquals(2, tras.size)
            assertTrue(tras.all { it.puntos.isEmpty() })
        }

    @Test
    fun `el perfil cuenta los recorridos terminados, sus fechas y los tramos con sus pasadas`() =
        runBlocking {
            assertEquals(EstadisticasPerfil(0, 0.0, null, null, 0, 0), repositorio.observarPerfil().first())
            val recorridos = base.recorridosDao()
            recorridos.insertar(
                RecorridoEntity(inicioMs = 1_000L, finMs = 2_000L, distanciaM = 1_500.0, destinoNombre = null),
            )
            recorridos.insertar(
                RecorridoEntity(inicioMs = 5_000L, finMs = 6_000L, distanciaM = 2_500.0, destinoNombre = "x"),
            )
            recorridos.insertar(
                RecorridoEntity(inicioMs = 9_000L, finMs = null, distanciaM = 0.0, destinoNombre = null),
            )
            val t = tramo()
            repositorio.crear(t, null, null, 1L, pasada(t, 100.0), OrigenPasada(10L))
            repositorio.guardarPasada(pasada(t, 90.0), OrigenPasada(20L))

            assertEquals(EstadisticasPerfil(2, 4_000.0, 1_000L, 5_000L, 1, 2), repositorio.observarPerfil().first())
        }
}
