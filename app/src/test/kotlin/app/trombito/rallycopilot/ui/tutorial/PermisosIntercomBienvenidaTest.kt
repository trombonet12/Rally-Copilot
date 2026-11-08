package app.trombito.rallycopilot.ui.tutorial

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

private const val MS_PASO_PAGINA = 800L
private const val SDK_ANDROID_11 = 30
private const val SDK_ANDROID_12 = 31
private const val PAGINA_PERMISOS = 5

/** El permiso de Bluetooth del intercom en la bienvenida: solo desde Android 12 y sin bloquear si se deniega. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PermisosIntercomBienvenidaTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun lista(
        estado: EstadoPermiso,
        onPedir: (TipoPermiso) -> Unit = {},
    ) = TipoPermiso.entries.map { PermisoBienvenida(it, estado, onPedir = { onPedir(it) }, onAbrirAjustes = {}) }

    private fun poner(
        permisos: List<PermisoBienvenida>,
        onTerminar: () -> Unit = {},
    ) = banco.poner {
        RallyCopilotTheme { BienvenidaScreen(onTerminar, permisos, paginaInicial = PAGINA_PERMISOS) }
    }

    @Test
    fun `antes de Android 12 no se pide el intercom`() {
        assertFalse(TipoPermiso.INTERCOM in TipoPermiso.aplicables(SDK_ANDROID_11))
        assertEquals(
            listOf(TipoPermiso.UBICACION, TipoPermiso.MICROFONO, TipoPermiso.NOTIFICACIONES),
            TipoPermiso.aplicables(SDK_ANDROID_11),
        )
    }

    @Test
    fun `desde Android 12 se pide el intercom con los demas`() {
        assertTrue(TipoPermiso.INTERCOM in TipoPermiso.aplicables(SDK_ANDROID_12))
        assertEquals(TipoPermiso.entries, TipoPermiso.aplicables(SDK_ANDROID_12))
    }

    @Test
    fun `la pagina de permisos explica el intercom y su boton lo pide`() {
        val pedidos = mutableListOf<TipoPermiso>()
        poner(lista(EstadoPermiso.PENDIENTE) { pedidos += it })

        val textos = banco.todosLosTextos()
        assertTrue(textos.toString(), "Intercom del casco" in textos)
        assertTrue(textos.toString(), "Para oír al copiloto y hablarle por el intercom del casco." in textos)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_PERMISO_BOTON_PREFIJO + TipoPermiso.INTERCOM.clave))

        assertEquals(listOf(TipoPermiso.INTERCOM), pedidos)
    }

    @Test
    fun `denegar el intercom no bloquea la bienvenida y avisa de que sigue por el altavoz`() {
        var terminada = 0
        poner(lista(EstadoPermiso.DENEGADO), onTerminar = { terminada++ })

        val textos = banco.todosLosTextos()
        assertTrue(
            textos.toString(),
            "Sin este permiso la voz sale por el altavoz del móvil y no por el casco." in textos,
        )
        banco.pulsar(banco.conEtiqueta(ETIQUETA_BIENVENIDA_SIGUIENTE))
        banco.esperar(MS_PASO_PAGINA)

        assertEquals(1, terminada)
    }
}
