package app.trombito.rallycopilot.data.ajustes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.data.db.AjustesConduccionEntity
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.datos.CapaMapa
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositorioAjustesConduccionImplTest {
    private lateinit var base: RallyDatabase
    private lateinit var repositorio: RepositorioAjustesConduccionImpl

    @Before
    fun preparar() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        base = Room.inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java).allowMainThreadQueries().build()
        repositorio = RepositorioAjustesConduccionImpl(base.ajustesConduccionDao())
    }

    @After
    fun cerrar() = base.close()

    @Test
    fun `sin fila devuelve los valores por defecto`() =
        runBlocking {
            assertEquals(AjustesConduccion(), repositorio.ajustes.first())
        }

    @Test
    fun `guardar y volver a leer`() =
        runBlocking {
            val ajustes =
                AjustesConduccion(
                    nivelZoom = -2,
                    icono = IconoPosicion.CASCO,
                    orientacion = Orientacion.NORTE,
                    capa = CapaMapa.SATELITE,
                    relieve = true,
                )
            repositorio.guardar(ajustes)
            assertEquals(ajustes, repositorio.ajustes.first())

            repositorio.guardar(ajustes.copy(nivelZoom = 2))
            assertEquals(ajustes.copy(nivelZoom = 2), repositorio.ajustes.first())
        }

    @Test
    fun `un nivel de zoom fuera de rango en disco se acota`() =
        runBlocking {
            val dao = base.ajustesConduccionDao()
            dao.guardar(
                AjustesConduccionEntity(
                    nivelZoom = 9,
                    icono = "CASCO",
                    orientacion = "NORTE",
                    capa = "SATELITE",
                    relieve = true,
                ),
            )
            assertEquals(AjustesConduccion.NIVEL_MAX, repositorio.ajustes.first().nivelZoom)

            dao.guardar(
                AjustesConduccionEntity(
                    nivelZoom = -7,
                    icono = "CASCO",
                    orientacion = "NORTE",
                    capa = "SATELITE",
                    relieve = true,
                ),
            )
            assertEquals(AjustesConduccion.NIVEL_MIN, repositorio.ajustes.first().nivelZoom)
        }

    @Test
    fun `un enum desconocido en disco vuelve al valor por defecto`() =
        runBlocking {
            base.ajustesConduccionDao().guardar(
                AjustesConduccionEntity(nivelZoom = 1, icono = "OVNI", orientacion = "???", capa = "", relieve = true),
            )
            val ajustes = repositorio.ajustes.first()
            assertEquals(AjustesConduccion(nivelZoom = 1, relieve = true), ajustes)
        }
}
