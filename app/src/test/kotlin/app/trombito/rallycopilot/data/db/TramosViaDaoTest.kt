package app.trombito.rallycopilot.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Consultas de la v21: tramos por tipo y de vía, y la cola de recorridos por trocear. */
@RunWith(RobolectricTestRunner::class)
class TramosViaDaoTest {
    private lateinit var base: RallyDatabase
    private lateinit var tramos: TramosDao
    private lateinit var recorridos: RecorridosDao

    @Before
    fun preparar() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        base = Room.inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java).allowMainThreadQueries().build()
        tramos = base.tramosDao()
        recorridos = base.recorridosDao()
    }

    @After
    fun cerrar() = base.close()

    private fun tramo(
        id: String,
        creadoEn: Long,
    ) = TramoEntity(
        id = id,
        nombre = null,
        traza = "x",
        longitudM = 1_000.0,
        sectores = "",
        microsectores = "",
        creadoEn = creadoEn,
    )

    private fun via(
        id: String,
        creadoEn: Long,
        viaRef: String,
        sentido: String,
        pkDesde: Double,
    ) = tramo(id, creadoEn).copy(
        tipo = TIPO_VIA,
        viaRef = viaRef,
        sentido = sentido,
        pkDesde = pkDesde,
        pkHasta = pkDesde + 1.0,
    )

    private fun recorrido(
        inicioMs: Long,
        finMs: Long?,
    ) = RecorridoEntity(inicioMs = inicioMs, finMs = finMs, distanciaM = 2_000.0, destinoNombre = null)

    @Test
    fun `un tramo sin tipo es un viaje y los de via se separan por tipo`() =
        runBlocking {
            tramos.insertarTramo(tramo("b", 2))
            tramos.insertarTramo(tramo("a", 1))
            tramos.insertarTramo(via("v", 3, "Ma-10", "CRECIENTE", 5.0))

            assertEquals(listOf("a", "b"), tramos.tramosPorTipo(TIPO_VIAJE).map { it.id })
            assertEquals(listOf("v"), tramos.tramosPorTipo(TIPO_VIA).map { it.id })
            assertEquals(TIPO_VIAJE, tramos.tramo("a")?.tipo)
        }

    @Test
    fun `los tramos de via se piden por carretera y sentido ordenados por pk`() =
        runBlocking {
            tramos.insertarTramo(via("v2", 1, "Ma-10", "CRECIENTE", 12.0))
            tramos.insertarTramo(via("v1", 2, "Ma-10", "CRECIENTE", 3.0))
            tramos.insertarTramo(via("contra", 3, "Ma-10", "DECRECIENTE", 3.0))
            tramos.insertarTramo(via("otra", 4, "Ma-13", "CRECIENTE", 3.0))
            tramos.insertarTramo(via("viaje", 5, "Ma-10", "CRECIENTE", 3.0).copy(tipo = TIPO_VIAJE))

            assertEquals(listOf("v1", "v2"), tramos.tramosViaDe("Ma-10", "CRECIENTE").map { it.id })
            assertEquals(listOf("contra"), tramos.tramosViaDe("Ma-10", "DECRECIENTE").map { it.id })
            assertEquals(emptyList<TramoEntity>(), tramos.tramosViaDe("Ma-10", "DESCONOCIDO"))
        }

    @Test
    fun `los recorridos terminados esperan a trocear hasta que se marcan`() =
        runBlocking {
            val a = recorridos.insertar(recorrido(10, 20))
            val b = recorridos.insertar(recorrido(5, 9))
            recorridos.insertar(recorrido(30, null))

            assertEquals(listOf(b, a), recorridos.recorridosSinTrocear().map { it.id })
            assertEquals(1, recorridos.marcarTroceado(b))
            assertEquals(listOf(a), recorridos.recorridosSinTrocear().map { it.id })
            assertEquals(true, recorridos.recorrido(b)?.viasTroceadas)
            assertEquals(0, recorridos.marcarTroceado(999))
        }

    @Test
    fun `el tiempo previsto se guarda y se puede borrar`() =
        runBlocking {
            val id = recorridos.insertar(recorrido(1, 2))
            assertEquals(null, recorridos.recorrido(id)?.tiempoPrevistoS)

            assertEquals(1, recorridos.guardarTiempoPrevisto(id, 1_234.5))
            assertEquals(1_234.5, recorridos.recorrido(id)?.tiempoPrevistoS)
            assertEquals(1, recorridos.guardarTiempoPrevisto(id, null))
            assertEquals(null, recorridos.recorrido(id)?.tiempoPrevistoS)
            assertEquals(0, recorridos.guardarTiempoPrevisto(999, 1.0))
        }
}
