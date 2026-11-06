package app.trombito.rallycopilot.ui.tutorial

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.ui.comun.TemaSobreCarbon
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
import app.trombito.rallycopilot.ui.theme.CarbonOpaco
import app.trombito.rallycopilot.ui.theme.Radios

/** Asfalto de las ilustraciones sobre el carbón. */
internal val Asfalto = Color(0xFF3A3D43)

private const val OPACIDAD_BORDE_PANEL = 0.35f

/**
 * Progreso 0..1 de una ilustración que se repite cada [duracionMs]. Es un `State` para leerlo solo al dibujar o al
 * componer lo que cambia, no la página entera en cada fotograma.
 */
@Composable
internal fun rememberProgreso(duracionMs: Int): State<Float> {
    val transicion = rememberInfiniteTransition(label = "ilustracion")
    return transicion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(duracionMs, easing = LinearEasing)),
        label = "progreso",
    )
}

/** Tramo [desde]..[hasta] del progreso [t] reescalado a 0..1 (0 antes de [desde], 1 después de [hasta]). */
internal fun tramoDe(
    t: Float,
    desde: Float,
    hasta: Float,
): Float = ((t - desde) / (hasta - desde)).coerceIn(0f, 1f)

/**
 * Panel oscuro de una ilustración: carbón con un borde ámbar tenue, igual en tema claro y oscuro (el carbón es
 * oscuro siempre) y con el contenido pintado con la paleta oscura.
 */
@Composable
internal fun PanelIlustracion(
    alto: Dp,
    modifier: Modifier = Modifier,
    contenido: @Composable BoxScope.() -> Unit,
) {
    TemaSobreCarbon {
        Box(
            modifier
                .fillMaxWidth()
                .height(alto)
                .clip(Radios.formaPanel)
                .background(CarbonOpaco)
                .border(1.dp, AmbarSobreCarbon.copy(alpha = OPACIDAD_BORDE_PANEL), Radios.formaPanel),
            content = contenido,
        )
    }
}

/** Icono de campana (Material, Apache 2.0) para los permisos de notificaciones; Iconos no lo trae. */
internal val IconoCampana: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "Campana",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData =
                addPathNodes(
                    "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32" +
                        "V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z",
                ),
            fill = SolidColor(Color.Black),
        ).build()
}
