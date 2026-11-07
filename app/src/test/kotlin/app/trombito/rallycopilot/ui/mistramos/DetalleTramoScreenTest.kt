package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.comun.resumenFalso
import app.trombito.rallycopilot.ui.comun.tramoFalso
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
class DetalleTramoScreenTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private val tramo =
        resumenFalso("t1", "Ma-2141 · Escorca", pasadas = 3, mejorMs = 252_300L, longitudM = 12_400.0, favorito = true)
    private val pasadas =
        listOf(
            FilaPasada(3, 1_762_000_000_000L, 257_300L, 5_000L, esMejor = false),
            FilaPasada(2, 1_761_000_000_000L, 252_300L, 0L, esMejor = true),
            FilaPasada(1, 1_760_000_000_000L, 260_500L, 8_200L, esMejor = false),
        )
    private val estado =
        DetalleTramoUiState(cargando = false, tramo = tramo, traza = tramoFalso("t1").traza, pasadas = pasadas)

    @Test
    fun `muestra el nombre, las medidas y el trazado`() {
        banco.poner { RallyCopilotTheme { DetalleTramoScreen(estado, AccionesDetalleTramo()) } }
        val textos = banco.todosLosTextos()
        assertTrue("Ma-2141 · Escorca" in textos)
        assertTrue("12,4 km" in textos)
        assertTrue("3" in textos)
        assertTrue("Pasadas" in textos)
        assertFalse("3 pasadas" in textos)
        assertTrue("04:12,3" in textos)
        assertTrue(banco.hay(ETIQUETA_TRAZA))
        assertTrue(banco.descripcion(banco.conEtiqueta(ETIQUETA_TRAZA))!!.contains("salida"))
    }

    @Test
    fun `con datos de mapa la traza sale sobre el mapa y sin ellos, en el lienzo`() {
        val dibujadas = mutableListOf<Int>()
        val mapaFalso: MapaDelTramo = { _, traza, modificador ->
            dibujadas += traza.size
            androidx.compose.foundation.layout
                .Box(modificador)
        }
        banco.poner {
            RallyCopilotTheme {
                DetalleTramoScreen(estado.copy(estiloMapaJson = "{}"), AccionesDetalleTramo(), mapa = mapaFalso)
            }
        }
        assertTrue(banco.hay(ETIQUETA_MAPA_TRAMO))
        assertFalse(banco.hay(ETIQUETA_TRAZA))
        assertEquals(listOf(estado.traza.size), dibujadas.distinct())
    }

    @Test
    fun `sin estilo de mapa instalado la traza se dibuja en el lienzo`() {
        val mapaFalso: MapaDelTramo = { _, _, modificador ->
            androidx.compose.foundation.layout
                .Box(modificador)
        }
        banco.poner { RallyCopilotTheme { DetalleTramoScreen(estado, AccionesDetalleTramo(), mapa = mapaFalso) } }
        assertTrue(banco.hay(ETIQUETA_TRAZA))
        assertFalse(banco.hay(ETIQUETA_MAPA_TRAMO))
    }

    @Test
    fun `cada pasada trae fecha, tiempo y su diferencia, y la mejor va marcada`() {
        banco.poner { RallyCopilotTheme { DetalleTramoScreen(estado, AccionesDetalleTramo()) } }
        val peor = banco.textos(banco.conEtiqueta("${ETIQUETA_PASADA_PREFIJO}3"))
        assertTrue("04:17,3" in peor)
        assertTrue("+5,0" in peor)
        assertFalse("Mejor" in peor)
        val mejor = banco.textos(banco.conEtiqueta("${ETIQUETA_PASADA_PREFIJO}2"))
        assertTrue("04:12,3" in mejor)
        assertTrue("Mejor" in mejor)
        assertFalse(mejor.any { it.startsWith("+") })
        assertTrue(peor.any { Regex("""\d+ \w{3} \d{4} · \d{2}:\d{2}""").matches(it) })
    }

    @Test
    fun `ver analisis entrega el numero de la pasada`() {
        val pedidas = mutableListOf<Long>()
        banco.poner {
            RallyCopilotTheme { DetalleTramoScreen(estado, AccionesDetalleTramo(onVerAnalisis = { pedidas += it })) }
        }
        // La fila entera es pulsable, no solo un botón.
        banco.pulsar(banco.conEtiqueta("${ETIQUETA_PASADA_PREFIJO}2"))
        banco.pulsar(banco.conEtiqueta("${ETIQUETA_PASADA_PREFIJO}1"))
        assertEquals(listOf(2L, 1L), pedidas)
        assertTrue(banco.esPulsable(banco.conEtiqueta("${ETIQUETA_PASADA_PREFIJO}3")))
        assertTrue(banco.conEtiqueta("${ETIQUETA_PASADA_PREFIJO}3").size.height >= 56 * 3f - 1)
        assertTrue(banco.hay("${ETIQUETA_ANALISIS_PREFIJO}3"))
    }

    @Test
    fun `reconocer esta junto a editar notas, lo pide y se explica`() {
        var reconocer = 0
        var editar = 0
        banco.poner {
            RallyCopilotTheme {
                DetalleTramoScreen(
                    estado,
                    AccionesDetalleTramo(onReconocer = { reconocer++ }, onEditarNotas = { editar++ }),
                )
            }
        }
        val reconocerNodo = banco.conEtiqueta(ETIQUETA_DETALLE_RECONOCER)
        val editarNodo = banco.conEtiqueta(ETIQUETA_DETALLE_EDITAR)
        assertTrue("Reconocer" in banco.textos(reconocerNodo))
        assertTrue("Editar notas" in banco.textos(editarNodo))
        assertTrue("misma fila", reconocerNodo.boundsInRoot.top == editarNodo.boundsInRoot.top)
        assertTrue("a la izquierda", reconocerNodo.boundsInRoot.right <= editarNodo.boundsInRoot.left)
        assertTrue(reconocerNodo.size.height >= 48 * 3)
        banco.pulsar(reconocerNodo)
        banco.pulsar(editarNodo)
        assertEquals(1, reconocer)
        assertEquals(1, editar)
        // La explicación de Reconocer no ocupa pantalla: sale al tocar el icono de información.
        val ayuda = {
            banco.todosLosTextos().any {
                it.startsWith(
                    "Reconocer: el copiloto lee las notas de este tramo",
                )
            }
        }
        assertFalse(ayuda())
        banco.pulsar(banco.conEtiqueta(ETIQUETA_DETALLE_AYUDA))
        assertTrue(ayuda())
    }

    @Test
    fun `favorito y renombrar estan en la cabecera`() {
        var favoritos = 0
        var renombrar = 0
        banco.poner {
            RallyCopilotTheme {
                DetalleTramoScreen(
                    estado,
                    AccionesDetalleTramo(onFavorito = { favoritos++ }, onPedirRenombrar = { renombrar++ }),
                )
            }
        }
        banco.pulsar(banco.conEtiqueta(ETIQUETA_DETALLE_FAVORITO))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_DETALLE_RENOMBRAR))
        assertEquals(1, favoritos)
        assertEquals(1, renombrar)
    }

    @Test
    fun `compartir es un icono con descripcion en la cabecera y se deshabilita mientras prepara el archivo`() {
        var compartir = 0
        var estadoActual by mutableStateOf(estado)
        banco.poner {
            RallyCopilotTheme { DetalleTramoScreen(estadoActual, AccionesDetalleTramo(onCompartir = { compartir++ })) }
        }
        val boton = banco.conEtiqueta(ETIQUETA_DETALLE_COMPARTIR)
        val descripcion = boton.children.firstNotNullOfOrNull { banco.descripcion(it) } ?: banco.descripcion(boton)
        assertEquals("Compartir el tramo con sus notas", descripcion)
        banco.pulsar(boton)
        assertEquals(1, compartir)

        estadoActual = estado.copy(compartir = EstadoCompartir.PREPARANDO)
        banco.reposo()
        assertFalse(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_DETALLE_COMPARTIR)))
    }

    @Test
    fun `un fallo al preparar el archivo se avisa en un snackbar`() {
        banco.poner {
            RallyCopilotTheme {
                DetalleTramoScreen(estado.copy(compartir = EstadoCompartir.FALLO), AccionesDetalleTramo())
            }
        }
        banco.esperar(300)
        assertTrue("No se pudo preparar el archivo del tramo. Inténtalo de nuevo." in banco.todosLosTextos())
    }

    @Test
    fun `las carreteras del nombre automatico salen de subtitulo bajo el titulo`() {
        val conCarreteras = tramo.copy(nombre = "Palma → Valldemossa", subtitulo = "Ma-1110 / Ma-10")
        banco.poner {
            RallyCopilotTheme { DetalleTramoScreen(estado.copy(tramo = conCarreteras), AccionesDetalleTramo()) }
        }
        assertEquals(listOf("Palma → Valldemossa"), banco.textos(banco.conEtiqueta(ETIQUETA_DETALLE_TITULO)))
        assertEquals(listOf("Ma-1110 / Ma-10"), banco.textos(banco.conEtiqueta(ETIQUETA_DETALLE_SUBTITULO)))
    }

    @Test
    fun `sin carreteras no hay subtitulo`() {
        banco.poner { RallyCopilotTheme { DetalleTramoScreen(estado, AccionesDetalleTramo()) } }
        assertFalse(banco.hay(ETIQUETA_DETALLE_SUBTITULO))
    }

    @Test
    fun `el dialogo de renombrar sale con el estado`() {
        banco.poner {
            RallyCopilotTheme { DetalleTramoScreen(estado.copy(renombrando = true), AccionesDetalleTramo()) }
        }
        assertTrue("Nombre del tramo" in banco.todosLosTextos())
        assertTrue(banco.hay(ETIQUETA_RENOMBRAR_CAMPO))
    }

    @Test
    fun `un tramo sin pasadas lo dice`() {
        banco.poner {
            RallyCopilotTheme { DetalleTramoScreen(estado.copy(pasadas = emptyList()), AccionesDetalleTramo()) }
        }
        assertTrue(banco.hay(ETIQUETA_PASADAS_VACIAS))
    }

    @Test
    fun `mientras carga se ve el indicador`() {
        banco.poner { RallyCopilotTheme { DetalleTramoScreen(DetalleTramoUiState(), AccionesDetalleTramo()) } }
        assertTrue(banco.hay(ETIQUETA_DETALLE_CARGANDO))
    }

    @Test
    fun `un tramo que no existe tiene su estado vacio`() {
        banco.poner {
            RallyCopilotTheme {
                DetalleTramoScreen(DetalleTramoUiState(cargando = false), AccionesDetalleTramo())
            }
        }
        assertTrue(banco.hay(ETIQUETA_DETALLE_NO_ENCONTRADO))
        assertTrue("Tramo no encontrado" in banco.todosLosTextos())
    }

    @Test
    fun `el titulo es un encabezado para TalkBack`() {
        banco.poner { RallyCopilotTheme { DetalleTramoScreen(estado, AccionesDetalleTramo()) } }
        val titulo = banco.conEtiqueta(ETIQUETA_DETALLE_TITULO)
        assertTrue(titulo.config.getOrNull(SemanticsProperties.Heading) != null)
    }
}
