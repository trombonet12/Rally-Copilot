package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.comun.resumenFalso
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class MisTramosScreenTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private val escorca =
        resumenFalso(
            "a",
            "Ma-2141 · Escorca",
            pasadas = 3,
            mejorMs = 252_300L,
            favorito = true,
            longitudM = 12_400.0,
        )
    private val valldemossa = resumenFalso("b", "Ma-10 · Valldemossa", pasadas = 1, mejorMs = null, longitudM = 850.0)

    private fun estado(vararg tramos: ResumenTramo) = MisTramosUiState(cargando = false, tramos = tramos.toList())

    @Test
    fun `cada fila muestra nombre, longitud, pasadas y mejor tiempo`() {
        banco.poner { RallyCopilotTheme { MisTramosScreen(estado(escorca, valldemossa), AccionesMisTramos()) } }
        val fila = banco.textos(banco.conEtiqueta("$ETIQUETA_FILA_PREFIJO${escorca.id}"))
        assertTrue("Ma-2141 · Escorca" in fila)
        assertTrue("12,4 km · 3 pasadas" in fila)
        assertTrue("04:12.3" in fila)
        val otra = banco.textos(banco.conEtiqueta("$ETIQUETA_FILA_PREFIJO${valldemossa.id}"))
        assertTrue("850 m · 1 pasada" in otra)
        assertTrue("—" in otra)
    }

    @Test
    fun `los tres filtros estan y el actual va seleccionado`() {
        banco.poner {
            RallyCopilotTheme {
                MisTramosScreen(estado(escorca).copy(filtro = FiltroTramos.REPETIDOS), AccionesMisTramos())
            }
        }
        assertEquals(
            listOf("tramos-filtro-todos", "tramos-filtro-repetidos", "tramos-filtro-favoritos"),
            banco.etiquetas(ETIQUETA_FILTRO_PREFIJO),
        )
        val seleccionado = banco.conEtiqueta("tramos-filtro-repetidos").config.getOrNull(SemanticsProperties.Selected)
        assertEquals(true, seleccionado)
        val otro = banco.conEtiqueta("tramos-filtro-todos").config.getOrNull(SemanticsProperties.Selected)
        assertEquals(false, otro)
    }

    @Test
    fun `tocar un filtro lo notifica`() {
        val filtros = mutableListOf<FiltroTramos>()
        banco.poner {
            RallyCopilotTheme { MisTramosScreen(estado(escorca), AccionesMisTramos(onFiltro = { filtros += it })) }
        }
        banco.pulsar(banco.conEtiqueta("tramos-filtro-favoritos"))
        banco.pulsar(banco.conEtiqueta("tramos-filtro-repetidos"))
        assertEquals(listOf(FiltroTramos.FAVORITOS, FiltroTramos.REPETIDOS), filtros)
    }

    @Test
    fun `la estrella alterna el favorito con su descripcion`() {
        val pulsados = mutableListOf<String>()
        banco.poner {
            RallyCopilotTheme {
                MisTramosScreen(estado(escorca, valldemossa), AccionesMisTramos(onFavorito = { pulsados += it.id }))
            }
        }
        val lleno = banco.conEtiqueta("$ETIQUETA_FAVORITO_PREFIJO${escorca.id}")
        val vacio = banco.conEtiqueta("$ETIQUETA_FAVORITO_PREFIJO${valldemossa.id}")
        assertEquals("Quitar de favoritos", descripcionDeHijo(lleno))
        assertEquals("Marcar como favorito", descripcionDeHijo(vacio))
        banco.pulsar(vacio)
        banco.pulsar(lleno)
        assertEquals(listOf("b", "a"), pulsados)
    }

    @Test
    fun `tocar una fila la abre`() {
        val abiertos = mutableListOf<String>()
        banco.poner {
            RallyCopilotTheme { MisTramosScreen(estado(escorca), AccionesMisTramos(onAbrir = { abiertos += it.id })) }
        }
        banco.pulsar(banco.conEtiqueta("$ETIQUETA_FILA_PREFIJO${escorca.id}"))
        assertEquals(listOf("a"), abiertos)
    }

    @Test
    fun `borrar y renombrar tambien estan como acciones de accesibilidad`() {
        val borrados = mutableListOf<String>()
        val renombrados = mutableListOf<String>()
        banco.poner {
            RallyCopilotTheme {
                MisTramosScreen(
                    estado(escorca),
                    AccionesMisTramos(onBorrar = { borrados += it.id }, onPedirRenombrar = { renombrados += it.id }),
                )
            }
        }
        val acciones = banco.conEtiqueta("$ETIQUETA_FILA_PREFIJO${escorca.id}").config[SemanticsActions.CustomActions]
        assertEquals(listOf("Borrar", "Cambiar el nombre"), acciones.map { it.label })
        acciones.forEach { it.action() }
        banco.reposo()
        assertEquals(listOf("a"), borrados)
        assertEquals(listOf("a"), renombrados)
    }

    @Test
    fun `el lapiz abre el renombrado`() {
        val renombrados = mutableListOf<String>()
        banco.poner {
            RallyCopilotTheme {
                MisTramosScreen(estado(escorca), AccionesMisTramos(onPedirRenombrar = { renombrados += it.id }))
            }
        }
        banco.pulsar(banco.conEtiqueta("$ETIQUETA_RENOMBRAR_PREFIJO${escorca.id}"))
        assertEquals(listOf("a"), renombrados)
    }

    @Test
    fun `estado vacio amable segun el filtro`() {
        var filtro by mutableStateOf(FiltroTramos.TODOS)
        banco.poner {
            RallyCopilotTheme {
                MisTramosScreen(
                    MisTramosUiState(cargando = false, filtro = filtro),
                    AccionesMisTramos(),
                )
            }
        }
        assertTrue("AÚN NO TIENES TRAMOS" in banco.todosLosTextos())
        filtro = FiltroTramos.REPETIDOS
        banco.reposo()
        assertTrue("NINGÚN TRAMO REPETIDO" in banco.todosLosTextos())
        filtro = FiltroTramos.FAVORITOS
        banco.reposo()
        assertTrue("SIN FAVORITOS" in banco.todosLosTextos())
        assertTrue(banco.hay(ETIQUETA_TRAMOS_VACIO))
    }

    @Test
    fun `mientras carga se ve el indicador y no la lista`() {
        banco.poner { RallyCopilotTheme { MisTramosScreen(MisTramosUiState(), AccionesMisTramos()) } }
        assertTrue(banco.hay(ETIQUETA_TRAMOS_CARGANDO))
        assertFalse(banco.hay(ETIQUETA_TRAMOS_LISTA))
    }

    @Test
    fun `el aviso de borrado ofrece deshacer`() {
        val deshechos = mutableListOf<Unit>()
        banco.poner {
            RallyCopilotTheme {
                MisTramosScreen(
                    estado(escorca).copy(borradoPendiente = BorradoPendiente("b", "Ma-10 · Valldemossa")),
                    AccionesMisTramos(onDeshacer = { deshechos += Unit }),
                )
            }
        }
        banco.esperar(300)
        assertTrue("«Ma-10 · Valldemossa» borrado" in banco.todosLosTextos())
        banco.pulsar(banco.pulsableConTexto("Deshacer"))
        assertEquals(1, deshechos.size)
    }

    @Test
    fun `el dialogo de renombrar entrega el texto escrito`() {
        val guardados = mutableListOf<String>()
        banco.poner {
            RallyCopilotTheme {
                MisTramosScreen(
                    estado(escorca).copy(renombrando = escorca),
                    AccionesMisTramos(onRenombrar = { guardados += it }),
                )
            }
        }
        val campo = banco.conEtiqueta(ETIQUETA_RENOMBRAR_CAMPO)
        campo.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("Mi curva"))
        banco.reposo()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_RENOMBRAR_GUARDAR))
        assertEquals(listOf("Mi curva"), guardados)
    }

    @Test
    fun `el dialogo parte con el nombre propio y no pisa uno que el usuario puso`() {
        val propio = escorca.copy(nombrePropio = "Mi tramo", nombre = "Mi tramo")
        banco.poner {
            RallyCopilotTheme { MisTramosScreen(estado(propio).copy(renombrando = propio), AccionesMisTramos()) }
        }
        assertEquals("Mi tramo", textoDelCampo())
    }

    @Test
    fun `el dialogo parte vacio si el tramo usa el nombre automatico`() {
        banco.poner {
            RallyCopilotTheme { MisTramosScreen(estado(escorca).copy(renombrando = escorca), AccionesMisTramos()) }
        }
        assertEquals("", textoDelCampo())
        assertTrue("Déjalo vacío para volver al nombre automático." in banco.todosLosTextos())
    }

    private fun textoDelCampo(): String? =
        banco
            .conEtiqueta(ETIQUETA_RENOMBRAR_CAMPO)
            .config
            .getOrNull(SemanticsProperties.EditableText)
            ?.text

    @Test
    fun `los botones tienen al menos 48 dp`() {
        banco.poner { RallyCopilotTheme { MisTramosScreen(estado(escorca), AccionesMisTramos()) } }
        val minimo = 48 * 2.625f
        listOf("$ETIQUETA_FAVORITO_PREFIJO${escorca.id}", "$ETIQUETA_RENOMBRAR_PREFIJO${escorca.id}").forEach {
            val tamano = banco.conEtiqueta(it).size
            assertTrue("$it mide $tamano", tamano.width >= minimo - 1 && tamano.height >= minimo - 1)
        }
    }

    private fun descripcionDeHijo(nodo: SemanticsNode): String? =
        nodo.children.firstNotNullOfOrNull { banco.descripcion(it) } ?: banco.descripcion(nodo)
}
