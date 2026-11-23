package app.trombito.rallycopilot.ui.ajustes

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.ajustes.Tema
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ETIQUETA_VOLVER
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La portada de Ajustes: una fila por categoría, en una columna, con su valor y la navegación a cada subpantalla. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AjustesPortadaTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun tarjeta(seccion: SeccionAjustes) = banco.textoDe(ETIQUETA_SECCION_PREFIJO + seccion.clave)

    @Test
    fun `la portada tiene una fila por categoria con su titulo`() {
        banco.ponerAjustes()
        val titulos =
            mapOf(
                SeccionAjustes.COPILOTO to "Copiloto y notas",
                SeccionAjustes.VOZ to "Voz y avisos",
                SeccionAjustes.TRAMOS to "Tramos",
                SeccionAjustes.MAPA to "Mapa",
                SeccionAjustes.ESCUCHA to "Escucha",
                SeccionAjustes.TEMA to "Apariencia",
                SeccionAjustes.DATOS to "Datos",
                SeccionAjustes.AYUDA to "Ayuda y acerca de",
            )
        assertEquals(SeccionAjustes.entries.toSet(), titulos.keys)
        assertEquals(8, SeccionAjustes.entries.size)
        titulos.forEach { (seccion, titulo) ->
            assertTrue("Falta $titulo", tarjeta(seccion).startsWith(titulo))
            assertTrue(banco.esPulsable(banco.conEtiqueta(ETIQUETA_SECCION_PREFIJO + seccion.clave)))
        }
    }

    @Test
    fun `las filas ocupan todo el ancho en una sola columna y miden al menos 48 dp`() {
        banco.ponerAjustes()
        val filas = SeccionAjustes.entries.map { banco.conEtiqueta(ETIQUETA_SECCION_PREFIJO + it.clave) }
        assertEquals("una sola columna", 1, filas.map { it.positionInRoot.x }.toSet().size)
        assertEquals("mismo ancho", 1, filas.map { it.size.width }.toSet().size)
        filas.zipWithNext().forEach { (arriba, abajo) ->
            assertTrue("en orden vertical", abajo.positionInRoot.y >= arriba.positionInRoot.y + arriba.size.height)
        }
        filas.forEach { assertTrue("alto ${it.size.height}", it.size.height >= 48 * 3) }
    }

    @Test
    fun `cada fila enseña el valor por defecto de su ajuste`() {
        banco.ponerAjustes()
        assertTrue(tarjeta(SeccionAjustes.COPILOTO), "Completo · Anticipación 5 s" in tarjeta(SeccionAjustes.COPILOTO))
        assertTrue(
            tarjeta(SeccionAjustes.VOZ),
            "Sin elegir · 100 % · 2 de 4 indicaciones" in tarjeta(SeccionAjustes.VOZ),
        )
        assertTrue("Salida automática" in tarjeta(SeccionAjustes.TRAMOS))
        assertTrue("Rumbo arriba · Mapa" in tarjeta(SeccionAjustes.MAPA))
        assertTrue("«Copiloto» activado" in tarjeta(SeccionAjustes.ESCUCHA))
        assertTrue("Día y noche" in tarjeta(SeccionAjustes.TEMA))
        assertTrue("Copia y mapas offline" in tarjeta(SeccionAjustes.DATOS))
        assertTrue("Tutorial, notas, voz y licencias" in tarjeta(SeccionAjustes.AYUDA))
    }

    @Test
    fun `los valores de la portada siguen a los ajustes`() {
        banco.ponerAjustes(
            AjustesConduccion(
                modoCopiloto = ModoCopiloto.SILENCIO,
                anticipacion = NivelAnticipacion.MUY_PRONTO,
                voz = VozCopiloto.SHARVARD,
                volumenVoz = 120,
                avisarCruces = true,
                avisarPuentes = true,
                avisarRotondas = false,
                salidaManual = true,
                orientacion = Orientacion.NORTE,
                capa = CapaMapa.SATELITE,
                activarConCopiloto = true,
                tema = Tema.CLARO,
            ),
        )
        assertTrue("Silencio · Anticipación 8 s" in tarjeta(SeccionAjustes.COPILOTO))
        assertTrue("Sharvard · 120 % · 3 de 4 indicaciones" in tarjeta(SeccionAjustes.VOZ))
        assertTrue("Salida manual" in tarjeta(SeccionAjustes.TRAMOS))
        assertTrue("Norte arriba · Satélite" in tarjeta(SeccionAjustes.MAPA))
        assertTrue("«Copiloto» activado" in tarjeta(SeccionAjustes.ESCUCHA))
        assertTrue("Claro" in tarjeta(SeccionAjustes.TEMA))
    }

    @Test
    fun `sin cronometro y sin indicaciones la portada lo dice`() {
        banco.ponerAjustes(
            AjustesConduccion(
                cronometrar = false,
                avisarCruces = false,
                avisarPuentes = false,
                avisarSalidasAutopista = false,
                avisarRotondas = false,
            ),
        )
        assertTrue("Sin cronómetro" in tarjeta(SeccionAjustes.TRAMOS))
        assertTrue("Sin indicaciones" in tarjeta(SeccionAjustes.VOZ))
    }

    @Test
    fun `tocar una fila abre su subpantalla con la vista previa y volver regresa a la portada`() {
        val abiertas = mutableListOf<SeccionAjustes>()
        banco.ponerAjustes(acciones = AccionesAjustes(onSeccion = { abiertas += it }))
        assertFalse(banco.hay(ETIQUETA_SECCION_PREVISTA_PREFIJO + "copiloto"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_SECCION_PREFIJO + "copiloto"))
        assertEquals(listOf(SeccionAjustes.COPILOTO), abiertas)
        assertTrue(banco.hay(ETIQUETA_SECCION_PREVISTA_PREFIJO + "copiloto"))
        assertTrue("Anticipación de las notas" in banco.todosLosTextos())
        assertFalse("la portada ya no se ve", banco.hay(ETIQUETA_SECCION_PREFIJO + "copiloto"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertTrue(banco.hay(ETIQUETA_SECCION_PREFIJO + "copiloto"))
        assertFalse(banco.hay(ETIQUETA_SECCION_PREVISTA_PREFIJO + "copiloto"))
    }

    @Test
    fun `al volver de una subpantalla la portada ya muestra el valor cambiado`() {
        banco.ponerAjustes()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_SECCION_PREFIJO + "copiloto"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ANTICIPACION_PREFIJO + "muy_tarde"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertTrue("Anticipación 2,5 s" in tarjeta(SeccionAjustes.COPILOTO))
    }

    @Test
    fun `volver desde la portada llama a la accion`() {
        var volver = 0
        banco.ponerAjustes(acciones = AccionesAjustes(onVolver = { volver++ }))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertEquals(1, volver)
    }
}
