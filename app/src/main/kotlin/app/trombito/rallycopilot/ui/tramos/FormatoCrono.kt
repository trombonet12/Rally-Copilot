package app.trombito.rallycopilot.ui.tramos

import android.content.res.Resources
import androidx.compose.ui.graphics.Color
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ParcialCrono
import app.trombito.rallycopilot.domain.tramos.TipoCorte
import app.trombito.rallycopilot.domain.tramos.TramoCronometrado
import app.trombito.rallycopilot.ui.navegacion.guiado.ColoresGuiado
import app.trombito.rallycopilot.ui.theme.ColoresRally
import app.trombito.rallycopilot.ui.theme.RallyOscuro
import java.util.Locale
import kotlin.math.abs

private const val MS_DECIMA = 100L
private const val DECIMAS_SEGUNDO = 10L
private const val SEGUNDOS_MINUTO = 60L
private const val MINUTOS_HORA = 60L
private const val DECIMAS_MINUTO = DECIMAS_SEGUNDO * SEGUNDOS_MINUTO
private const val DECIMAS_HORA = DECIMAS_MINUTO * MINUTOS_HORA
private const val MENOS = '−'

/** Cuánto se queda la tarjeta de meta en pantalla. */
internal const val DURACION_TARJETA_META_MS = 8_000L

/** Cuánto destaca el parcial de un sector recién cortado. */
internal const val DURACION_PARCIAL_DESTACADO_MS = 3_000L

/** Qué se pinta del cronómetro. */
internal enum class VistaCrono { NINGUNA, HUD, MINI }

/**
 * HUD completo solo con un tramo guardado que tiene mejor pasada de referencia («tramo conocido», decisión de Joan);
 * en cualquier otro caso (tramo nuevo o conducción sin tramo) solo el cronómetro pequeño.
 */
internal fun vistaCrono(crono: CronoEnCurso?): VistaCrono =
    when {
        crono == null -> VistaCrono.NINGUNA
        crono.tramo != null && crono.hayMejorPasada -> VistaCrono.HUD
        else -> VistaCrono.MINI
    }

/** Si el delta es a favor, en contra o ninguno (con la precisión de una décima, como se lee en pantalla). */
internal enum class SignoDelta { MEJOR, PEOR, IGUAL }

internal fun decimas(ms: Long): Long = Math.round(abs(ms) / MS_DECIMA.toDouble())

internal fun signoDelta(ms: Long): SignoDelta =
    when {
        decimas(ms) == 0L -> SignoDelta.IGUAL
        ms < 0 -> SignoDelta.MEJOR
        else -> SignoDelta.PEOR
    }

/** Color de un [SignoDelta]: verde a favor, rojo en contra, neutro si va igual. */
internal fun colorSigno(
    signo: SignoDelta?,
    colores: ColoresRally = RallyOscuro,
    neutro: Color = ColoresGuiado.Texto,
): Color =
    when (signo) {
        SignoDelta.MEJOR -> colores.deltaMejor
        SignoDelta.PEOR -> colores.deltaPeor
        else -> neutro
    }

/** Verde si va más rápido que la mejor pasada (delta negativo), rojo si va más lento, neutro si va igual. */
internal fun colorDelta(
    ms: Long?,
    colores: ColoresRally = RallyOscuro,
    neutro: Color = ColoresGuiado.Texto,
): Color = colorSigno(ms?.let(::signoDelta), colores, neutro)

/** Color de un parcial: el del delta si lo hay, el neutro si no hay mejor pasada con la que comparar. */
internal fun colorParcial(
    diferenciaMs: Long?,
    colores: ColoresRally = RallyOscuro,
    neutro: Color = ColoresGuiado.Texto,
): Color = colorDelta(diferenciaMs, colores, neutro)

/**
 * «mm:ss.d» (décimas truncadas, como un cronómetro) y «h:mm:ss.d» pasada la hora. Con [conDecimas] falso, «mm:ss».
 */
internal fun formatearTiempo(
    ms: Long,
    conDecimas: Boolean = true,
): String {
    val d = ms.coerceAtLeast(0L) / MS_DECIMA
    val dec = d % DECIMAS_SEGUNDO
    val s = d / DECIMAS_SEGUNDO % SEGUNDOS_MINUTO
    val m = d / DECIMAS_MINUTO % MINUTOS_HORA
    val h = d / DECIMAS_HORA
    val base =
        if (h > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", m, s)
        }
    return if (conDecimas) "$base.$dec" else base
}

/** Valor absoluto de un delta: «1,2» y, desde el minuto, «1:05,3». */
private fun cuerpoDelta(ms: Long): String {
    val d = decimas(ms)
    val dec = d % DECIMAS_SEGUNDO
    val s = d / DECIMAS_SEGUNDO
    return if (s >= SEGUNDOS_MINUTO) {
        String.format(Locale.ROOT, "%d:%02d,%d", s / SEGUNDOS_MINUTO, s % SEGUNDOS_MINUTO, dec)
    } else {
        String.format(Locale.ROOT, "%d,%d", s, dec)
    }
}

/** Delta con signo y coma decimal: «−0,3», «+1,2»; «0,0» si es menos de media décima. */
internal fun formatearDelta(ms: Long): String =
    when (signoDelta(ms)) {
        SignoDelta.MEJOR -> "$MENOS${cuerpoDelta(ms)}"
        SignoDelta.PEOR -> "+${cuerpoDelta(ms)}"
        SignoDelta.IGUAL -> cuerpoDelta(0L)
    }
