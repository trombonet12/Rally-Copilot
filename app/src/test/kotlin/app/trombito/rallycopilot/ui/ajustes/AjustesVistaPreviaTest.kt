package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.ajustes.Tema
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_VOZ_OPCION_PREFIJO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Vista previa fija de cada subpantalla de Ajustes: cambia en vivo al tocar la opción. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AjustesVistaPreviaTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun estadoDe(etiqueta: String) =
        banco
            .conEtiqueta(etiqueta)
            .children
            .first { banco.esPulsable(it) }
            .config
            .getOrNull(SemanticsProperties.ToggleableState)

    private fun descripcionDe(etiqueta: String) = banco.descripcion(banco.conEtiqueta(etiqueta))

    private fun ponerProgreso(
        nodo: SemanticsNode,
        valor: Float,
    ) {
        val accion = checkNotNull(nodo.config.getOrNull(SemanticsActions.SetProgress)) { "No es un deslizador" }
        accion.action?.invoke(valor)
        banco.reposo()
    }

    private fun comprobarVistaPrevia(seccion: SeccionAjustes) {
        banco.ponerAjustes(seccion = seccion)
        val previa = banco.conEtiqueta(ETIQUETA_SECCION_PREVISTA_PREFIJO + seccion.clave)
        val alto = previa.size.height
        assertTrue("$seccion mide $alto px", alto in (160 * 3)..(200 * 3))
        val desplazable = banco.nodos().first { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
        assertFalse(
            "La vista previa de $seccion se desplaza",
            contieneEtiqueta(desplazable, ETIQUETA_SECCION_PREVISTA_PREFIJO + seccion.clave),
        )
    }

    @Test
    fun `la vista previa de copiloto va arriba, fija y mide entre 160 y 200 dp`() =
        comprobarVistaPrevia(SeccionAjustes.COPILOTO)

    @Test
    fun `la vista previa de notas va arriba, fija y mide entre 160 y 200 dp`() =
        comprobarVistaPrevia(SeccionAjustes.NOTAS)

    @Test
    fun `la vista previa de voz va arriba, fija y mide entre 160 y 200 dp`() = comprobarVistaPrevia(SeccionAjustes.VOZ)

    @Test
    fun `la vista previa de indicaciones va arriba, fija y mide entre 160 y 200 dp`() =
        comprobarVistaPrevia(SeccionAjustes.INDICACIONES)

    @Test
    fun `la vista previa de tramos va arriba, fija y mide entre 160 y 200 dp`() =
        comprobarVistaPrevia(SeccionAjustes.TRAMOS)

    @Test
    fun `la vista previa de mapa va arriba, fija y mide entre 160 y 200 dp`() =
        comprobarVistaPrevia(SeccionAjustes.MAPA)

    @Test
    fun `la vista previa de escucha va arriba, fija y mide entre 160 y 200 dp`() =
        comprobarVistaPrevia(SeccionAjustes.ESCUCHA)

    @Test
    fun `la vista previa de tema va arriba, fija y mide entre 160 y 200 dp`() =
        comprobarVistaPrevia(SeccionAjustes.TEMA)

    @Test
    fun `datos no tiene vista previa`() {
        banco.ponerAjustes(seccion = SeccionAjustes.DATOS)
        assertFalse(banco.hay(ETIQUETA_SECCION_PREVISTA_PREFIJO + "datos"))
    }

    @Test
    fun `el interruptor de cronometrar trayectos esta activado por defecto y la vista previa lo explica`() {
        banco.ponerAjustes(seccion = SeccionAjustes.TRAMOS)
        assertEquals(ToggleableState.On, estadoDe(ETIQUETA_AJUSTES_CRONOMETRAR))
        assertTrue("Cronometrar trayectos" in banco.textos(banco.conEtiqueta(ETIQUETA_AJUSTES_CRONOMETRAR)))
        assertTrue(descripcionDe(ETIQUETA_PREVISTA_CRONO)!!.startsWith("Cronómetro activo"))
    }

    @Test
    fun `desactivar cronometrar cambia la vista previa a solo notas y guiado`() {
        banco.ponerAjustes(seccion = SeccionAjustes.TRAMOS)
        banco.pulsarInterruptor(ETIQUETA_AJUSTES_CRONOMETRAR)
        assertEquals(ToggleableState.Off, estadoDe(ETIQUETA_AJUSTES_CRONOMETRAR))
        assertEquals("Sin cronómetro: solo notas y guiado", descripcionDe(ETIQUETA_PREVISTA_CRONO))
    }

    @Test
    fun `la salida manual cambia el reloj a LISTO con la cuenta atras`() {
        banco.ponerAjustes(seccion = SeccionAjustes.TRAMOS)
        assertTrue(descripcionDe(ETIQUETA_PREVISTA_CRONO)!!.startsWith("Cronómetro activo"))
        banco.pulsarInterruptor(ETIQUETA_AJUSTES_SALIDA_MANUAL)
        assertEquals(ToggleableState.On, estadoDe(ETIQUETA_AJUSTES_SALIDA_MANUAL))
        assertTrue(descripcionDe(ETIQUETA_PREVISTA_CRONO)!!.startsWith("Cronómetro listo"))
    }

    @Test
    fun `las notas grandes tambien se pueden activar desde la seccion Notas`() {
        banco.ponerAjustes(seccion = SeccionAjustes.NOTAS)
        banco.pulsarInterruptor(ETIQUETA_NOTAS_GRANDES)
        assertEquals(ToggleableState.On, estadoDe(ETIQUETA_NOTAS_GRANDES))
    }

    @Test
    fun `la tarjeta de nota de ejemplo cambia entre derecha 4 larga y derecha media al tocar simplificadas`() {
        banco.ponerAjustes(seccion = SeccionAjustes.NOTAS)
        val completa = banco.textoDe(ETIQUETA_PREVISTA_NOTA)
        assertTrue(completa, completa.startsWith("DERECHA 4 LARGA"))
        assertTrue(completa, "50" in completa)
        banco.pulsarInterruptor(ETIQUETA_NOTAS_SIMPLIFICADAS)
        val simplificada = banco.textoDe(ETIQUETA_PREVISTA_NOTA)
        assertTrue(simplificada, "MEDIA" in simplificada)
        assertFalse(simplificada, "DERECHA 4" in simplificada)
    }

    @Test
    fun `te la canta N s antes cambia al tocar cada nivel de anticipacion`() {
        banco.ponerAjustes(seccion = SeccionAjustes.NOTAS)
        assertEquals("Te la canta 5 s antes (a 90 km/h, a 150 m)", banco.textoDe(ETIQUETA_PREVISTA_ANTICIPACION))
        val esperados =
            mapOf(
                NivelAnticipacion.MUY_PRONTO to "Te la canta 8 s antes (a 90 km/h, a 230 m)",
                NivelAnticipacion.PRONTO to "Te la canta 6,5 s antes (a 90 km/h, a 190 m)",
                NivelAnticipacion.TARDE to "Te la canta 3,5 s antes (a 90 km/h, a 110 m)",
                NivelAnticipacion.MUY_TARDE to "Te la canta 2,5 s antes (a 90 km/h, a 90 m)",
            )
        esperados.forEach { (nivel, texto) ->
            banco.pulsar(banco.conEtiqueta(ETIQUETA_ANTICIPACION_PREFIJO + nivel.name.lowercase()))
            assertEquals(texto, banco.textoDe(ETIQUETA_PREVISTA_ANTICIPACION))
        }
    }

    @Test
    fun `la barra de anticipacion dice a cuantos metros suena el aviso y cambia con el nivel`() {
        banco.ponerAjustes(seccion = SeccionAjustes.NOTAS)
        assertEquals("La nota suena a 150 metros de la curva", descripcionDe(ETIQUETA_PREVISTA_BARRA))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ANTICIPACION_PREFIJO + "muy_tarde"))
        assertEquals("La nota suena a 90 metros de la curva", descripcionDe(ETIQUETA_PREVISTA_BARRA))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ANTICIPACION_PREFIJO + "muy_pronto"))
        assertEquals("La nota suena a 230 metros de la curva", descripcionDe(ETIQUETA_PREVISTA_BARRA))
    }

    @Test
    fun `la vista previa del copiloto enseña el modo elegido y la nota de ejemplo`() {
        banco.ponerAjustes(seccion = SeccionAjustes.COPILOTO)
        val previa = banco.conEtiqueta(ETIQUETA_SECCION_PREVISTA_PREFIJO + "copiloto")
        assertTrue("Completo" in banco.textos(previa))
        assertTrue(banco.hay(ETIQUETA_PREVISTA_NOTA))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_AJUSTES_MODO_PREFIJO + "silencio"))
        val cambiada = banco.textos(banco.conEtiqueta(ETIQUETA_SECCION_PREVISTA_PREFIJO + "copiloto"))
        assertTrue("Silencio" in cambiada)
        assertTrue("El copiloto no habla" in cambiada)
        assertFalse("Completo" in cambiada)
    }

    @Test
    fun `escuchar ejemplo se pide desde la vista previa de copiloto y de voz`() {
        var pedidos = 0
        val acciones = AccionesAjustes(onEscucharEjemplo = { pedidos++ })
        listOf(SeccionAjustes.COPILOTO, SeccionAjustes.VOZ).forEach { seccion ->
            banco.ponerAjustes(seccion = seccion, acciones = acciones)
            val botones =
                banco.nodos().filter { it.config.getOrNull(SemanticsProperties.TestTag) == ETIQUETA_PREVISTA_ESCUCHAR }
            assertEquals(1, botones.size)
            assertTrue(banco.descripcion(botones.single())!!.startsWith("Escuchar un ejemplo"))
            banco.pulsar(botones.single())
        }
        assertEquals(2, pedidos)
    }

    @Test
    fun `la vista previa de voz enseña el nombre real de la voz y cambia al elegirla`() {
        banco.ponerAjustes(seccion = SeccionAjustes.VOZ)
        assertEquals("Elige una voz para que hable", banco.textoDe(ETIQUETA_PREVISTA_VOZ_NOMBRE))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOZ_OPCION_PREFIJO + VozCopiloto.SHARVARD.name.lowercase()))
        assertEquals("Sharvard", banco.textoDe(ETIQUETA_PREVISTA_VOZ_NOMBRE))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOZ_OPCION_PREFIJO + VozCopiloto.DAVEFX.name.lowercase()))
        assertEquals("Dave", banco.textoDe(ETIQUETA_PREVISTA_VOZ_NOMBRE))
        assertEquals("Voz Dave al 100 por ciento", descripcionDe(ETIQUETA_PREVISTA_VOZ))
        assertFalse(banco.todosLosTextos().any { it.startsWith("Voz 1") || it.startsWith("Voz 2") })
    }

    @Test
    fun `el deslizador de volumen va de 50 a 150 de diez en diez y la vista previa lo refleja`() {
        banco.ponerAjustes(AjustesConduccion(voz = VozCopiloto.DAVEFX), SeccionAjustes.VOZ)
        assertEquals("100 %", banco.textoDe(ETIQUETA_AJUSTES_VOLUMEN_VALOR))
        ponerProgreso(banco.conEtiqueta(ETIQUETA_AJUSTES_VOLUMEN), 130f)
        assertEquals("130 %", banco.textoDe(ETIQUETA_AJUSTES_VOLUMEN_VALOR))
        assertTrue("Volumen 130 %" in banco.textos(banco.conEtiqueta(ETIQUETA_PREVISTA_VOZ)))
        assertEquals("Voz Dave al 130 por ciento", descripcionDe(ETIQUETA_PREVISTA_VOZ))
        ponerProgreso(banco.conEtiqueta(ETIQUETA_AJUSTES_VOLUMEN), 50f)
        assertEquals("50 %", banco.textoDe(ETIQUETA_AJUSTES_VOLUMEN_VALOR))
    }

    @Test
    fun `la vista previa de indicaciones tacha la que se desactiva`() {
        banco.ponerAjustes(seccion = SeccionAjustes.INDICACIONES)
        // De base cruces y puentes están apagados; salidas y rotondas, activas.
        listOf("cruces" to "Cruces", "puentes" to "Puentes").forEach { (clave, nombre) ->
            assertEquals("$nombre: no avisa", descripcionDe(ETIQUETA_PREVISTA_INDICACION_PREFIJO + clave))
        }
        listOf("salidas" to "Salidas", "rotondas" to "Rotondas").forEach { (clave, nombre) ->
            assertEquals("$nombre: avisa", descripcionDe(ETIQUETA_PREVISTA_INDICACION_PREFIJO + clave))
        }
        banco.pulsarInterruptor(ETIQUETA_AJUSTES_CRUCES)
        assertEquals(ToggleableState.On, estadoDe(ETIQUETA_AJUSTES_CRUCES))
        assertEquals("Cruces: avisa", descripcionDe(ETIQUETA_PREVISTA_INDICACION_PREFIJO + "cruces"))
        assertEquals("Puentes: no avisa", descripcionDe(ETIQUETA_PREVISTA_INDICACION_PREFIJO + "puentes"))
        banco.pulsarInterruptor(ETIQUETA_AJUSTES_PUENTES)
        assertEquals(ToggleableState.On, estadoDe(ETIQUETA_AJUSTES_PUENTES))
        assertEquals("Puentes: avisa", descripcionDe(ETIQUETA_PREVISTA_INDICACION_PREFIJO + "puentes"))
        assertEquals("Salidas: avisa", descripcionDe(ETIQUETA_PREVISTA_INDICACION_PREFIJO + "salidas"))
        banco.pulsarInterruptor(ETIQUETA_AJUSTES_ROTONDAS)
        assertEquals("Rotondas: no avisa", descripcionDe(ETIQUETA_PREVISTA_INDICACION_PREFIJO + "rotondas"))
    }

    @Test
    fun `el mapa de ejemplo muestra el icono de posicion elegido y lo describe`() {
        banco.ponerAjustes(seccion = SeccionAjustes.MAPA)
        assertTrue(banco.hay(ETIQUETA_PREVISTA_MAPA_ICONO + IconoPosicion.FLECHA_WRC.name.lowercase()))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ICONO_PREFIJO + IconoPosicion.CASCO.name.lowercase()))
        assertTrue(banco.hay(ETIQUETA_PREVISTA_MAPA_ICONO + "casco"))
        assertFalse(banco.hay(ETIQUETA_PREVISTA_MAPA_ICONO + "flecha_wrc"))
        assertEquals(
            "Mapa de ejemplo: Rumbo arriba, Mapa, icono Casco de Trombito",
            descripcionDe(ETIQUETA_PREVISTA_MAPA),
        )
    }

    @Test
    fun `el mapa de ejemplo cambia con la orientacion y la capa`() {
        banco.ponerAjustes(AjustesConduccion(orientacion = Orientacion.RUMBO), SeccionAjustes.MAPA)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ORIENTACION_NORTE))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_CAPA_PREFIJO + CapaMapa.SATELITE.name.lowercase()))
        assertEquals(
            "Mapa de ejemplo: Norte arriba, Satélite, icono Flecha de rally",
            descripcionDe(ETIQUETA_PREVISTA_MAPA),
        )
    }

    @Test
    fun `el relieve es un interruptor del mapa`() {
        banco.ponerAjustes(seccion = SeccionAjustes.MAPA)
        assertEquals(ToggleableState.Off, estadoDe(ETIQUETA_AJUSTES_RELIEVE))
        banco.pulsarInterruptor(ETIQUETA_AJUSTES_RELIEVE)
        assertEquals(ToggleableState.On, estadoDe(ETIQUETA_AJUSTES_RELIEVE))
    }

    @Test
    fun `la vista previa de escucha refleja si la palabra Copiloto esta activa`() {
        banco.ponerAjustes(AjustesConduccion(activarConCopiloto = false), SeccionAjustes.ESCUCHA)
        assertEquals("No escucha: usa el botón del micrófono", descripcionDe(ETIQUETA_PREVISTA_ESCUCHA))
    }

    @Test
    fun `con la palabra Copiloto activa la vista previa enseña el micro escuchando`() {
        // Activada por defecto (decisión de Joan, 2026-11-06).
        banco.ponerAjustes(seccion = SeccionAjustes.ESCUCHA)
        assertEquals("Escucha la palabra Copiloto mientras conduces", descripcionDe(ETIQUETA_PREVISTA_ESCUCHA))
    }

    @Test
    fun `las miniaturas del tema resaltan la elegida y la muestra cambia al tocar oscuro claro o automatico`() {
        banco.ponerAjustes(seccion = SeccionAjustes.TEMA)
        // Día y noche es el tema por defecto (decisión de Joan, 2026-11-08).
        assertEquals("Muestra del tema Día y noche", descripcionDe(ETIQUETA_TEMA_MUESTRA))
        assertTrue(banco.hay(ETIQUETA_TEMA_MINIATURA_PREFIJO + "oscuro"))
        assertTrue(banco.hay(ETIQUETA_TEMA_MINIATURA_PREFIJO + "claro"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_TEMA_PREFIJO + Tema.OSCURO.name.lowercase()))
        assertEquals("Muestra del tema Oscuro", descripcionDe(ETIQUETA_TEMA_MUESTRA))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_TEMA_PREFIJO + Tema.CLARO.name.lowercase()))
        assertEquals("Muestra del tema Claro", descripcionDe(ETIQUETA_TEMA_MUESTRA))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_TEMA_PREFIJO + Tema.SISTEMA.name.lowercase()))
        assertEquals("Muestra del tema Automático", descripcionDe(ETIQUETA_TEMA_MUESTRA))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_TEMA_PREFIJO + Tema.SOL.name.lowercase()))
        assertEquals("Muestra del tema Día y noche", descripcionDe(ETIQUETA_TEMA_MUESTRA))
        Tema.entries.forEach {
            assertTrue(banco.esPulsable(banco.conEtiqueta(ETIQUETA_TEMA_PREFIJO + it.name.lowercase())))
        }
        val visibles = banco.textos(banco.raiz())
        listOf("Oscuro", "Claro", "Automático", "Día y noche").forEach { assertTrue("Falta $it", it in visibles) }
    }

    @Test
    fun `el boton de informacion despliega la explicacion y la pliega`() {
        banco.ponerAjustes(seccion = SeccionAjustes.NOTAS)
        val texto = "Cuántos segundos antes de la curva te canta la nota."
        assertFalse(texto in banco.todosLosTextos())
        val info = banco.conEtiqueta(ETIQUETA_INFO_PREFIJO + "ajustes_anticipacion_titulo")
        assertTrue(banco.descripcion(info)!!.startsWith("Más información"))
        banco.pulsar(info)
        assertTrue(texto in banco.todosLosTextos())
    }
}
