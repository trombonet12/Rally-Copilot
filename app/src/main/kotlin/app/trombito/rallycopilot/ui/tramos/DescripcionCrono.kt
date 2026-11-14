package app.trombito.rallycopilot.ui.tramos

import android.content.res.Resources
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.FormatoEs

private const val MS_DECIMA = 100L
private const val DECIMAS_SEGUNDO = 10L
private const val DECIMAS_MINUTO = 600L

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
    val segundos = FormatoEs.decimal((d % DECIMAS_MINUTO) / DECIMAS_SEGUNDO.toDouble())
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

/** Lo que TalkBack lee del cronómetro; con [distanciaSubtramo] dice que es un subtramo y de qué longitud. */
internal fun descripcionCronometro(
    recursos: Resources,
    transcurridoMs: Long,
    deltaMs: Long?,
    listo: Boolean,
    distanciaSubtramo: String?,
): String {
    val tiempo = descripcionTiempo(recursos, transcurridoMs)
    val delta = deltaMs?.let { descripcionDelta(recursos, it) }
    return when {
        listo -> {
            recursos.getString(R.string.crono_listo_descripcion)
        }

        distanciaSubtramo != null && delta == null -> {
            recursos.getString(R.string.subtramo_hud_descripcion_sin_delta, distanciaSubtramo, tiempo)
        }

        distanciaSubtramo != null -> {
            recursos.getString(R.string.subtramo_hud_descripcion, distanciaSubtramo, tiempo, delta)
        }

        delta == null -> {
            recursos.getString(R.string.crono_hud_descripcion_sin_delta, tiempo)
        }

        else -> {
            recursos.getString(R.string.crono_hud_descripcion, tiempo, delta)
        }
    }
}
