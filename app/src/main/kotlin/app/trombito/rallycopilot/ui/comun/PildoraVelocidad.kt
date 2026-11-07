package app.trombito.rallycopilot.ui.comun

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
import app.trombito.rallycopilot.ui.theme.CarbonTranslucido
import app.trombito.rallycopilot.ui.theme.CifraMedia
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Movimiento
import app.trombito.rallycopilot.ui.theme.Radios
import app.trombito.rallycopilot.ui.theme.RojoAcento
import app.trombito.rallycopilot.ui.theme.TextoSecundarioSobreCarbon
import app.trombito.rallycopilot.ui.theme.TextoSobreCarbon

/** Etiqueta de prueba de la píldora de velocidad. */
const val ETIQUETA_PILDORA_VELOCIDAD = "pildora-velocidad"

/** Etiqueta de prueba de la señal de límite. */
const val ETIQUETA_SENAL_LIMITE = "senal-limite"

private const val RELACION_ARO = 7
private const val RELACION_CIFRA = 0.4f
private const val OPACIDAD_PULSO_MIN = 0.25f
private val GROSOR_BORDE_PILDORA = 2.dp

/**
 * Señal redonda de límite (aro rojo, fondo blanco, cifra negra: colores de la señal real, el rojo es aquí el aro,
 * no un relleno). Decorativa por sí sola: quien la usa la describe.
 */
@Composable
fun SenalLimiteWrc(
    limiteKmh: Int,
    modifier: Modifier = Modifier,
    lado: Dp = Medidas.senalLimite,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(lado)
                .background(Color.White, CircleShape)
                .border(lado / RELACION_ARO, RojoAcento, CircleShape),
    ) {
        Text(
            text = limiteKmh.toString(),
            color = Color.Black,
            fontWeight = FontWeight.Black,
            // La cifra va en dp, no en sp: la señal tiene lado fijo y con fuentes grandes se salía del aro.
            fontSize = with(LocalDensity.current) { (lado * RELACION_CIFRA).toSp() },
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Píldora de velocidad de la conducción: velocidad actual en cifra media (40 sp) y señal de límite de 40 dp, sobre
 * carbón translúcido. Con [excede] el borde de la píldora pulsa en rojo (no hay relleno rojo) y aparece una flecha ▲
 * (redundancia al color). [velocidadKmh] nulo = sin dato; [limiteKmh] nulo = sin señal.
 */
@Composable
fun PildoraVelocidad(
    velocidadKmh: Int?,
    limiteKmh: Int?,
    modifier: Modifier = Modifier,
    excede: Boolean = velocidadKmh != null && limiteKmh != null && velocidadKmh > limiteKmh,
) {
    val colorBorde = if (excede) RojoAcento.copy(alpha = alphaPulso()) else Color.Transparent
    val velocidadTexto =
        velocidadKmh?.let { stringResource(R.string.panel_velocidad_descripcion, it) }
            ?: stringResource(R.string.panel_velocidad_sin_dato)
    val limiteTexto = limiteKmh?.let { stringResource(R.string.panel_limite_descripcion, it) }
    val excesoTexto = stringResource(R.string.wrc_exceso_velocidad)
    val descripcion = listOfNotNull(velocidadTexto, limiteTexto, if (excede) excesoTexto else null).joinToString(". ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
        modifier =
            modifier
                .testTag(ETIQUETA_PILDORA_VELOCIDAD)
                .defaultMinSize(minHeight = Medidas.zonaTactil)
                .background(CarbonTranslucido, Radios.formaPildora)
                .border(GROSOR_BORDE_PILDORA, colorBorde, Radios.formaPildora)
                .padding(horizontal = Espacio.l, vertical = Espacio.s)
                .clearAndSetSemantics {
                    contentDescription = descripcion
                    if (excede) stateDescription = excesoTexto
                },
    ) {
        if (excede) {
            Icon(
                imageVector = Iconos.FlechaArriba,
                contentDescription = null,
                tint = RojoAcentoClaro,
                modifier = Modifier.size(Medidas.iconoLista),
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            Text(
                text = velocidadKmh?.toString() ?: stringResource(R.string.panel_velocidad_vacia),
                style = CifraMedia,
                color = TextoSobreCarbon,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = stringResource(R.string.panel_unidad),
                style = EtiquetaIcono,
                color = TextoSecundarioSobreCarbon,
                modifier = Modifier.padding(bottom = Espacio.s),
            )
        }
        if (limiteKmh != null) SenalLimiteWrc(limiteKmh, Modifier.testTag(ETIQUETA_SENAL_LIMITE))
    }
}

/** Rojo claro de la flecha de exceso sobre carbón (el `tertiary` oscuro, 7:1 sobre el carbón). */
private val RojoAcentoClaro = Color(0xFFFF8F85)

@Composable
private fun alphaPulso(): Float {
    val transicion = rememberInfiniteTransition(label = "pulso-exceso")
    val alpha by transicion.animateFloat(
        initialValue = OPACIDAD_PULSO_MIN,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(Movimiento.PULSO_EXCESO), RepeatMode.Reverse),
        label = "alpha-exceso",
    )
    return alpha
}
