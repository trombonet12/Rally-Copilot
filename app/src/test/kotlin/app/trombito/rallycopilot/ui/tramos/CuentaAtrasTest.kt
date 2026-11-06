package app.trombito.rallycopilot.ui.tramos

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
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

/** La cuenta atrás de la salida manual y el botón «SALIDA» que la abre. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CuentaAtrasTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val dichas = mutableListOf<String>()
    private var salidas = 0
    private var cerradas = 0
    private var crono by mutableStateOf<CronoEnCurso?>(null)

    private fun poner() {
        banco.poner { RallyCopilotTheme { CuentaAtras({ dichas += it }, { salidas++ }, { cerradas++ }) } }
    }

    private fun luzEncendida(n: Int) =
        banco.descripcion(banco.conEtiqueta(ETIQUETA_CUENTA_ATRAS_LUZ_PREFIJO + n)) == "Luz $n encendida"

    @Test
    fun `las luces se encienden una por segundo y el copiloto dice cada numero`() {
        poner()
        assertEquals(listOf("cinco"), dichas)
        assertTrue(luzEncendida(1))
        assertFalse(luzEncendida(2))
        assertEquals(listOf("5"), banco.textos(banco.conEtiqueta(ETIQUETA_CUENTA_ATRAS_NUMERO)))

        banco.esperar(PASO_CUENTA_ATRAS_MS)
        assertEquals(listOf("cinco", "cuatro"), dichas)
        assertTrue(luzEncendida(1) && luzEncendida(2))
        assertFalse(luzEncendida(3))
        assertEquals(listOf("4"), banco.textos(banco.conEtiqueta(ETIQUETA_CUENTA_ATRAS_NUMERO)))

        banco.esperar(3 * PASO_CUENTA_ATRAS_MS)
        assertEquals(listOf("cinco", "cuatro", "tres", "dos", "uno"), dichas)
        assertTrue((1..LUCES_CUENTA_ATRAS).all(::luzEncendida))
        assertEquals(0, salidas)
        assertEquals("Cuenta atrás: 1", descripcionPanel())
    }

    @Test
    fun `al final todas pasan a verde, dice ya, da la salida y se retira sola`() {
        poner()
        banco.esperar(LUCES_CUENTA_ATRAS * PASO_CUENTA_ATRAS_MS)

        assertEquals(listOf("cinco", "cuatro", "tres", "dos", "uno", "ya"), dichas)
        assertEquals(1, salidas)
        assertTrue(banco.hay(ETIQUETA_CUENTA_ATRAS_YA))
        assertEquals(listOf("¡YA!"), banco.textos(banco.conEtiqueta(ETIQUETA_CUENTA_ATRAS_YA)))
        assertEquals("Luces en verde", banco.descripcion(banco.conEtiqueta(ETIQUETA_CUENTA_ATRAS_LUZ_PREFIJO + 3)))
        assertEquals("¡Ya! Cronómetro en marcha", descripcionPanel())
        assertEquals(0, cerradas)

        banco.esperar(DURACION_YA_MS + 100)
        assertEquals(1, cerradas)
        assertEquals(1, salidas)
    }

    @Test
    fun `tocar fuera cancela sin dar la salida y la cuenta atras se detiene`() {
        poner()
        banco.esperar(2 * PASO_CUENTA_ATRAS_MS)
        val fondo = banco.conEtiqueta(ETIQUETA_CUENTA_ATRAS_FONDO)
        assertEquals("Cancelar la cuenta atrás", banco.descripcion(fondo))
        assertTrue(banco.esPulsable(fondo))

        banco.pulsar(fondo)

        assertEquals(1, cerradas)
        assertEquals(0, salidas)
    }

    private fun descripcionPanel(): String? =
        banco
            .nodos()
            .firstNotNullOfOrNull { n ->
                banco.descripcion(n)?.takeIf { it.startsWith("Cuenta atrás: ") || it.startsWith("¡Ya!") }
            }

    @Test
    fun `el boton SALIDA solo sale con salida manual pendiente, mide 56 dp y abre la cuenta atras`() {
        crono = cronoSinTramo(0L).copy(enMarcha = false, esperaSalidaManual = true)
        banco.poner {
            RallyCopilotTheme {
                CompositionLocalProvider(
                    LocalAccionesSalidaCrono provides AccionesSalidaCrono({ dichas += it }, { salidas++ }),
                ) { CronoMiniEnPanel(crono) }
            }
        }
        assertTrue(banco.hay(ETIQUETA_CRONO_SALIDA))
        val boton = banco.conEtiqueta(ETIQUETA_CRONO_SALIDA)
        assertEquals(listOf("SALIDA"), banco.textos(boton))
        assertTrue("alto ${boton.size.height}", boton.size.height >= 56 * 3)
        assertEquals(listOf("LISTO"), banco.textos(banco.conEtiqueta(ETIQUETA_CRONO_MINI)))
        assertFalse(banco.hay(ETIQUETA_CUENTA_ATRAS))

        banco.pulsar(boton)
        assertTrue(banco.hay(ETIQUETA_CUENTA_ATRAS))
        assertEquals(listOf("cinco"), dichas)

        banco.esperar(LUCES_CUENTA_ATRAS * PASO_CUENTA_ATRAS_MS)
        assertEquals(1, salidas)
        banco.esperar(DURACION_YA_MS + 100)
        assertFalse(banco.hay(ETIQUETA_CUENTA_ATRAS))
    }

    @Test
    fun `sin salida manual o ya en marcha no hay boton SALIDA`() {
        crono = cronoSinTramo(0L).copy(enMarcha = false, esperaSalidaManual = false)
        banco.poner {
            RallyCopilotTheme {
                CompositionLocalProvider(
                    LocalAccionesSalidaCrono provides AccionesSalidaCrono({}, {}),
                ) { CronoMiniEnPanel(crono) }
            }
        }
        assertFalse(banco.hay(ETIQUETA_CRONO_SALIDA))
        crono = cronoSinTramo(5_000L)
        banco.reposo()
        assertFalse(banco.hay(ETIQUETA_CRONO_SALIDA))
    }
}
