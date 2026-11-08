package app.trombito.rallycopilot.data.audio

import android.util.Log
import app.trombito.rallycopilot.data.escucha.FabricaAudio
import app.trombito.rallycopilot.data.escucha.FuenteAudio

/**
 * Micrófono según la ruta: con un intercom de casco ([ruta]) graba por el micrófono del casco ([casco] crea la
 * grabación con la fuente de comunicación y el casco como dispositivo preferido); sin él, o si el casco no abre, por
 * el del [movil]. Si el casco se cae mientras se escucha, la fuente cambia sola al micrófono del móvil sin que quien
 * lee lo note (el reconocedor sigue recibiendo audio).
 */
class FabricaAudioRuta(
    private val ruta: RutaAudio,
    private val movil: FabricaAudio,
    private val casco: (ReservaAudio) -> FabricaAudio,
    /** Espera (en el hilo de grabación, que ya es de E/S) a que el canal del casco esté listo. */
    private val esperar: (Long) -> Unit = { ms -> Thread.sleep(ms) },
) : FabricaAudio {
    override fun abrir(): FuenteAudio? {
        val reserva = ruta.reservar(UsoRuta.ENTRADA)
        val fuente = if (reserva.dispositivo == null) null else abrirCasco(reserva)
        if (fuente != null) return FuenteConRuta(fuente, reserva, movil)
        reserva.close()
        return movil.abrir()
    }

    /** El micrófono del casco, o `null` si no abre o el casco se perdió mientras se esperaba al canal. */
    private fun abrirCasco(reserva: ReservaAudio): FuenteAudio? {
        val espera = reserva.msHastaListo()
        if (espera > 0) esperar(espera)
        val fuente = if (reserva.perdida) null else casco(reserva).abrir()
        if (fuente == null) Log.w(ETIQUETA, "El micrófono del casco no abre: se usa el del móvil")
        return fuente
    }

    private companion object {
        const val ETIQUETA = "AudioRuta"
    }
}

/** Fuente que empieza en el casco y pasa al micrófono del móvil si el casco se pierde o deja de dar audio. */
private class FuenteConRuta(
    private var actual: FuenteAudio,
    private var reserva: ReservaAudio?,
    private val movil: FabricaAudio,
) : FuenteAudio {
    override fun leer(buffer: ShortArray): Int {
        if (reserva?.perdida == true) pasarAlMovil()
        val n = actual.leer(buffer)
        if (n >= 0 || reserva == null) return n
        // El casco falla sin avisar: se intenta con el móvil antes de dar la escucha por rota.
        pasarAlMovil()
        return actual.leer(buffer)
    }

    private fun pasarAlMovil() {
        Log.i(ETIQUETA, "El casco se ha perdido: micrófono del móvil")
        actual.close()
        reserva?.close()
        reserva = null
        actual = movil.abrir() ?: FuenteVacia
    }

    override fun close() {
        actual.close()
        reserva?.close()
        reserva = null
    }

    private companion object {
        const val ETIQUETA = "AudioRuta"
    }
}

/** El micrófono del móvil tampoco abre: lecturas fallidas (quien escucha lo trata como micrófono roto). */
private object FuenteVacia : FuenteAudio {
    override fun leer(buffer: ShortArray): Int = -1

    override fun close() = Unit
}
