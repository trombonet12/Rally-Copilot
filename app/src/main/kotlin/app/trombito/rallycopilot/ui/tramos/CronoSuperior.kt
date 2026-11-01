package app.trombito.rallycopilot.ui.tramos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ResultadoCrono

/**
 * Lo que el cronómetro pone arriba del mapa, bajo el banner de maniobra, la franja de notas y el banner de alerta: el
 * HUD completo si el tramo es conocido (con mejor pasada), la tarjeta de meta unos segundos al cruzarla y nada en
 * cualquier otro caso (con un tramo nuevo o sin tramo solo hay [CronometroMini] en el panel inferior). Con [compacto]
 * (apaisado) todo es más bajo.
 */
@Composable
fun CronoSuperior(
    crono: CronoEnCurso?,
    modifier: Modifier = Modifier,
    compacto: Boolean = false,
    reloj: () -> Long = System::currentTimeMillis,
) {
    if (crono == null) return
    val resultado = crono.resultado
    when {
        resultado != null -> MetaTemporal(resultado, compacto, modifier)
        vistaCrono(crono) == VistaCrono.HUD -> HudCrono(crono, modifier, compacto, reloj)
    }
}

/** El cronómetro pequeño del panel inferior: solo sin tramo conocido y mientras no haya resultado de meta. */
@Composable
fun CronoMiniEnPanel(
    crono: CronoEnCurso?,
    modifier: Modifier = Modifier,
    reloj: () -> Long = System::currentTimeMillis,
) {
    if (crono != null && crono.resultado == null && vistaCrono(crono) == VistaCrono.MINI) {
        CronometroMini(crono, modifier, reloj)
    }
}

/** La tarjeta de meta, que se retira sola a los [DURACION_TARJETA_META_MS] de aparecer ese resultado. */
@Composable
private fun MetaTemporal(
    resultado: ResultadoCrono,
    compacta: Boolean,
    modifier: Modifier,
) {
    var visible by remember(resultado) { mutableStateOf(true) }
    LaunchedEffect(resultado) {
        esperarEnPrincipal(DURACION_TARJETA_META_MS)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        TarjetaMeta(resultado, compacta = compacta)
    }
}
