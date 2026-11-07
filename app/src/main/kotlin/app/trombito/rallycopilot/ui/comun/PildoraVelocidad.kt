package app.trombito.rallycopilot.ui.comun

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
import app.trombito.rallycopilot.ui.theme.CarbonTranslucido
import app.trombito.rallycopilot.ui.theme.CifraMedia
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono
import app.trombito.rallycopilot.ui.theme.LimitarEscalaHud
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

/** Etiqueta de prueba de la señal gris «?» de una vía sin límite conocido. */
const val ETIQUETA_SENAL_LIMITE_DESCONOCIDO = "senal-limite-desconocido"

/** Veces que pulsa el borde al empezar el exceso; después queda fijo. */
internal const val PULSOS_EXCESO = 3

private const val RELACION_ARO = 7
private const val RELACION_ARO_DESCONOCIDA = 16
private const val RELACION_CIFRA = 0.4f
private const val OPACIDAD_PULSO_MIN = 0.25f
private const val OPACIDAD_SENAL_DESCONOCIDA = 0.75f
private const val PERIODO_PULSO_EXCESO_MS = 2L * Movimiento.PULSO_EXCESO
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
 * Señal discreta de «no hay límite conocido en esta vía»: círculo gris de trazo fino punteado con «?», sin rojo ni
 * blanco para no confundirse con un límite real. Decorativa: la píldora la describe.
 */
@Composable
fun SenalLimiteDesconocida(
    modifier: Modifier = Modifier,
    lado: Dp = Medidas.senalLimite,
) {
    val trazo = TextoSecundarioSobreCarbon.copy(alpha = OPACIDAD_SENAL_DESCONOCIDA)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(lado)
                .drawBehind {
                    val grosor = lado.toPx() / RELACION_ARO_DESCONOCIDA
                    drawCircle(
                        color = trazo,
                        radius = (size.minDimension - grosor) / 2,
                        style =
                            Stroke(
                                width = grosor,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(grosor * 2, grosor * 1.5f)),
                            ),
                    )
                },
    ) {
        Text(
            text = stringResource(R.string.panel_limite_desconocido),
            color = trazo,
            fontWeight = FontWeight.Bold,
            fontSize = with(LocalDensity.current) { (lado * RELACION_CIFRA).toSp() },
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Píldora de velocidad de la conducción: velocidad actual en cifra media (40 sp) y señal de límite de 40 dp, sobre
 * carbón translúcido. Con [excede] el borde de la píldora pulsa en rojo tres veces y queda fijo (no hay relleno rojo)
 * y aparece una flecha ▲ (redundancia al color). [velocidadKmh] nulo = sin dato; [limiteKmh] nulo = sin señal, salvo
 * con [limiteDesconocido] (se va por una vía sin límite en los datos): señal gris con «?». El escalado de la fuente del
 * sistema se limita a 1,3 (ver [LimitarEscalaHud]).
 */
@Composable
fun PildoraVelocidad(
    velocidadKmh: Int?,
    limiteKmh: Int?,
    modifier: Modifier = Modifier,
    excede: Boolean = velocidadKmh != null && limiteKmh != null && velocidadKmh > limiteKmh,
    limiteDesconocido: Boolean = false,
) {
    LimitarEscalaHud {
        ContenidoPildora(velocidadKmh, limiteKmh, excede, limiteDesconocido && limiteKmh == null, modifier)
    }
}

@Composable
private fun ContenidoPildora(
    velocidadKmh: Int?,
    limiteKmh: Int?,
    excede: Boolean,
    limiteDesconocido: Boolean,
    modifier: Modifier,
) {
    val colorBorde = if (excede) RojoAcento.copy(alpha = alphaBordeExceso()) else Color.Transparent
    val velocidadTexto =
        velocidadKmh?.let { stringResource(R.string.panel_velocidad_descripcion, it) }
            ?: stringResource(R.string.panel_velocidad_sin_dato)
    val limiteTexto =
        when {
            limiteKmh != null -> stringResource(R.string.panel_limite_descripcion, limiteKmh)
            limiteDesconocido -> stringResource(R.string.panel_limite_desconocido_descripcion)
            else -> null
        }
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
        if (limiteKmh != null) {
            SenalLimiteWrc(limiteKmh, Modifier.testTag(ETIQUETA_SENAL_LIMITE))
        } else if (limiteDesconocido) {
            SenalLimiteDesconocida(Modifier.testTag(ETIQUETA_SENAL_LIMITE_DESCONOCIDO))
        }
    }
}

/** Rojo claro de la flecha de exceso sobre carbón (el `tertiary` oscuro, 7:1 sobre el carbón). */
private val RojoAcentoClaro = Color(0xFFFF8F85)

/**
 * Opacidad del borde de exceso [ms] después de empezar: [PULSOS_EXCESO] pulsos (de 1 a [OPACIDAD_PULSO_MIN] y vuelta,
 * un periodo cada 1,2 s) y después fija en 1. Así el aviso llama la atención sin destellear sin fin en el campo
 * visual del conductor.
 */
internal fun alfaBordeExceso(ms: Long): Float {
    if (ms >= PULSOS_EXCESO * PERIODO_PULSO_EXCESO_MS) return 1f
    val fase = (ms % PERIODO_PULSO_EXCESO_MS).toFloat() / Movimiento.PULSO_EXCESO
    val hundimiento = if (fase <= 1f) fase else 2f - fase
    return 1f - (1f - OPACIDAD_PULSO_MIN) * hundimiento
}

/** La opacidad del borde mientras dura el exceso: pulsa [PULSOS_EXCESO] veces desde que se monta y queda en 1. */
@Composable
private fun alphaBordeExceso(): Float {
    var alfa by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) {
        val inicio = withFrameMillis { it }
        var transcurrido = 0L
        while (transcurrido < PULSOS_EXCESO * PERIODO_PULSO_EXCESO_MS) {
            alfa = alfaBordeExceso(transcurrido)
            transcurrido = withFrameMillis { it } - inicio
        }
        alfa = 1f
    }
    return alfa
}
