package app.trombito.rallycopilot.ui.tramos

import android.content.res.Resources
import app.trombito.rallycopilot.R
import java.util.Locale

private const val MS_DECIMA = 100L
private const val DECIMAS_SEGUNDO = 10L
private const val DECIMAS_MINUTO = 600L
private val ES = Locale.forLanguageTag("es-ES")

/** Tiempo dicho en voz alta: «2 minutos 30,4 segundos» (TalkBack lee las cifras en español). */
internal fun descripcionTiempo(
    recursos: Resources,
    ms: Long,
): String {
    val d = ms.coerceAtLeast(0L) / MS_DECIMA
    return descripcionDecimas(recursos, d)
}

private fun descripcionDecimas(
    recursos: Resources,
    d: Long,
): String {
    val minutos = (d / DECIMAS_MINUTO).toInt()
    val segundos = String.format(ES, "%.1f", (d % DECIMAS_MINUTO) / DECIMAS_SEGUNDO.toDouble())
    val partesSegundos = recursos.getString(R.string.crono_segundos, segundos)
    return if (minutos > 0) {
        recursos.getQuantityString(R.plurals.crono_minutos, minutos, minutos) + " " + partesSegundos
    } else {
        partesSegundos
    }
}

/** Delta dicho en voz alta: «más 1,2 segundos», «menos 0,3 segundos» o «igual que tu mejor tiempo». */
internal fun descripcionDelta(
    recursos: Resources,
    ms: Long,
): String =
    when (signoDelta(ms)) {
        SignoDelta.IGUAL -> recursos.getString(R.string.crono_delta_igual)
        SignoDelta.MEJOR -> recursos.getString(R.string.crono_delta_menos, descripcionDecimas(recursos, decimas(ms)))
        SignoDelta.PEOR -> recursos.getString(R.string.crono_delta_mas, descripcionDecimas(recursos, decimas(ms)))
    }
