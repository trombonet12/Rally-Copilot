package app.trombito.rallycopilot.ui.inicio

import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
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
class InicioScreenTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    @Test
    fun `el menu muestra las cinco entradas del producto en orden`() {
        banco.poner { RallyCopilotTheme { InicioScreen() } }
        val esperadas = listOf("conducir", "mis_tramos", "mis_carreteras", "perfil", "ajustes")
        assertEquals(esperadas.map { "inicio-$it" }, banco.etiquetas("inicio-"))
        val textos = banco.textos(banco.raiz())
        listOf("CONDUCIR", "Mis tramos", "Mis carreteras", "Perfil del piloto", "Ajustes").forEach {
            assertTrue("Falta la entrada $it en $textos", it in textos)
        }
    }

    @Test
    fun `la cabecera muestra el nombre y el lema`() {
        banco.poner { RallyCopilotTheme { InicioScreen() } }
        val textos = banco.textos(banco.raiz())
        assertTrue("RALLY COPILOT" in textos)
        assertTrue("Tu copiloto en cada curva" in textos)
    }

    @Test
    fun `se puede desplazar en pantallas bajas`() {
        banco.poner { RallyCopilotTheme { InicioScreen() } }
        assertTrue(banco.hayDesplazable())
    }

    @Test
    fun `conducir, mis tramos, mis carreteras, perfil y ajustes son pulsables y navegan`() {
        val abiertas = mutableListOf<EntradaInicio>()
        banco.poner { RallyCopilotTheme { InicioScreen(onEntrada = { abiertas += it }) } }
        listOf("conducir", "mis_tramos", "mis_carreteras", "perfil", "ajustes").forEach {
            banco.pulsar(banco.conEtiqueta("inicio-$it"))
        }
        assertEquals(
            listOf(
                EntradaInicio.CONDUCIR,
                EntradaInicio.MIS_TRAMOS,
                EntradaInicio.MIS_CARRETERAS,
                EntradaInicio.PERFIL,
                EntradaInicio.AJUSTES,
            ),
            abiertas,
        )
    }

    @Test
    fun `todas las entradas son pulsables y miden lo pedido`() {
        banco.poner { RallyCopilotTheme { InicioScreen() } }
        val densidad = 3f
        EntradaInicio.entries.forEach {
            val nodo = banco.conEtiqueta("inicio-${it.name.lowercase()}")
            assertTrue("${it.name} debe ser pulsable", banco.esPulsable(nodo))
            val alto = if (it == EntradaInicio.CONDUCIR) 120 else 96
            assertEquals("${it.name} debe medir $alto dp", alto * densidad, nodo.size.height.toFloat(), 1f)
        }
    }

    @Test
    fun `sin salidas la tarjeta dice que el primer tramo saldra ahi y no tiene acciones`() {
        banco.poner { RallyCopilotTheme { InicioScreen() } }
        assertTrue(banco.hay(ETIQUETA_BIENVENIDA))
        assertFalse(banco.hay(ETIQUETA_ULTIMA_SALIDA))
        assertFalse(banco.hay(ETIQUETA_REPETIR_ULTIMO))
        assertTrue("Tu primer tramo saldrá aquí" in banco.textos(banco.conEtiqueta(ETIQUETA_BIENVENIDA)))
    }

    @Test
    fun `la ultima salida es una tarjeta con el tramo, el tiempo y el mejor con coma decimal`() {
        val salida = UltimaSalida("a", "Ma-2141 · Escorca", 257_300L, 252_300L)
        banco.poner { RallyCopilotTheme { InicioScreen(estado = InicioUiState(salida)) } }
        assertFalse(banco.hay(ETIQUETA_BIENVENIDA))
        val tarjeta = banco.conEtiqueta(ETIQUETA_ULTIMA_SALIDA)
        val textos = banco.textos(tarjeta)
        listOf("Ma-2141 · Escorca", "04:17,3", "Mejor 04:12,3").forEach {
            assertTrue("Falta $it en $textos", it in textos)
        }
        assertEquals("Última salida: Ma-2141 · Escorca · 04:17,3 · mejor 04:12,3", banco.descripcion(tarjeta))
    }

    @Test
    fun `tocar la ultima salida abre el tramo y el boton repite el ultimo tramo`() {
        val salida = UltimaSalida("tramo-7", "Ma-10 · Sóller", 100_000L, 90_000L)
        val abiertos = mutableListOf<String>()
        var repetidos = 0
        banco.poner {
            RallyCopilotTheme {
                InicioScreen(
                    estado = InicioUiState(salida),
                    onAbrirUltima = { abiertos += it },
                    onRepetirUltima = { repetidos++ },
                )
            }
        }
        val tarjeta = banco.conEtiqueta(ETIQUETA_ULTIMA_SALIDA)
        val boton = banco.conEtiqueta(ETIQUETA_REPETIR_ULTIMO)
        assertTrue(banco.esPulsable(tarjeta))
        assertTrue(banco.esPulsable(boton))
        assertTrue("Repetir último tramo" in banco.textos(boton))
        banco.pulsar(tarjeta)
        banco.pulsar(boton)
        assertEquals(listOf("tramo-7"), abiertos)
        assertEquals(1, repetidos)
        val densidad = 3f
        assertTrue("El botón debe medir al menos 48 dp", boton.size.height >= 48 * densidad - 1f)
        assertTrue("La tarjeta debe medir al menos 48 dp", tarjeta.size.height >= 48 * densidad - 1f)
        assertTrue(banco.pulsablesSinNombre().isEmpty())
    }
}
