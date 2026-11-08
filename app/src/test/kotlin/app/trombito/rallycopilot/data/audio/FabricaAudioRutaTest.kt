package app.trombito.rallycopilot.data.audio

import app.trombito.rallycopilot.data.escucha.FabricaAudio
import app.trombito.rallycopilot.data.escucha.FuenteAudio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Micrófono de mentira: cada lectura devuelve [valor] (o -1 si [roto]) y se anota quién lo abrió y cerró. */
private class MicrofonoFalso(
    val nombre: String,
    val eventos: MutableList<String>,
    var valor: Int = 100,
) : FuenteAudio {
    var roto = false

    override fun leer(buffer: ShortArray): Int {
        eventos += "lee:$nombre"
        return if (roto) -1 else valor
    }

    override fun close() {
        eventos += "cierra:$nombre"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FabricaAudioRutaTest {
    private val ambito = TestScope()
    private val sistema = SistemaAudioFalso()
    private val eventos = mutableListOf<String>()
    private val intercom = DispositivoAudio(id = 7, tipo = TipoDispositivo.CASCO_SCO)
    private val esperas = mutableListOf<Long>()
    private val movil = MicrofonoFalso("movil", eventos)
    private val casco = MicrofonoFalso("casco", eventos)
    private var cascoAbre = true

    private fun ruta() =
        RutaAudioCasco(
            sistema = sistema,
            sdk = 31,
            permiso = { true },
            ambito = ambito,
            reloj = { ambito.testScheduler.currentTime },
        )

    private fun fabrica(ruta: RutaAudio = ruta()) =
        FabricaAudioRuta(
            ruta = ruta,
            movil =
                FabricaAudio {
                    eventos += "abre:movil"
                    movil
                },
            casco = {
                FabricaAudio {
                    eventos += "abre:casco"
                    if (cascoAbre) casco else null
                }
            },
            esperar = { esperas += it },
        )

    @Test
    fun `sin intercom el microfono es el del movil`() {
        val fuente = fabrica().abrir()

        assertNotNull(fuente)
        assertEquals(listOf("abre:movil"), eventos)
        assertTrue(esperas.isEmpty())
    }

    @Test
    fun `con intercom escucha por el microfono del casco tras esperar al canal`() {
        sistema.comunicacion = listOf(intercom)

        val fuente = fabrica().abrir()!!
        fuente.leer(ShortArray(10))

        assertEquals(listOf(RutaAudioCasco.ESPERA_SCO_MS), esperas)
        assertEquals(listOf("abre:casco", "lee:casco"), eventos)
    }

    @Test
    fun `si el microfono del casco no abre se usa el del movil y se libera el canal`() {
        sistema.comunicacion = listOf(intercom)
        cascoAbre = false

        val fuente = fabrica().abrir()

        assertNotNull(fuente)
        assertEquals(listOf("abre:casco", "abre:movil"), eventos)
    }

    @Test
    fun `si el casco se cae a media escucha la fuente pasa sola al movil y sigue dando audio`() {
        sistema.comunicacion = listOf(intercom)
        val fuente = fabrica().abrir()!!
        assertEquals(100, fuente.leer(ShortArray(10)))

        sistema.cambiar(comunicacion = emptyList())

        assertEquals(100, fuente.leer(ShortArray(10)))
        assertEquals(listOf("abre:casco", "lee:casco", "cierra:casco", "abre:movil", "lee:movil"), eventos)
    }

    @Test
    fun `si el casco deja de dar audio sin avisar se prueba el movil antes de dar el error`() {
        sistema.comunicacion = listOf(intercom)
        val fuente = fabrica().abrir()!!
        casco.roto = true

        assertEquals(100, fuente.leer(ShortArray(10)))
        assertEquals(listOf("abre:casco", "lee:casco", "cierra:casco", "abre:movil", "lee:movil"), eventos)
    }

    @Test
    fun `si ni el movil abre tras la caida las lecturas fallan sin lanzar`() {
        sistema.comunicacion = listOf(intercom)
        val ruta = ruta()
        val fuente =
            FabricaAudioRuta(
                ruta = ruta,
                movil = FabricaAudio { null },
                casco = { FabricaAudio { casco } },
                esperar = {},
            ).abrir()!!

        sistema.cambiar(comunicacion = emptyList())

        assertEquals(-1, fuente.leer(ShortArray(10)))
        fuente.close()
    }

    @Test
    fun `cerrar la fuente cierra el microfono y libera la reserva del canal`() {
        sistema.comunicacion = listOf(intercom)
        val fuente = fabrica().abrir()!!

        fuente.close()
        ambito.testScheduler.advanceTimeBy(RutaAudioCasco.LINGER_MS)
        ambito.testScheduler.runCurrent()

        assertEquals(listOf("abre:casco", "cierra:casco"), eventos)
        assertTrue("liberar" in sistema.eventos)
    }

    @Test
    fun `sin intercom no se pide ninguna espera ni se toca el audio del sistema`() {
        fabrica().abrir()?.close()

        assertTrue(sistema.eventos.isEmpty())
    }
}
