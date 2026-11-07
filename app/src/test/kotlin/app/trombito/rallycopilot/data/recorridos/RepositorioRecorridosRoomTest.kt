package app.trombito.rallycopilot.data.recorridos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.data.db.CalibracionEntity
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.data.db.RecorridoEntity
import app.trombito.rallycopilot.data.db.RecorridoPuntoEntity
import app.trombito.rallycopilot.data.db.RecorridosDao
import app.trombito.rallycopilot.data.notas.CalibradorRecorridos
import app.trombito.rallycopilot.data.notas.p
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.MuestraVelocidad
import app.trombito.rallycopilot.motor.notas.PerfilVelocidad
import app.trombito.rallycopilot.motor.notas.TablaGrados
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
class RepositorioRecorridosRoomTest {
    private lateinit var base: RallyDatabase
    private lateinit var repositorio: RepositorioRecorridosRoom
    private var ahora = 1_000L

    @Before
    fun preparar() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        base = Room.inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java).allowMainThreadQueries().build()
        repositorio = RepositorioRecorridosRoom(base.recorridosDao(), Dispatchers.Unconfined) { ahora }
    }

    @After
    fun cerrar() = base.close()

    /** Puntos hacia el norte cada 0,001 grados (unos 111 m) desde [desde], uno por segundo. */
    private fun ruta(
        cuantos: Int,
        desde: Int = 0,
    ) = List(cuantos) { i ->
        PuntoRecorrido(
            lat = 39.5 + (desde + i) * 0.001,
            lon = 2.9,
            tiempoMs = 10_000L + (desde + i) * 1_000L,
            velocidadMs = 30.0,
        )
    }

    @Test
    fun `un recorrido de mas de 1 km se conserva con sus puntos`() =
        runBlocking {
            val id = repositorio.empezar("Sóller")
            repositorio.anadirPuntos(id, ruta(11)) // 10 tramos, 1,1 km

            val recorrido = repositorio.terminar(id)

            assertNotNull(recorrido)
            assertEquals(1_000L, recorrido?.inicioMs)
            assertEquals(20_000L, recorrido?.finMs)
            assertEquals("Sóller", recorrido?.destinoNombre)
            assertEquals(1_112.0, recorrido?.distanciaM ?: 0.0, 5.0)
            assertEquals(listOf(recorrido), repositorio.listar().first())
            assertEquals(11, repositorio.puntos(id).size)
        }

    @Test
    fun `la distancia guardada ignora los puntos de deriva a menos de 5 m`() =
        runBlocking {
            val id = repositorio.empezar()
            val tramo = ruta(11)
            // Entre cada par de puntos, una deriva a ~2 m del anterior.
            val conDeriva = tramo.flatMap { listOf(it, it.copy(lat = it.lat + 0.00002, tiempoMs = it.tiempoMs + 500)) }
            repositorio.anadirPuntos(id, conDeriva)

            val recorrido = repositorio.terminar(id)

            assertEquals(1_112.0, recorrido?.distanciaM ?: 0.0, 5.0)
        }

    @Test
    fun `un recorrido de menos de 1 km se descarta con sus puntos`() =
        runBlocking {
            val id = repositorio.empezar()
            repositorio.anadirPuntos(id, ruta(5)) // 444 m

            assertNull(repositorio.terminar(id))

            assertTrue(repositorio.listar().first().isEmpty())
            assertTrue(repositorio.puntos(id).isEmpty())
            assertNull(base.recorridosDao().recorrido(id))
        }

    @Test
    fun `un recorrido sin puntos se descarta`() =
        runBlocking {
            assertNull(repositorio.terminar(repositorio.empezar()))
        }

    @Test
    fun `los puntos se añaden a continuacion y conservan el orden`() =
        runBlocking {
            val id = repositorio.empezar()
            repositorio.anadirPuntos(id, ruta(3))
            repositorio.anadirPuntos(id, ruta(3, desde = 3))
            repositorio.anadirPuntos(id, emptyList())

            assertEquals((0 until 6).toList(), base.recorridosDao().puntos(id).map { it.orden })
            assertEquals(ruta(6), repositorio.puntos(id))
        }

    @Test
    fun `listar solo devuelve los terminados, el mas reciente primero`() =
        runBlocking {
            ahora = 1_000
            val primero = repositorio.empezar()
            repositorio.anadirPuntos(primero, ruta(12))
            ahora = 5_000
            val segundo = repositorio.empezar()
            repositorio.anadirPuntos(segundo, ruta(12))
            ahora = 9_000
            repositorio.empezar() // en curso

            repositorio.terminar(primero)
            repositorio.terminar(segundo)

            assertEquals(listOf(segundo, primero), repositorio.listar().first().map { it.id })
        }

    @Test
    fun `borrar un recorrido borra sus puntos en cascada y respeta los demas`() =
        runBlocking {
            val a = repositorio.empezar()
            val b = repositorio.empezar()
            repositorio.anadirPuntos(a, ruta(12))
            repositorio.anadirPuntos(b, ruta(12))

            repositorio.borrar(a)

            assertTrue(base.recorridosDao().puntos(a).isEmpty())
            assertEquals(12, base.recorridosDao().puntos(b).size)
        }

    /** Carretera que serpentea (media onda cada ~200 m, radios de ~130 m) con [medias] curvas, a 15 m/s. */
    private fun serpenteo(medias: Int): List<PuntoRecorrido> =
        (0..medias * 20).map { i ->
            val punto = p(i * 10.0, 30.0 * sin(2 * PI * i * 10.0 / 400.0))
            PuntoRecorrido(punto.lat, punto.lon, i * 1_000L, 15.0)
        }

    /** Graba [puntos] en lotes de 30 como la conducción y cierra el recorrido; espera el cálculo de calibración. */
    private suspend fun grabar(
        repo: RepositorioRecorridosRoom,
        puntos: List<PuntoRecorrido>,
    ): Long {
        val id = repo.empezar()
        puntos.chunked(30).forEach { repo.anadirPuntos(id, it) }
        checkNotNull(repo.terminar(id))
        repo.calibracionEnCurso?.join()
        return id
    }

    @Test
    fun `terminar usa la distancia acumulada y no relee los puntos`() =
        runBlocking {
            val contador = DaoContador(base.recorridosDao())
            val sinCalibrar = CoroutineScope(Job().apply { cancel() })
            val repo = RepositorioRecorridosRoom(contador, Dispatchers.Unconfined, sinCalibrar) { ahora }
            val id = repo.empezar("Sóller")
            repo.anadirPuntos(id, ruta(6))
            repo.anadirPuntos(id, ruta(5, desde = 6)) // 11 puntos, 1,1 km en dos lotes

            val recorrido = checkNotNull(repo.terminar(id))

            assertEquals(0, contador.lecturasDePuntos)
            assertEquals(20_000L, recorrido.finMs)
            assertEquals(1_112.0, recorrido.distanciaM, 5.0)
            // La distancia es la misma que da releer todos los puntos (recorrido huérfano de otro proceso).
            val huerfano =
                base.recorridosDao().insertar(
                    RecorridoEntity(inicioMs = 1L, finMs = null, distanciaM = 0.0, destinoNombre = null),
                )
            val filas = ruta(11).map { RecorridoPuntoEntity(0, 0, it.lat, it.lon, it.tiempoMs, null) }
            base.recorridosDao().anadirPuntos(huerfano, filas)
            val releido = checkNotNull(repo.terminar(huerfano))
            assertEquals(1, contador.lecturasDePuntos)
            assertEquals(recorrido.distanciaM, releido.distanciaM, 0.0)
            assertEquals(recorrido.finMs, releido.finMs)
        }

    @Test
    fun `el factor de calibracion se guarda al cerrar el recorrido y es el mismo que calcular con los puntos`() =
        runBlocking {
            val puntos = serpenteo(80)
            val repo = RepositorioRecorridosRoom(base.recorridosDao(), Dispatchers.Unconfined) { ahora }
            val id = grabar(repo, puntos)

            val guardado = checkNotNull(base.recorridosDao().calibracion())
            assertEquals(id.toString(), guardado.clave)
            val esperado =
                checkNotNull(PerfilVelocidad.calibrar(listOf(muestras(repo.puntos(id))), TablaGrados()).factor)
            assertEquals(esperado, guardado.factor)

            // Sin leer ni un punto: el valor sale de lo guardado.
            base.openHelper.writableDatabase.execSQL("DELETE FROM recorrido_punto")
            assertEquals(esperado, repo.factorCalibracion())
            assertEquals(esperado, CalibradorRecorridos(repo).factor())
        }

    @Test
    fun `con varios recorridos el factor es el de todos juntos, igual que antes`() =
        runBlocking {
            val repo = RepositorioRecorridosRoom(base.recorridosDao(), Dispatchers.Unconfined) { ahora }
            val a = grabar(repo, serpenteo(30))
            ahora = 5_000
            val b = grabar(repo, serpenteo(40).map { it.copy(velocidadMs = 12.0) })

            val juntos =
                PerfilVelocidad
                    .calibrar(
                        listOf(muestras(repo.puntos(b)), muestras(repo.puntos(a))),
                        TablaGrados(),
                    ).factor
            assertEquals(juntos, repo.factorCalibracion())
            assertEquals("$b,$a", base.recorridosDao().calibracion()?.clave)
            assertTrue(juntos != null)
        }

    @Test
    fun `si cambia el conjunto de recorridos el factor guardado se recalcula`() =
        runBlocking {
            val repo = RepositorioRecorridosRoom(base.recorridosDao(), Dispatchers.Unconfined) { ahora }
            val a = grabar(repo, serpenteo(80))
            val conA = repo.factorCalibracion()
            assertTrue(conA != null)

            repo.borrar(a)

            assertNull(repo.factorCalibracion())
            assertEquals("", base.recorridosDao().calibracion()?.clave)
            // Un guardado obsoleto (el proceso murió antes de recalcular) no se usa.
            base.recorridosDao().guardarCalibracion(CalibracionEntity(clave = "999", factor = 1.23))
            assertNull(repo.factorCalibracion())
        }

    @Test
    fun `sin recorridos no hay factor`() =
        runBlocking {
            assertNull(repositorio.factorCalibracion())
        }

    private fun muestras(puntos: List<PuntoRecorrido>) =
        puntos.map { MuestraVelocidad(PuntoGeo(it.lat, it.lon), it.velocidadMs) }
}

/** Cuenta cuántas veces se leen los puntos de un recorrido; el resto lo delega en el DAO real. */
private class DaoContador(
    private val real: RecorridosDao,
) : RecorridosDao() {
    var lecturasDePuntos = 0

    override suspend fun insertar(recorrido: RecorridoEntity) = real.insertar(recorrido)

    override suspend fun insertarPuntos(puntos: List<RecorridoPuntoEntity>) = real.insertarPuntos(puntos)

    override suspend fun ultimoOrden(id: Long) = real.ultimoOrden(id)

    override suspend fun recorrido(id: Long) = real.recorrido(id)

    override suspend fun puntos(id: Long): List<RecorridoPuntoEntity> {
        lecturasDePuntos++
        return real.puntos(id)
    }

    override fun observarTerminados() = real.observarTerminados()

    override suspend fun idsTerminadosRecientes(limite: Int) = real.idsTerminadosRecientes(limite)

    override suspend fun calibracion() = real.calibracion()

    override suspend fun guardarCalibracion(calibracion: CalibracionEntity) = real.guardarCalibracion(calibracion)

    override suspend fun idsEnCurso() = real.idsEnCurso()

    override suspend fun cerrar(
        id: Long,
        finMs: Long,
        distanciaM: Double,
    ) = real.cerrar(id, finMs, distanciaM)

    override suspend fun borrar(id: Long) = real.borrar(id)

    override suspend fun anadirPuntos(
        id: Long,
        puntos: List<RecorridoPuntoEntity>,
    ) = real.anadirPuntos(id, puntos)
}
