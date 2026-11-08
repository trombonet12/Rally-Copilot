package app.trombito.rallycopilot.ui.widget

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.MainActivity
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import app.trombito.rallycopilot.ui.comun.detalladaFalsa
import app.trombito.rallycopilot.ui.comun.resumenFalso
import app.trombito.rallycopilot.ui.comun.tramoFalso
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AtajoTest {
    private val contexto: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `el intent de cada atajo se traduce de vuelta al mismo atajo`() {
        Atajo.entries.forEach { assertEquals(it, it.intent(contexto).atajo()) }
    }

    @Test
    fun `el intent del atajo es explicito a MainActivity`() {
        val intent = Atajo.CONDUCIR_LIBRE.intent(contexto)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(ACCION_ATAJO, intent.action)
    }

    @Test
    fun `el intent que escribe shortcuts xml se reconoce`() {
        val intent = Intent(ACCION_ATAJO).putExtra(EXTRA_ATAJO, "repetir_ultimo")
        assertEquals(Atajo.REPETIR_ULTIMO, intent.atajo())
    }

    @Test
    fun `un intent sin la accion o con una clave desconocida no es un atajo`() {
        assertNull(Intent(Intent.ACTION_MAIN).atajo())
        assertNull(Intent(Intent.ACTION_MAIN).putExtra(EXTRA_ATAJO, "conducir_libre").atajo())
        assertNull(Intent(ACCION_ATAJO).atajo())
        assertNull(Intent(ACCION_ATAJO).putExtra(EXTRA_ATAJO, "borrar_todo").atajo())
    }

    @Test
    fun `conducir libre va al mapa en libre sin mirar los tramos`() =
        runTest {
            val destino = resolverAtajo(Atajo.CONDUCIR_LIBRE, FalsoRepositorioTramos())
            assertEquals(DestinoAtajo.Libre, destino)
        }

    @Test
    fun `repetir va a la salida del ultimo tramo con pasadas`() =
        runTest {
            val repositorio =
                FalsoRepositorioTramos(
                    listOf(
                        resumenFalso("sin", "Sin pasadas", pasadas = 0, mejorMs = null, ultimoUsoMs = 5_000L),
                        resumenFalso("a", "Ma-2141 · Escorca", ultimoUsoMs = 3_000L),
                    ),
                    pasadas = mapOf("a" to listOf(detalladaFalsa(1, "a", 90_000L, 1_000L, esMejor = true))),
                    trazas = mapOf("a" to tramoFalso("a")),
                )
            val destino = resolverAtajo(Atajo.REPETIR_ULTIMO, repositorio)
            assertEquals(DestinoAtajo.AlTramo(Destino("Ma-2141 · Escorca", PuntoGeo(39.80, 2.80))), destino)
        }

    @Test
    fun `repetir sin tramos cronometrados lo dice`() =
        runTest {
            val destino = resolverAtajo(Atajo.REPETIR_ULTIMO, FalsoRepositorioTramos())
            assertEquals(DestinoAtajo.SinTramos, destino)
        }

    @Test
    fun `el paso abre el mapa salvo que ya se este en el`() {
        val alTramo = DestinoAtajo.AlTramo(Destino("Tramo", PuntoGeo(39.8, 2.8)))
        assertTrue(pasoDeAtajo(DestinoAtajo.Libre, enMapa = false).abrirMapa)
        assertFalse(pasoDeAtajo(DestinoAtajo.Libre, enMapa = true).abrirMapa)
        assertTrue(pasoDeAtajo(alTramo, enMapa = false).abrirMapa)
        assertFalse(pasoDeAtajo(alTramo, enMapa = true).abrirMapa)
    }

    @Test
    fun `solo repetir pasa el destino al mapa y solo sin tramos se avisa`() {
        val destino = Destino("Tramo", PuntoGeo(39.8, 2.8))
        assertEquals(destino, pasoDeAtajo(DestinoAtajo.AlTramo(destino), enMapa = true).destinoEnMapa)
        assertNull(pasoDeAtajo(DestinoAtajo.Libre, enMapa = false).destinoEnMapa)
        assertTrue(pasoDeAtajo(DestinoAtajo.SinTramos, enMapa = false).avisarSinTramos)
        assertNull(pasoDeAtajo(DestinoAtajo.SinTramos, enMapa = false).destinoEnMapa)
        assertFalse(pasoDeAtajo(DestinoAtajo.AlTramo(destino), enMapa = false).avisarSinTramos)
    }
}
