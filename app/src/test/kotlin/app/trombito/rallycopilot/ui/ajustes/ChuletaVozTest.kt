package app.trombito.rallycopilot.ui.ajustes

import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.edicion.FrasesOrdenes
import app.trombito.rallycopilot.motor.edicion.InterpreteOrdenes
import app.trombito.rallycopilot.motor.edicion.Orden
import app.trombito.rallycopilot.motor.edicion.TipoPregunta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** La chuleta de voz sale de la fuente real de órdenes: lo que se enseña es lo que el copiloto entiende. */
@RunWith(RobolectricTestRunner::class)
class ChuletaVozTest {
    private val recursos = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private fun frases(grupo: GrupoVoz) =
        ChuletaVoz.frases(grupo, sueltas = emptyList(), lugarIr = "Sóller", lugarParada = "Inca").map { it.first }

    @Test
    fun `cada orden de FrasesOrdenes aparece en su grupo con su primera variante`() {
        FrasesOrdenes.porOrden.forEach { (orden, variantes) ->
            val grupo = GrupoVoz.de(orden)
            assertTrue("$orden no sale en $grupo", variantes.first() in frases(grupo))
        }
    }

    @Test
    fun `las preguntas se enseñan con signos y el resto sin ellos`() {
        assertEquals("«¿Cuánto falta?»", ChuletaVoz.ejemplo(Orden.Pregunta(TipoPregunta.CUANTO_FALTA)))
        assertEquals(
            "«Modo notas»",
            ChuletaVoz.ejemplo(Orden.Modo(app.trombito.rallycopilot.motor.voz.ModoCopiloto.NOTAS)),
        )
    }

    @Test
    fun `las órdenes con lugar enseñan los prefijos reales`() {
        val navegacion = frases(GrupoVoz.NAVEGACION)
        assertTrue("llévame a Sóller" in navegacion)
        assertTrue("añade parada en Inca" in navegacion)
        assertEquals(Orden.IrA("soller"), InterpreteOrdenes.interpretar("llévame a Sóller"))
        assertEquals(Orden.ParadaEn("inca"), InterpreteOrdenes.interpretar("añade parada en Inca"))
    }

    @Test
    fun `todas las frases enseñadas las entiende el intérprete`() {
        GrupoVoz.entries.forEach { grupo ->
            frases(grupo).forEach { assertNotNull("«$it» no se entiende", InterpreteOrdenes.interpretar(it)) }
        }
        listOf(R.array.ayuda_voz_control_sueltas, R.array.ayuda_voz_edicion).forEach { id ->
            recursos.getStringArray(id).forEach {
                val frase = it.trim('«', '»')
                assertNotNull("«$frase» no se entiende", InterpreteOrdenes.interpretar(frase))
            }
        }
    }
}
