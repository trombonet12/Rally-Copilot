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

/** La portada de Ajustes: una tarjeta por sección con su valor, y la navegación a cada subpantalla. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AjustesPortadaTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun tarjeta(seccion: SeccionAjustes) = banco.textoDe(ETIQUETA_SECCION_PREFIJO + seccion.clave)

    @Test
    fun `la portada tiene una tarjeta por seccion con su titulo`() {
        banco.ponerAjustes()
        val titulos =
            mapOf(
                SeccionAjustes.COPILOTO to "Copiloto",
                SeccionAjustes.NOTAS to "Notas",
                SeccionAjustes.VOZ to "Voz",
                SeccionAjustes.INDICACIONES to "Indicaciones",
                SeccionAjustes.TRAMOS to "Tramos",
                SeccionAjustes.MAPA to "Mapa",
                SeccionAjustes.ESCUCHA to "Escucha",
                SeccionAjustes.TEMA to "Tema",
                SeccionAjustes.DATOS to "Datos",
                SeccionAjustes.ACERCA_DE to "Acerca de",
            )
        assertEquals(SeccionAjustes.entries.toSet(), titulos.keys)
        titulos.forEach { (seccion, titulo) ->
            assertTrue("Falta $titulo", tarjeta(seccion).startsWith(titulo))
            assertTrue(banco.esPulsable(banco.conEtiqueta(ETIQUETA_SECCION_PREFIJO + seccion.clave)))
        }
    }

    @Test
    fun `cada tarjeta enseña el valor por defecto de su ajuste`() {
        banco.ponerAjustes()
        assertTrue(tarjeta(SeccionAjustes.COPILOTO), "Completo" in tarjeta(SeccionAjustes.COPILOTO))
        assertTrue("Anticipación 5 s" in tarjeta(SeccionAjustes.NOTAS))
        assertTrue("Sin elegir · 100 %" in tarjeta(SeccionAjustes.VOZ))
        assertTrue("Todas activas" in tarjeta(SeccionAjustes.INDICACIONES))
        assertTrue("Salida automática" in tarjeta(SeccionAjustes.TRAMOS))
        assertTrue("Rumbo arriba · Mapa" in tarjeta(SeccionAjustes.MAPA))
        assertTrue("Desactivada" in tarjeta(SeccionAjustes.ESCUCHA))
        assertTrue("Oscuro" in tarjeta(SeccionAjustes.TEMA))
    }

    @Test
    fun `los valores de la portada siguen a los ajustes`() {
        banco.ponerAjustes(
            AjustesConduccion(
                modoCopiloto = ModoCopiloto.SILENCIO,
                anticipacion = NivelAnticipacion.MUY_PRONTO,
                voz = VozCopiloto.SHARVARD,
                volumenVoz = 120,
                avisarCruces = false,
                salidaManual = true,
                orientacion = Orientacion.NORTE,
                capa = CapaMapa.SATELITE,
                activarConCopiloto = true,
                tema = Tema.CLARO,
            ),
        )
        assertTrue("Silencio" in tarjeta(SeccionAjustes.COPILOTO))
        assertTrue("Anticipación 8 s" in tarjeta(SeccionAjustes.NOTAS))
        assertTrue("Sharvard · 120 %" in tarjeta(SeccionAjustes.VOZ))
        assertTrue("2 de 3 activas" in tarjeta(SeccionAjustes.INDICACIONES))
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
                avisarSalidasAutopista = false,
                avisarRotondas = false,
            ),
        )
        assertTrue("Sin cronómetro" in tarjeta(SeccionAjustes.TRAMOS))
        assertTrue("Ninguna activa" in tarjeta(SeccionAjustes.INDICACIONES))
    }

    @Test
    fun `tocar una tarjeta abre su subpantalla con la vista previa y volver regresa a la portada`() {
        val abiertas = mutableListOf<SeccionAjustes>()
        banco.ponerAjustes(acciones = AccionesAjustes(onSeccion = { abiertas += it }))
        assertFalse(banco.hay(ETIQUETA_SECCION_PREVISTA_PREFIJO + "notas"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_SECCION_PREFIJO + "notas"))
        assertEquals(listOf(SeccionAjustes.NOTAS), abiertas)
        assertTrue(banco.hay(ETIQUETA_SECCION_PREVISTA_PREFIJO + "notas"))
        assertTrue("Anticipación de las notas" in banco.todosLosTextos())
        assertFalse("la portada ya no se ve", banco.hay(ETIQUETA_SECCION_PREFIJO + "copiloto"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertTrue(banco.hay(ETIQUETA_SECCION_PREFIJO + "copiloto"))
        assertFalse(banco.hay(ETIQUETA_SECCION_PREVISTA_PREFIJO + "notas"))
    }

    @Test
    fun `al volver de una subpantalla la portada ya muestra el valor cambiado`() {
        banco.ponerAjustes()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_SECCION_PREFIJO + "notas"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ANTICIPACION_PREFIJO + "muy_tarde"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertTrue("Anticipación 2,5 s" in tarjeta(SeccionAjustes.NOTAS))
    }

    @Test
    fun `volver desde la portada llama a la accion`() {
        var volver = 0
        banco.ponerAjustes(acciones = AccionesAjustes(onVolver = { volver++ }))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertEquals(1, volver)
    }
}
