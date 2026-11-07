package app.trombito.rallycopilot.data.escucha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Caminos de error del micrófono con una [Grabadora] falsa: no se toca el framework de audio. */
@RunWith(RobolectricTestRunner::class)
class FabricaAudioRecordTest {
    private class GrabadoraFalsa(
        override val inicializada: Boolean = true,
        private val alIniciar: () -> Unit = {},
        private val alParar: () -> Unit = {},
    ) : Grabadora {
        val eventos = mutableListOf<String>()

        override fun iniciar() {
            alIniciar()
            eventos += "inicia"
        }

        override fun leer(buffer: ShortArray): Int = buffer.size

        override fun parar() {
            eventos += "para"
            alParar()
        }

        override fun liberar() {
            eventos += "libera"
        }
    }

    @Test
    fun `un AudioRecord sin inicializar se libera y no da fuente`() {
        val grabadora = GrabadoraFalsa(inicializada = false)

        assertNull(FabricaAudioRecord { grabadora }.abrir())
        assertEquals(listOf("libera"), grabadora.eventos)
    }

    @Test
    fun `sin permiso la creacion lanza SecurityException y no da fuente`() {
        assertNull(FabricaAudioRecord { throw SecurityException("RECORD_AUDIO") }.abrir())
    }

    @Test
    fun `un microfono ocupado lanza IllegalStateException al iniciar y no da fuente`() {
        val grabadora = GrabadoraFalsa(alIniciar = { error("ocupado") })

        assertNull(FabricaAudioRecord { grabadora }.abrir())
    }

    @Test
    fun `una grabadora lista se inicia y su fuente lee y se cierra parando y liberando`() {
        val grabadora = GrabadoraFalsa()

        val fuente = FabricaAudioRecord { grabadora }.abrir()

        assertNotNull(fuente)
        assertEquals(1_600, fuente?.leer(ShortArray(1_600)))
        fuente?.close()
        assertEquals(listOf("inicia", "para", "libera"), grabadora.eventos)
    }

    @Test
    fun `cerrar una fuente ya parada no revienta y libera igualmente`() {
        val grabadora = GrabadoraFalsa(alParar = { error("ya parada") })

        FabricaAudioRecord { grabadora }.abrir()?.close()

        assertEquals(listOf("inicia", "para", "libera"), grabadora.eventos)
    }
}
