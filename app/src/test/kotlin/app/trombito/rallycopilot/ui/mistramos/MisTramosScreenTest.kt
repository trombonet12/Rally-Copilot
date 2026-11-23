package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ETIQUETA_ESQUELETO
import app.trombito.rallycopilot.ui.comun.ETIQUETA_FONDO_WRC
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
            mejorMs = 700_000L,
            favorito = true,
            longitudM = 12_400.0,
        )
    private val valldemossa = resumenFalso("b", "Ma-10 · Valldemossa", pasadas = 1, mejorMs = null, longitudM = 850.0)

    private fun estado(vararg tramos: ResumenTramo) = MisTramosUiState(cargando = false, tramos = tramos.toList())

    @Test
    fun `cada fila muestra solo nombre, kilometros con velocidad media y tiempo`() {
        banco.poner { RallyCopilotTheme { MisTramosScreen(estado(escorca, valldemossa), AccionesMisTramos()) } }
        val fila = banco.textos(banco.conEtiqueta("$ETIQUETA_FILA_PREFIJO${escorca.id}"))
        assertTrue("Ma-2141 · Escorca" in fila)
        // 12,4 km en 11:40,0 son 63,8 km/h; ya no se cuentan las pasadas en la fila.
        assertTrue("12,4 km · 64 km/h" in fila)
        assertTrue("11:40,0" in fila)
        assertFalse(fila.any { "pasada" in it })
        // Sin ninguna pasada no hay velocidad: solo la distancia y un guion en lugar del tiempo.
        val otra = banco.textos(banco.conEtiqueta("$ETIQUETA_FILA_PREFIJO${valldemossa.id}"))
        assertTrue("850 m" in otra)
        assertTrue("—" in otra)
    }

    @Test
    fun `el tiempo pasa a segundo plano mientras solo hay una pasada`() {
        val una = resumenFalso("u", "Ma-10 · Deià", pasadas = 1, mejorMs = 600_000L, longitudM = 10_000.0)
        banco.poner { RallyCopilotTheme { MisTramosScreen(estado(escorca, una), AccionesMisTramos()) } }
        val variasPasadas = banco.conEtiqueta("$ETIQUETA_TIEMPO_PREFIJO${escorca.id}")
        val unaPasada = banco.conEtiqueta("$ETIQUETA_TIEMPO_PREFIJO${una.id}")
        assertEquals(listOf("10:00,0"), banco.textos(unaPasada))
        assertTrue(tamanoDeTexto(unaPasada).value < tamanoDeTexto(variasPasadas).value)
    }

    @Test
    fun `el nombre es Origen a Destino y las carreteras del nombre automatico ya no salen en la fila`() {
        val nuevo =
            resumenFalso("c", "Palma → Montuïri", longitudM = 21_000.0, mejorMs = 1_200_000L)
                .copy(subtitulo = "Ma-13 / Ma-15")
        banco.poner { RallyCopilotTheme { MisTramosScreen(estado(nuevo, escorca), AccionesMisTramos()) } }
        val fila = banco.textos(banco.conEtiqueta("$ETIQUETA_FILA_PREFIJO${nuevo.id}"))
        assertTrue("Palma → Montuïri" in fila)
        assertTrue("21,0 km · 63 km/h" in fila)
        assertFalse("Ma-13 / Ma-15" in fila)
    }

    @Test
    fun `no hay pestanas ni icono de Mis carreteras porque es otra entrada`() {
        banco.poner { RallyCopilotTheme { MisTramosScreen(estado(escorca), AccionesMisTramos()) } }
        assertTrue(banco.etiquetas("tramos-pestana-").isEmpty())
        assertFalse(banco.nodos().any { banco.descripcion(it) == "Mis carreteras" })
        assertTrue(banco.hay(ETIQUETA_FONDO_WRC))
    }

    @Test
    fun `un archivo de otra app pregunta antes de importar con su nombre y su longitud`() {
        val confirmados = mutableListOf<Int>()
        val cancelados = mutableListOf<Int>()
        banco.poner {
            RallyCopilotTheme {
                MisTramosScreen(
                    estado(escorca),
                    AccionesMisTramos(
                        onConfirmarImportacion = { confirmados += 1 },
                        onCancelarImportacion = { cancelados += 1 },
                    ),
                    importacion = EstadoImportacion.Pendiente("Mi ruta", 12_400.0),
                )
            }
        }
        assertEquals(
            listOf("¿Importar «Mi ruta» (12,4 km)?"),
            banco.textos(banco.conEtiqueta(ETIQUETA_IMPORTAR_DIALOGO)),
        )
        assertTrue(banco.todosLosTextos().any { "Te lo ha enviado otra app" in it })
        banco.pulsar(banco.conEtiqueta(ETIQUETA_IMPORTAR_CANCELAR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_IMPORTAR_ACEPTAR))
        assertEquals(1, cancelados.size)
        assertEquals(1, confirmados.size)
    }

    @Test
    fun `sin nombre el dialogo de importar dice este tramo`() {
        banco.poner {
            RallyCopilotTheme {
                MisTramosScreen(
                    estado(escorca),
                    AccionesMisTramos(),
                    importacion = EstadoImportacion.Pendiente(null, 850.0),
                )
            }
        }
        assertEquals(
            listOf("¿Importar este tramo (850 m)?"),
            banco.textos(banco.conEtiqueta(ETIQUETA_IMPORTAR_DIALOGO)),
        )
    }

    @Test
    fun `sin archivo pendiente no hay dialogo de importar`() {
        banco.poner { RallyCopilotTheme { MisTramosScreen(estado(escorca), AccionesMisTramos()) } }
        assertFalse(banco.hay(ETIQUETA_IMPORTAR_DIALOGO))
    }

    @Test
    fun `hay dos filtros, sin Repetidos, y el actual va seleccionado`() {
        banco.poner {
            RallyCopilotTheme {
                MisTramosScreen(estado(escorca).copy(filtro = FiltroTramos.FAVORITOS), AccionesMisTramos())
            }
        }
        assertEquals(
            listOf("tramos-filtro-todos", "tramos-filtro-favoritos"),
            banco.etiquetas(ETIQUETA_FILTRO_PREFIJO),
        )
        val seleccionado = banco.conEtiqueta("tramos-filtro-favoritos").config.getOrNull(SemanticsProperties.Selected)
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
        banco.pulsar(banco.conEtiqueta("tramos-filtro-todos"))
        assertEquals(listOf(FiltroTramos.FAVORITOS, FiltroTramos.TODOS), filtros)
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
        assertTrue("Aún no tienes viajes" in banco.todosLosTextos())
        filtro = FiltroTramos.FAVORITOS
        banco.reposo()
        assertTrue("Sin favoritos" in banco.todosLosTextos())
        assertTrue(banco.hay(ETIQUETA_TRAMOS_VACIO))
    }

    @Test
    fun `mientras carga se ven esqueletos y no la lista`() {
        banco.poner { RallyCopilotTheme { MisTramosScreen(MisTramosUiState(), AccionesMisTramos()) } }
        assertTrue(banco.hay(ETIQUETA_TRAMOS_CARGANDO))
        assertTrue(banco.hay(ETIQUETA_ESQUELETO))
        assertFalse(banco.hay(ETIQUETA_TRAMOS_LISTA))
    }

    @Test
    fun `los chips de filtro llevan la mayuscula inicial y ya no hay lapiz en la fila`() {
        banco.poner { RallyCopilotTheme { MisTramosScreen(estado(escorca), AccionesMisTramos()) } }
        val textos = banco.todosLosTextos()
        listOf("Todos", "Favoritos").forEach { assertTrue("Falta $it en $textos", it in textos) }
        assertFalse("Repetidos" in textos)
        assertFalse("TODOS" in textos)
        assertTrue(banco.etiquetas("tramo-renombrar-").isEmpty())
    }

    @Test
    fun `cada fila lleva su miniatura, con la silueta si ya se ha leido`() {
        val traza = listOf(PuntoGeo(39.80, 2.80), PuntoGeo(39.81, 2.82), PuntoGeo(39.83, 2.81))
        banco.poner {
            RallyCopilotTheme {
                MisTramosScreen(
                    estado(escorca, valldemossa).copy(trazas = mapOf(escorca.id to traza)),
                    AccionesMisTramos(),
                )
            }
        }
        // Una miniatura con silueta (Escorca) y un hueco con la bandera (Valldemossa, aún sin leer).
        assertEquals(1, banco.nodos().count { it.config.getOrNull(SemanticsProperties.TestTag) == ETIQUETA_MINIATURA })
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
    fun `si la base falla al borrar avisa de que el tramo sigue en la lista`() {
        banco.poner {
            RallyCopilotTheme {
                MisTramosScreen(
                    estado(escorca, valldemossa).copy(falloBorrado = "Ma-10 · Valldemossa"),
                    AccionesMisTramos(),
                )
            }
        }
        banco.esperar(300)
        val aviso = "No se pudo borrar «Ma-10 · Valldemossa». Sigue en tu lista; inténtalo de nuevo."
        assertTrue(aviso in banco.todosLosTextos())
        assertTrue(banco.hay("$ETIQUETA_FILA_PREFIJO${valldemossa.id}"))
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
        listOf("$ETIQUETA_FAVORITO_PREFIJO${escorca.id}").forEach {
            val tamano = banco.conEtiqueta(it).size
            assertTrue("$it mide $tamano", tamano.width >= minimo - 1 && tamano.height >= minimo - 1)
        }
    }

    private fun tamanoDeTexto(nodo: SemanticsNode): TextUnit {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados
            .first()
            .layoutInput.style.fontSize
    }

    private fun descripcionDeHijo(nodo: SemanticsNode): String? =
        nodo.children.firstNotNullOfOrNull { banco.descripcion(it) } ?: banco.descripcion(nodo)
}
