package app.trombito.rallycopilot.data.audio

import android.media.AudioDeviceInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [SistemaAudio] de mentira: el estado de los dispositivos se cambia a mano y cada orden queda anotada. */
internal class SistemaAudioFalso : SistemaAudio {
    var comunicacion = emptyList<DispositivoAudio>()
    var salidas = emptyList<DispositivoAudio>()
    var scoPermitido = true
    var aceptaComunicacion = true
    val eventos = mutableListOf<String>()
    private var aviso: (() -> Unit)? = null

    /** Simula una alta o baja de dispositivos: el sistema avisa tras cambiar las listas. */
    fun cambiar(
        comunicacion: List<DispositivoAudio> = this.comunicacion,
        salidas: List<DispositivoAudio> = this.salidas,
    ) {
        this.comunicacion = comunicacion
        this.salidas = salidas
        aviso?.invoke()
    }

    override fun dispositivosComunicacion() = comunicacion

    override fun salidasConectadas() = salidas

    override fun scoDisponible() = scoPermitido

    override fun fijarComunicacion(dispositivo: DispositivoAudio): Boolean {
        eventos += "fijar:${dispositivo.id}"
        return aceptaComunicacion
    }

    override fun liberarComunicacion() {
        eventos += "liberar"
    }

    override fun fijarSco(activo: Boolean) {
        eventos += if (activo) "sco-on" else "sco-off"
    }

    override fun fijarModoComunicacion(activo: Boolean) {
        eventos += "modo:$activo"
    }

    override fun escucharCambios(alCambiar: () -> Unit): AutoCloseable {
        aviso = alCambiar
        return AutoCloseable { aviso = null }
    }

    override fun infoSalida(id: Int): AudioDeviceInfo? = null

    override fun infoEntrada(id: Int): AudioDeviceInfo? = null
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RutaAudioCascoTest {
    private val ambito = TestScope()
    private val sistema = SistemaAudioFalso()
    private var permiso = true
    private var activado = true

    private val intercom = DispositivoAudio(id = 7, tipo = TipoDispositivo.CASCO_SCO)
    private val altavoz = DispositivoAudio(id = 2, tipo = TipoDispositivo.OTRO)

    private fun ruta(sdk: Int) =
        RutaAudioCasco(
            sistema = sistema,
            sdk = sdk,
            permiso = { permiso },
            ambito = ambito,
            reloj = { ambito.testScheduler.currentTime },
            activado = { activado },
        )

    @Test
    fun `sin intercom todo va por el altavoz y no se abre nada`() {
        sistema.comunicacion = listOf(altavoz)
        val ruta = ruta(API_31)

        val reserva = ruta.reservar(UsoRuta.SALIDA)

        assertEquals(ModoAudio.ALTAVOZ, ruta.modo.value)
        assertNull(reserva.dispositivo)
        assertTrue(sistema.eventos.isEmpty())
    }

    @Test
    fun `desde la API 31 un SCO de comunicacion es modo casco y se abre con setCommunicationDevice`() {
        sistema.comunicacion = listOf(altavoz, intercom)
        val ruta = ruta(API_31)

        val reserva = ruta.reservar(UsoRuta.SALIDA)

        assertEquals(ModoAudio.CASCO, ruta.modo.value)
        assertEquals(intercom, reserva.dispositivo)
        assertEquals(listOf("modo:true", "fijar:7"), sistema.eventos)
    }

    @Test
    fun `un BLE headset tambien vale pero el SCO tiene preferencia`() {
        val ble = DispositivoAudio(id = 9, tipo = TipoDispositivo.CASCO_BLE)
        sistema.comunicacion = listOf(ble)
        assertEquals(ble, ruta(API_31).reservar(UsoRuta.SALIDA).dispositivo)

        sistema.eventos.clear()
        sistema.comunicacion = listOf(ble, intercom)
        assertEquals(intercom, ruta(API_31).reservar(UsoRuta.SALIDA).dispositivo)
    }

    @Test
    fun `antes de la API 31 se usa startBluetoothSco y no setCommunicationDevice`() {
        sistema.salidas = listOf(altavoz, intercom)
        sistema.comunicacion = listOf(intercom) // en estas APIs no se consulta
        val ruta = ruta(API_30)

        val reserva = ruta.reservar(UsoRuta.SALIDA)

        assertEquals(intercom, reserva.dispositivo)
        assertEquals(listOf("modo:true", "sco-on"), sistema.eventos)
    }

    @Test
    fun `antes de la API 31 un A2DP cuenta como casco solo si el movil admite SCO`() {
        val a2dp = DispositivoAudio(id = 4, tipo = TipoDispositivo.A2DP)
        sistema.salidas = listOf(a2dp)
        sistema.scoPermitido = false
        assertNull(ruta(API_30).reservar(UsoRuta.SALIDA).dispositivo)

        sistema.scoPermitido = true
        assertEquals(a2dp, ruta(API_30).reservar(UsoRuta.SALIDA).dispositivo)
    }

    @Test
    fun `desde la API 31 un A2DP suelto no es casco`() {
        sistema.comunicacion = listOf(DispositivoAudio(id = 4, tipo = TipoDispositivo.A2DP))

        assertNull(ruta(API_31).reservar(UsoRuta.SALIDA).dispositivo)
    }

    @Test
    fun `sin permiso BLUETOOTH_CONNECT va por el altavoz y al concederlo pasa al casco`() {
        sistema.comunicacion = listOf(intercom)
        permiso = false
        val ruta = ruta(API_31)

        assertNull(ruta.reservar(UsoRuta.SALIDA).dispositivo)
        assertEquals(ModoAudio.ALTAVOZ, ruta.modo.value)
        assertTrue(sistema.eventos.isEmpty())

        permiso = true
        assertEquals(intercom, ruta.reservar(UsoRuta.SALIDA).dispositivo)
        assertEquals(ModoAudio.CASCO, ruta.modo.value)
    }

    @Test
    fun `con el ajuste apagado va por el altavoz aunque haya intercom`() {
        sistema.comunicacion = listOf(intercom)
        activado = false

        assertNull(ruta(API_31).reservar(UsoRuta.SALIDA).dispositivo)
    }

    @Test
    fun `si el sistema rechaza abrir el canal va por el altavoz y restaura el modo de audio`() {
        sistema.comunicacion = listOf(intercom)
        sistema.aceptaComunicacion = false

        val reserva = ruta(API_31).reservar(UsoRuta.SALIDA)

        assertNull(reserva.dispositivo)
        assertEquals(listOf("modo:true", "fijar:7", "modo:false"), sistema.eventos)
    }

    @Test
    fun `un intercom que se conecta en caliente cambia el modo sin reiniciar`() {
        val ruta = ruta(API_31)
        assertEquals(ModoAudio.ALTAVOZ, ruta.modo.value)

        sistema.cambiar(comunicacion = listOf(intercom))
        assertEquals(ModoAudio.CASCO, ruta.modo.value)

        sistema.cambiar(comunicacion = emptyList())
        assertEquals(ModoAudio.ALTAVOZ, ruta.modo.value)
    }

    @Test
    fun `el casco que se cae con una reserva viva cierra el canal y la deja perdida`() {
        sistema.comunicacion = listOf(intercom)
        val ruta = ruta(API_31)
        val reserva = ruta.reservar(UsoRuta.SALIDA)
        var avisada = false
        ambito.launch {
            reserva.esperarCaida()
            avisada = true
        }
        ambito.runCurrent()
        assertFalse(reserva.perdida)
        assertFalse(avisada)

        sistema.cambiar(comunicacion = emptyList())
        ambito.runCurrent()

        assertTrue(reserva.perdida)
        assertTrue(avisada)
        assertEquals(ModoAudio.ALTAVOZ, ruta.modo.value)
        assertEquals(listOf("modo:true", "fijar:7", "liberar", "modo:false"), sistema.eventos)
        // Una reserva nueva ya va por el altavoz y cerrar la perdida no toca nada.
        assertNull(ruta.reservar(UsoRuta.SALIDA).dispositivo)
        reserva.close()
        assertEquals(4, sistema.eventos.size)
    }

    @Test
    fun `cambiar de intercom en mitad de un uso tambien lo da por perdido`() {
        sistema.comunicacion = listOf(intercom)
        val ruta = ruta(API_31)
        val reserva = ruta.reservar(UsoRuta.SALIDA)

        sistema.cambiar(comunicacion = listOf(DispositivoAudio(id = 8, tipo = TipoDispositivo.CASCO_SCO)))

        assertTrue(reserva.perdida)
        assertEquals(8, ruta.reservar(UsoRuta.SALIDA).dispositivo?.id)
    }

    @Test
    fun `el canal se mantiene cinco segundos tras el ultimo uso y se cierra`() {
        sistema.comunicacion = listOf(intercom)
        val ruta = ruta(API_31)
        ruta.reservar(UsoRuta.SALIDA).close()

        ambito.advanceTimeBy(RutaAudioCasco.LINGER_MS - 1)
        ambito.runCurrent()
        assertFalse("liberar" in sistema.eventos)

        ambito.advanceTimeBy(1)
        ambito.runCurrent()
        assertEquals(listOf("modo:true", "fijar:7", "liberar", "modo:false"), sistema.eventos)
    }

    @Test
    fun `un uso dentro de ese plazo reutiliza el canal sin reabrirlo ni esperar`() {
        sistema.comunicacion = listOf(intercom)
        val ruta = ruta(API_31)
        ruta.reservar(UsoRuta.SALIDA).close()
        ambito.advanceTimeBy(RutaAudioCasco.LINGER_MS / 2)
        ambito.runCurrent()

        val segunda = ruta.reservar(UsoRuta.ENTRADA)
        ambito.advanceTimeBy(RutaAudioCasco.LINGER_MS * 2)
        ambito.runCurrent()

        assertEquals(0L, segunda.msHastaListo())
        assertEquals(listOf("modo:true", "fijar:7"), sistema.eventos) // segunda sigue viva: no se cierra
        segunda.close()
    }

    @Test
    fun `el canal no se cierra mientras quede un uso vivo`() {
        sistema.comunicacion = listOf(intercom)
        val ruta = ruta(API_31)
        val voz = ruta.reservar(UsoRuta.SALIDA)
        val microfono = ruta.reservar(UsoRuta.ENTRADA)

        voz.close()
        ambito.advanceTimeBy(RutaAudioCasco.LINGER_MS * 2)
        ambito.runCurrent()
        assertFalse("liberar" in sistema.eventos)

        microfono.close()
        microfono.close() // cerrar dos veces no descuenta dos usos
        ambito.advanceTimeBy(RutaAudioCasco.LINGER_MS)
        ambito.runCurrent()
        assertEquals(1, sistema.eventos.count { it == "liberar" })
    }

    @Test
    fun `antes de la API 31 el canal se cierra con stopBluetoothSco`() {
        sistema.salidas = listOf(intercom)
        val ruta = ruta(API_30)
        ruta.reservar(UsoRuta.SALIDA).close()

        ambito.advanceTimeBy(RutaAudioCasco.LINGER_MS)
        ambito.runCurrent()

        assertEquals(listOf("modo:true", "sco-on", "sco-off", "modo:false"), sistema.eventos)
    }

    @Test
    fun `el canal recien abierto avisa de cuanto falta y despues queda listo`() {
        sistema.comunicacion = listOf(intercom)
        val reserva = ruta(API_31).reservar(UsoRuta.SALIDA)

        assertEquals(RutaAudioCasco.ESPERA_SCO_MS, reserva.msHastaListo())

        ambito.advanceTimeBy(RutaAudioCasco.ESPERA_SCO_MS)
        assertEquals(0L, reserva.msHastaListo())
    }

    private companion object {
        const val API_30 = 30
        const val API_31 = 31
    }
}
