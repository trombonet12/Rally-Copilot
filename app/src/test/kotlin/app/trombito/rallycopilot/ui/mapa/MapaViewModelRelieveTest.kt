package app.trombito.rallycopilot.ui.mapa

import androidx.lifecycle.SavedStateHandle
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoComplemento
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.ProgresoDescarga
import app.trombito.rallycopilot.motor.datos.Complemento
import app.trombito.rallycopilot.ui.comun.FalsaFuenteUbicacion
import app.trombito.rallycopilot.ui.comun.FalsoLocutor
import app.trombito.rallycopilot.ui.comun.FalsoProveedorVia
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioDatosMapa
import app.trombito.rallycopilot.ui.navegacion.FalsoControlNavegacion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** El relieve ya no va en el APK: activarlo sin sus datos lanza la descarga. */
@OptIn(ExperimentalCoroutinesApi::class)
class MapaViewModelRelieveTest {
    private val ajustes = FalsoRepositorioAjustesConduccion()
    private val navegacion = FalsoControlNavegacion()

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun crear(repositorio: FalsoRepositorioDatosMapa) =
        MapaViewModel(
            repositorio,
            CanalizacionGps(
                FalsaFuenteUbicacion(),
                FalsoProveedorVia(),
                navegacion,
                ajustes,
                FalsoLocutor(),
                reloj = { 0L },
            ),
            ajustes,
            navegacion,
            SavedStateHandle(),
        )

    private val base = DatosInstalados("2026.11.1", 1_000_000, conSatelite = false)

    @Test
    fun `activar el relieve sin sus datos pide descargarlo y guarda el ajuste`() {
        val repositorio = FalsoRepositorioDatosMapa(EstadoDatosMapa(instalados = base))

        crear(repositorio).onRelieve(true)

        assertEquals(listOf(Complemento.RELIEVE), repositorio.complementosDescargados)
        assertTrue(ajustes.mutable.value.relieve)
    }

    @Test
    fun `con el relieve ya instalado no se descarga nada`() {
        val estado =
            EstadoDatosMapa(
                instalados = base.copy(conRelieve = true),
                complementos = mapOf(Complemento.RELIEVE to EstadoComplemento(instalado = true, bytes = 10)),
            )
        val repositorio = FalsoRepositorioDatosMapa(estado)

        crear(repositorio).onRelieve(true)

        assertTrue(repositorio.complementosDescargados.isEmpty())
    }

    @Test
    fun `si el relieve ya se esta descargando no se vuelve a pedir`() {
        val descarga = ProgresoDescarga(10, 100, esperandoRed = false)
        val estado =
            EstadoDatosMapa(
                instalados = base,
                complementos = mapOf(Complemento.RELIEVE to EstadoComplemento(descarga = descarga)),
            )
        val repositorio = FalsoRepositorioDatosMapa(estado)

        crear(repositorio).onRelieve(true)

        assertTrue(repositorio.complementosDescargados.isEmpty())
    }

    @Test
    fun `apagar el relieve no descarga nada`() {
        val repositorio = FalsoRepositorioDatosMapa(EstadoDatosMapa(instalados = base))

        crear(repositorio).onRelieve(false)

        assertTrue(repositorio.complementosDescargados.isEmpty())
    }
}
