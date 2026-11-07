package app.trombito.rallycopilot.data.voz

import app.trombito.rallycopilot.domain.voz.VelocidadHabla
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Caminos de error del motor nativo con dobles: no se carga ninguna biblioteca nativa. */
@RunWith(RobolectricTestRunner::class)
class MotorTtsSherpaTest {
    @get:Rule val carpeta = TemporaryFolder()

    private class MotorFalso(
        private val audio: () -> AudioNativo = { AudioNativo(floatArrayOf(0f, 0.5f, -1f, 2f), FRECUENCIA) },
    ) : TtsNativo {
        var liberado = false

        override fun generar(
            texto: String,
            velocidad: Float,
        ): AudioNativo = audio()

        override fun liberar() {
            liberado = true
        }
    }

    private class CargadorFalso(
        private val alCargar: (Int) -> TtsNativo,
    ) : CargadorTts {
        var cargas = 0
        var ultimoEnDisco: Boolean? = null
        var ultimoModelo: String? = null

        override fun cargar(
            enDisco: Boolean,
            modelo: String,
            tokens: String,
            datos: String,
        ): TtsNativo {
            ultimoEnDisco = enDisco
            ultimoModelo = modelo
            return alCargar(++cargas)
        }
    }

    private fun motor(
        cargador: CargadorTts,
        datos: () -> File = { carpeta.root },
    ) = MotorTtsSherpa(VocesInstaladas(carpeta.root), cargador, datos)

    private fun sintetizar(
        motor: MotorTtsSherpa,
        voz: VozCopiloto = VozCopiloto.DAVEFX,
    ) = motor.sintetizar(voz, "curva a derechas", VelocidadHabla.NORMAL)

    @Test
    fun `convierte el audio nativo a PCM de 16 bits recortando los excesos`() {
        val audio = sintetizar(motor(CargadorFalso { MotorFalso() }))

        assertEquals(FRECUENCIA, audio.frecuenciaHz)
        assertEquals(listOf<Short>(0, 16_384, -32_767, 32_767), audio.muestras.toList())
    }

    @Test
    fun `una voz sharvard sin descargar da error de sintesis sin cargar el modelo`() {
        val cargador = CargadorFalso { MotorFalso() }

        val error = assertThrows(ErrorSintesis::class.java) { sintetizar(motor(cargador), VozCopiloto.SHARVARD) }

        assertTrue(error.message.orEmpty().contains("no está descargada"))
        assertEquals(0, cargador.cargas)
    }

    @Test
    fun `un modelo que no carga se informa como error de sintesis y se reintenta en la siguiente frase`() {
        val cargador =
            CargadorFalso { n -> if (n == 1) error("modelo corrupto") else MotorFalso() }
        val motor = motor(cargador)

        val fallo = assertThrows(ErrorSintesis::class.java) { sintetizar(motor) }
        val audio = sintetizar(motor)

        assertTrue(fallo.cause is IllegalStateException)
        assertEquals(2, cargador.cargas)
        assertEquals(4, audio.muestras.size)
    }

    @Test
    fun `sin la biblioteca nativa no revienta y se informa como error de sintesis`() {
        val cargador = CargadorFalso { throw UnsatisfiedLinkError("libsherpa") }

        val fallo = assertThrows(ErrorSintesis::class.java) { sintetizar(motor(cargador)) }

        assertTrue(fallo.cause is UnsatisfiedLinkError)
    }

    @Test
    fun `una excepcion del motor nativo al generar se informa como error de sintesis`() {
        val cargador = CargadorFalso { MotorFalso { throw IllegalArgumentException("onnx") } }

        val fallo = assertThrows(ErrorSintesis::class.java) { sintetizar(motor(cargador)) }

        assertEquals("Falló la síntesis", fallo.message)
    }

    @Test
    fun `una frecuencia distinta de la esperada se rechaza`() {
        val cargador = CargadorFalso { MotorFalso { AudioNativo(floatArrayOf(0f), 16_000) } }

        val fallo = assertThrows(ErrorSintesis::class.java) { sintetizar(motor(cargador)) }

        assertTrue(fallo.message.orEmpty().contains("16000"))
    }

    @Test
    fun `los datos de espeak que no se pueden preparar propagan el error sin cargar el modelo`() {
        val cargador = CargadorFalso { MotorFalso() }
        val motor = motor(cargador, datos = { throw ErrorSintesis("No se pudo preparar espeak-ng-data") })

        assertThrows(ErrorSintesis::class.java) { sintetizar(motor) }

        assertEquals(0, cargador.cargas)
    }

    @Test
    fun `tras liberar vuelve a cargar y libera el modelo anterior`() {
        val motores = mutableListOf<MotorFalso>()
        val cargador = CargadorFalso { MotorFalso().also(motores::add) }
        val motor = motor(cargador)

        sintetizar(motor)
        sintetizar(motor)
        assertEquals(1, cargador.cargas)
        motor.liberar()
        sintetizar(motor)

        assertEquals(2, cargador.cargas)
        assertTrue(motores[0].liberado)
        assertFalse(motores[1].liberado)
        motor.liberar()
        assertTrue(motores[1].liberado)
    }

    @Test
    fun `sharvard instalada se carga desde disco y al cambiar de voz se libera el modelo anterior`() {
        File(carpeta.root, NOMBRE_MODELO_SHARVARD).writeText("modelo")
        File(carpeta.root, NOMBRE_TOKENS_SHARVARD).writeText("tokens")
        val motores = mutableListOf<MotorFalso>()
        val cargador = CargadorFalso { MotorFalso().also(motores::add) }
        val motor = motor(cargador)

        sintetizar(motor, VozCopiloto.DAVEFX)
        assertEquals(false, cargador.ultimoEnDisco)
        sintetizar(motor, VozCopiloto.SHARVARD)

        assertEquals(true, cargador.ultimoEnDisco)
        assertTrue(cargador.ultimoModelo.orEmpty().endsWith(NOMBRE_MODELO_SHARVARD))
        assertTrue(motores[0].liberado)
        assertFalse(motores[1].liberado)
    }

    private companion object {
        const val FRECUENCIA = MotorTtsSherpa.FRECUENCIA_HZ
    }
}
