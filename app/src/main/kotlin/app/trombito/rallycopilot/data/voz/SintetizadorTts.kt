package app.trombito.rallycopilot.data.voz

import app.trombito.rallycopilot.domain.voz.VelocidadHabla
import app.trombito.rallycopilot.domain.voz.VozCopiloto

/** Audio PCM de 16 bits mono ya sintetizado, a [frecuenciaHz]. */
class AudioPcm(
    val muestras: ShortArray,
    val frecuenciaHz: Int,
)

/** La síntesis falló (modelo no cargable, texto que el modelo rechaza, error nativo). */
class ErrorSintesis(
    mensaje: String,
    causa: Throwable? = null,
) : RuntimeException(mensaje, causa)

/**
 * Motor de texto a voz. Se oculta tras una interfaz para probar la lógica sin código nativo (JNI).
 * Las llamadas bloquean: el [Locutor] las hace siempre desde su hilo de síntesis, de una en una.
 */
interface SintetizadorTts {
    /** Frecuencia del audio de [voz] (constante por voz, sin cargar el modelo). */
    fun frecuenciaHz(voz: VozCopiloto): Int

    /**
     * Sintetiza [texto] con [voz] a [velocidad] (la frecuencia no cambia: la frase solo dura menos); carga el modelo
     * si hace falta. Lanza [ErrorSintesis] si falla.
     */
    fun sintetizar(
        voz: VozCopiloto,
        texto: String,
        velocidad: VelocidadHabla,
    ): AudioPcm

    /** Libera el modelo cargado (el siguiente [sintetizar] lo vuelve a cargar). */
    fun liberar()
}
