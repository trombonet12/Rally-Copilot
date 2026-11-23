package app.trombito.rallycopilot.ui.comun

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R

/**
 * Las poses de Trombito con casco, sin brazos ni guante blanco: [SALUDANDO] (bienvenida y diálogos), [BANDERA] (metas
 * y finales) y [DORMIDO] (estados vacíos). Nunca se pintan sobre el mapa en marcha.
 */
enum class PoseMascota(
    @DrawableRes val recurso: Int,
) {
    SALUDANDO(R.drawable.mascota_saludando),
    BANDERA(R.drawable.mascota_bandera),
    DORMIDO(R.drawable.mascota_dormido),
}

private val LADO_MASCOTA = 96.dp

/** Trombito en la [pose] dada, cuadrado de [lado]. Es decorativo: TalkBack lo salta. */
@Composable
fun MascotaPose(
    pose: PoseMascota,
    modifier: Modifier = Modifier,
    lado: Dp = LADO_MASCOTA,
) {
    Image(
        painter = painterResource(pose.recurso),
        contentDescription = null,
        modifier = modifier.size(lado).clearAndSetSemantics {},
    )
}
