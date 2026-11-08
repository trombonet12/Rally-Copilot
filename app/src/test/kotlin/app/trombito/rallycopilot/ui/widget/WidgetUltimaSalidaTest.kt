package app.trombito.rallycopilot.ui.widget

import android.app.Application
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import app.trombito.rallycopilot.ui.comun.detalladaFalsa
import app.trombito.rallycopilot.ui.comun.resumenFalso
import app.trombito.rallycopilot.ui.comun.tramoFalso
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WidgetUltimaSalidaTest {
    private val contexto: Application = ApplicationProvider.getApplicationContext()

    private fun pintar(contenido: ContenidoWidget): View =
        construirVistas(contexto, contenido).apply(contexto, FrameLayout(contexto))

    private fun View.texto(id: Int): String = findViewById<TextView>(id).text.toString()

    @Test
    fun `sin salidas el contenido es vacio`() =
        runTest {
            val repositorio = FalsoRepositorioTramos(listOf(resumenFalso("a", pasadas = 0, mejorMs = null)))
            assertEquals(ContenidoWidget.Vacio, contenidoWidget(repositorio))
        }

    @Test
    fun `con salidas el contenido es el ultimo tramo con su tiempo y su mejor tiempo`() =
        runTest {
            val repositorio =
                FalsoRepositorioTramos(
                    listOf(
                        resumenFalso("a", "Ma-2141 · Escorca", pasadas = 2, mejorMs = 252_300L, ultimoUsoMs = 3_000L),
                        resumenFalso("b", "Ma-10 · Valldemossa", ultimoUsoMs = 1_000L),
                    ),
                    pasadas =
                        mapOf(
                            "a" to
                                listOf(
                                    detalladaFalsa(2, "a", 257_300L, 2_000L),
                                    detalladaFalsa(1, "a", 252_300L, 1_000L, esMejor = true),
                                ),
                        ),
                    trazas = mapOf("a" to tramoFalso("a")),
                )
            assertEquals(
                ContenidoWidget.ConSalida("Ma-2141 · Escorca", "04:17,3", "04:12,3"),
                contenidoWidget(repositorio),
            )
        }

    @Test
    fun `las vistas con salida dicen tramo tiempo y mejor con coma decimal`() {
        val vista = pintar(ContenidoWidget.ConSalida("Ma-2141 · Escorca", "04:17,3", "04:12,3"))
        assertEquals("Ma-2141 · Escorca", vista.texto(R.id.widget_nombre))
        assertEquals("04:17,3", vista.texto(R.id.widget_tiempo))
        assertEquals("mejor 04:12,3", vista.texto(R.id.widget_mejor))
        assertEquals(View.VISIBLE, vista.findViewById<View>(R.id.widget_tiempos).visibility)
    }

    @Test
    fun `las vistas sin salidas invitan a conducir el primer tramo y esconden los tiempos`() {
        val vista = pintar(ContenidoWidget.Vacio)
        assertEquals("Conduce tu primer tramo", vista.texto(R.id.widget_nombre))
        assertEquals(View.GONE, vista.findViewById<View>(R.id.widget_tiempos).visibility)
    }

    @Test
    fun `el widget siempre tiene el titulo y el boton conducir`() {
        listOf(ContenidoWidget.Vacio, ContenidoWidget.ConSalida("T", "00:01,0", "00:01,0")).forEach {
            val vista = pintar(it)
            assertEquals("Última salida", vista.texto(R.id.widget_titulo))
            assertEquals("Conducir", vista.texto(R.id.widget_conducir))
            assertTrue(vista.findViewById<View>(R.id.widget_conducir).hasOnClickListeners())
        }
    }
}
