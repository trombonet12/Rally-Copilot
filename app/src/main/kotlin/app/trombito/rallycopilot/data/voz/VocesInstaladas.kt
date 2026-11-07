package app.trombito.rallycopilot.data.voz

import android.content.Context
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Nombres con los que la release de datos publica la voz Sharvard (ver `scripts/voz.py` de rally-copilot-data). */
const val NOMBRE_MODELO_SHARVARD = "voz-sharvard.onnx"
const val NOMBRE_TOKENS_SHARVARD = "voz-sharvard-tokens.txt"

/**
 * Qué voces se pueden usar ahora. Dave va dentro del APK (siempre disponible); Sharvard se baja de la release de datos
 * a `filesDir/mapa/` al elegirla, y hasta entonces (o si se borra) el copiloto habla con Dave: [efectiva].
 */
@Singleton
class VocesInstaladas internal constructor(
    private val directorioMapa: File,
) {
    @Inject
    constructor(
        @ApplicationContext contexto: Context,
    ) : this(File(contexto.filesDir, "mapa"))

    /** Modelo ONNX de Sharvard en disco (puede no existir). */
    val modeloSharvard: File get() = File(directorioMapa, NOMBRE_MODELO_SHARVARD)

    /** Tokens de Sharvard en disco (pueden no existir). */
    val tokensSharvard: File get() = File(directorioMapa, NOMBRE_TOKENS_SHARVARD)

    fun estaInstalada(voz: VozCopiloto): Boolean =
        when (voz) {
            VozCopiloto.DAVEFX -> true
            VozCopiloto.SHARVARD -> modeloSharvard.length() > 0 && tokensSharvard.length() > 0
        }

    /** La voz con la que se habla de verdad: [voz] si está instalada y, si no, Dave. */
    fun efectiva(voz: VozCopiloto): VozCopiloto = if (estaInstalada(voz)) voz else VozCopiloto.DAVEFX
}
