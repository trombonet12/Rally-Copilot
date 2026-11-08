package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.domain.edicion.TramoEditable
import app.trombito.rallycopilot.domain.escucha.MarcaConPosicion
import app.trombito.rallycopilot.motor.edicion.Marca
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.TipoAviso
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.ui.analisis.formatearKmDesdeM
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ETIQUETA_VOLVER
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.edicion.AccionesMapaEdicion
import app.trombito.rallycopilot.ui.edicion.ETIQUETA_EDITOR_AVISO_PREFIJO
import app.trombito.rallycopilot.ui.edicion.ETIQUETA_EDITOR_DIVIDIR
import app.trombito.rallycopilot.ui.edicion.ETIQUETA_EDITOR_FUSIONAR
import app.trombito.rallycopilot.ui.edicion.ETIQUETA_EDITOR_MODO_AVISO
import app.trombito.rallycopilot.ui.edicion.EscenaEdicion
import app.trombito.rallycopilot.ui.edicion.TipoMarcador
import app.trombito.rallycopilot.ui.edicion.idMarcadorNota
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class EditarTramoScreenTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private val traza = List(41) { PuntoGeo(39.6 + it * 0.00085, 2.9) }
    private val tramo = Tramo("t1", traza, traza.zipWithNext { a, b -> a.distanciaA(b) }.sum())
    private val primera = Nota(300.0, 340.0, Direccion.DERECHA, 4)
    private val segunda = Nota(900.0, 950.0, Direccion.IZQUIERDA, null, cuidado = true)
    private val tercera = Nota(1_500.0, 1_540.0, Direccion.IZQUIERDA, 2)

    private val datos =
        TramoEditable(
            tramo,
            HojaNotas(listOf(primera, segunda, tercera).map { ElementoHoja.DeNota(it) }),
            listOf(MarcaConPosicion(Marca(700.0, 1_000L), null)),
            editadas = 1,
        )

    private val pedidas = mutableListOf<String>()
    private val escenas = mutableListOf<EscenaEdicion>()
    private var gestos = AccionesMapaEdicion()
    private val mapaFalso: MapaDeEdicion = { _, escena, acciones, _: Modifier ->
        escenas += escena
        gestos = acciones
    }

    private val acciones =
        AccionesEditarTramo(
            onVolver = { pedidas += "volver" },
            onTocarNota = { pedidas += "editar:${it.posicionM.toInt()}" },
            onSeleccionar = { nota, centrar -> pedidas += "elegir:${nota.posicionM.toInt()}:$centrar" },
            onTocarPunto = { pedidas += "punto:${it.toInt()}" },
            onMoverCorte = { i, m -> pedidas += "corte:$i:${m.toInt()}" },
            onSoltarCorte = { pedidas += "soltar" },
            onRestaurar = { pedidas += "restaurar" },
            onFusionar = { pedidas += "fusionar" },
            onDividir = { pedidas += "dividir" },
            onAnadirAviso = { pedidas += "aviso:${it.name}" },
            onQuitarAviso = { pedidas += "quitar-aviso:${it.toInt()}" },
        )

    @Suppress("LongParameterList")
    private fun estado(
        estilo: String? = "{}",
        seleccionM: Double? = null,
        centrarVersion: Int = 0,
        centrar: Boolean = centrarVersion > 0,
        editor: EditorDeTramo? = null,
        tramoEditable: TramoEditable = datos,
    ) = EditarTramoUiState(
        cargando = false,
        datos = tramoEditable,
        editor = editor,
        sectoresM = listOf(600.0, 1_200.0),
        microsectoresPorKm = 1,
        microsectoresM = listOf(1_000.0),
        estiloMapaJson = estilo,
        seleccionM = seleccionM,
        centrarVersion = centrarVersion,
        centrarSeleccion = centrar,
    )

    private fun poner(estado: EditarTramoUiState) {
        banco.poner { RallyCopilotTheme { EditarTramoScreen(estado, acciones, mapa = mapaFalso) } }
    }

    private fun fila(nota: Nota) = banco.conEtiqueta(ETIQUETA_EDITAR_NOTA + idMarcadorNota(nota))

    @Test
    fun `la lista trae las notas por kilometro con su texto corto`() {
        poner(estado())
        val filas = banco.etiquetas(ETIQUETA_EDITAR_NOTA).filterNot { it.startsWith(ETIQUETA_EDITAR_NOTA_EDITAR) }
        assertEquals(listOf(primera, segunda, tercera).map { ETIQUETA_EDITAR_NOTA + idMarcadorNota(it) }, filas)
        assertTrue("D4" in banco.textos(fila(primera)))
        assertTrue("IH" in banco.textos(fila(segunda)))
    }

    @Test
    fun `el mapa recibe la traza y un marcador por nota, marca y corte`() {
        poner(estado())
        val escena = escenas.last()
        assertEquals(traza, escena.traza)
        val tipos = escena.marcadores.groupingBy { it.tipo }.eachCount()
        assertEquals(2, tipos[TipoMarcador.NOTA])
        assertEquals(1, tipos[TipoMarcador.CUIDADO])
        assertEquals(1, tipos[TipoMarcador.MARCA])
        assertEquals(2, tipos[TipoMarcador.CORTE])
        assertEquals(1, escena.microsectores.size)
    }

    @Test
    fun `la leyenda cuenta cada tipo y explica las marcas`() {
        poner(estado())
        assertEquals(listOf("Nota 2"), banco.textos(banco.conEtiqueta(ETIQUETA_EDITAR_LEYENDA + "NOTA")))
        assertEquals(listOf("Cuidado 1"), banco.textos(banco.conEtiqueta(ETIQUETA_EDITAR_LEYENDA + "CUIDADO")))
        assertEquals(listOf("Marca 1"), banco.textos(banco.conEtiqueta(ETIQUETA_EDITAR_LEYENDA + "MARCA")))
        assertEquals(listOf("Corte 2"), banco.textos(banco.conEtiqueta(ETIQUETA_EDITAR_LEYENDA + "CORTE")))
        assertTrue(banco.todosLosTextos().any { it.contains("«marca aquí»") })
        assertFalse(banco.todosLosTextos().any { it.startsWith("Marcas:") })
    }

    @Test
    fun `tocar una fila elige la nota y pide centrar, y el lapiz abre su editor`() {
        poner(estado())
        banco.pulsar(fila(segunda))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_EDITAR_NOTA_EDITAR + idMarcadorNota(tercera)))
        assertEquals(listOf("elegir:900:true", "editar:1500"), pedidas)
    }

    @Test
    fun `la nota elegida va marcada en la lista y en la escena del mapa`() {
        poner(estado(seleccionM = 900.0))
        assertEquals(true, fila(segunda).config.getOrNull(SemanticsProperties.Selected))
        assertEquals(false, fila(primera).config.getOrNull(SemanticsProperties.Selected))
        assertEquals(idMarcadorNota(segunda), escenas.last().seleccionId)
        assertNull(escenas.last().centrado)
    }

    @Test
    fun `una eleccion desde la lista centra el mapa en la nota`() {
        poner(estado(seleccionM = 900.0, centrarVersion = 3))
        assertEquals(3, checkNotNull(escenas.last().centrado).version)
    }

    @Test
    fun `una eleccion desde el mapa no mueve la camara aunque hubiera una anterior desde la lista`() {
        poner(estado(seleccionM = 900.0, centrarVersion = 3, centrar = false))
        assertNull(escenas.last().centrado)
    }

    @Test
    fun `tocar una nota en el mapa la elige y tocarla otra vez abre el editor`() {
        poner(estado())
        val marcador = escenas.last().marcadores.first { it.id == idMarcadorNota(primera) }
        gestos.onMarcador(marcador)
        assertEquals(listOf("elegir:300:false"), pedidas)

        pedidas.clear()
        poner(estado(seleccionM = 300.0))
        gestos.onMarcador(marcador)
        assertEquals(listOf("editar:300"), pedidas)
    }

    @Test
    fun `el trazo y los cortes del mapa llegan a sus callbacks`() {
        poner(estado())
        gestos.onPuntoTraza(1_100.0)
        gestos.onMoverCorte(1, 1_250.0)
        gestos.onSoltarCorte()
        assertEquals(listOf("punto:1100", "corte:1:1250", "soltar"), pedidas)
    }

    private val conAvisos =
        datos.copy(
            hoja =
                HojaNotas(
                    listOf(
                        ElementoHoja.DeNota(primera),
                        ElementoHoja.DeAviso(Aviso(500.0, TipoAviso.BADEN)),
                        ElementoHoja.DeNota(segunda),
                    ),
                ),
        )

    @Test
    fun `los avisos sueltos de la hoja se listan con su icono y se pueden quitar`() {
        poner(estado(tramoEditable = conAvisos))
        assertTrue("AVISOS (1)" in banco.todosLosTextos())
        val fila = banco.conEtiqueta(ETIQUETA_EDITAR_AVISO + "500")
        assertTrue("Cuidado, badén" in banco.textos(fila))
        val quitar = banco.conEtiqueta(ETIQUETA_EDITAR_AVISO_QUITAR + "500")
        val descripcion = "Quitar el aviso «Cuidado, badén», ${formatearKmDesdeM(500.0)}"
        assertTrue(banco.nodos().any { banco.descripcion(it) == descripcion })

        banco.pulsar(quitar)
        assertEquals(listOf("quitar-aviso:500"), pedidas)
    }

    @Test
    fun `sin avisos la lista lo dice`() {
        poner(estado())
        assertTrue("AVISOS (0)" in banco.todosLosTextos())
        assertTrue(banco.todosLosTextos().any { it.startsWith("Sin avisos") })
    }

    @Test
    fun `el editor de una nota ofrece fusionar salvo en la ultima, y dividir`() {
        poner(estado(editor = EditorDeTramo(primera.posicionM, primera)))
        assertTrue(banco.hay(ETIQUETA_EDITOR_FUSIONAR))
        assertTrue(banco.hay(ETIQUETA_EDITOR_DIVIDIR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_EDITOR_FUSIONAR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_EDITOR_DIVIDIR))
        assertEquals(listOf("fusionar", "dividir"), pedidas)
    }

    @Test
    fun `la ultima nota no se puede fusionar`() {
        poner(estado(editor = EditorDeTramo(tercera.posicionM, tercera)))
        assertFalse(banco.hay(ETIQUETA_EDITOR_FUSIONAR))
        assertTrue(banco.hay(ETIQUETA_EDITOR_DIVIDIR))
    }

    @Test
    fun `un punto sobre una curva ofrece dividir ahi y un punto libre solo anadir`() {
        poner(estado(editor = EditorDeTramo(320.0, null)))
        assertTrue(banco.hay(ETIQUETA_EDITOR_DIVIDIR))
        assertTrue(banco.hay(ETIQUETA_EDITOR_MODO_AVISO))
        assertTrue("Dividir aquí" in banco.todosLosTextos())
        assertFalse(banco.hay(ETIQUETA_EDITOR_FUSIONAR))
    }

    @Test
    fun `un punto libre ofrece anadir un aviso con iconos`() {
        poner(estado(editor = EditorDeTramo(600.0, null)))
        assertFalse(banco.hay(ETIQUETA_EDITOR_DIVIDIR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_EDITOR_MODO_AVISO))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_EDITOR_AVISO_PREFIJO + TipoAviso.PUENTE.name))
        assertEquals(listOf("aviso:PUENTE"), pedidas)
    }

    @Test
    fun `la ayuda es un icono que abre un dialogo`() {
        poner(estado())
        assertFalse(banco.hay(ETIQUETA_EDITAR_AYUDA_DIALOGO))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_EDITAR_AYUDA))
        assertTrue(banco.hay(ETIQUETA_EDITAR_AYUDA_DIALOGO))
        assertTrue(banco.textos(banco.conEtiqueta(ETIQUETA_EDITAR_AYUDA_DIALOGO)).single().contains("Arrastra"))
    }

    @Test
    fun `restaurar, volver y los microsectores siguen ahi`() {
        poner(estado())
        banco.pulsar(banco.conEtiqueta(ETIQUETA_EDITAR_RESTAURAR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertEquals(listOf("restaurar", "volver"), pedidas)
        assertTrue(banco.hay(ETIQUETA_EDITAR_MICRO_MAS))
    }

    @Test
    fun `sin mapa instalado avisa y la lista sigue sirviendo`() {
        poner(estado(estilo = null))
        assertTrue(banco.hay(ETIQUETA_EDITAR_SIN_MAPA))
        assertTrue(escenas.isEmpty())
        assertTrue(banco.hay(ETIQUETA_EDITAR_NOTA + idMarcadorNota(primera)))
    }

    @Test
    fun `mientras carga hay un indicador con texto`() {
        banco.poner { RallyCopilotTheme { EditarTramoScreen(EditarTramoUiState(), acciones, mapa = mapaFalso) } }
        assertTrue(banco.hay(ETIQUETA_EDITAR_CARGANDO))
        assertTrue(banco.todosLosTextos().contains("Cargando el tramo…"))
    }

    @Test
    fun `los botones tactiles miden al menos 48 dp`() {
        poner(estado())
        val minimo = 48 * 3f
        listOf(
            ETIQUETA_VOLVER,
            ETIQUETA_EDITAR_AYUDA,
            ETIQUETA_EDITAR_NOTA_EDITAR + idMarcadorNota(primera),
        ).forEach {
            val caja = banco.conEtiqueta(it).boundsInRoot
            assertTrue("$it mide ${caja.width} x ${caja.height}", caja.width >= minimo && caja.height >= minimo)
        }
    }
}
