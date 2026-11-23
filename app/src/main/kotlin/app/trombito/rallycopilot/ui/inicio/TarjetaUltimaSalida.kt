package app.trombito.rallycopilot.ui.inicio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.mistramos.MiniaturaTraza
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.CifraPequena
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Radios

/** Etiqueta de prueba del botón «Repetir último tramo» de la tarjeta de la última salida. */
const val ETIQUETA_REPETIR_ULTIMO = "repetir-ultimo-inicio"

private val LADO_MINIATURA = 72.dp
private val ICONO_BOTON = 18.dp
private const val GROSOR_SILUETA_DP = 3f
private const val TRAMO_PUNTEADO_DP = 8f
private const val OPACIDAD_SILUETA = 0.6f

/**
 * Tarjeta de la última salida: pulsable (abre el detalle del tramo) con la silueta, el nombre, el tiempo con la cifra
 * del cronómetro y el mejor, y debajo el botón «Repetir último tramo». Sin salidas ([salida] nula) es la misma tarjeta
 * con una silueta punteada y «Tu primer tramo saldrá aquí».
 */
@Composable
internal fun TarjetaUltimaSalida(
    salida: UltimaSalida?,
    onAbrir: (String) -> Unit,
    onRepetir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TarjetaWrc(modifier = modifier.fillMaxWidth(), relleno = Espacio.m) {
        if (salida == null) {
            PrimerTramo()
        } else {
            UltimaSalidaContenido(salida, onAbrir, onRepetir)
        }
    }
}

@Composable
private fun UltimaSalidaContenido(
    salida: UltimaSalida,
    onAbrir: (String) -> Unit,
    onRepetir: () -> Unit,
) {
    val tiempo = FormatoEs.tiempo(salida.tiempoMs)
    val mejor = FormatoEs.tiempo(salida.mejorMs)
    val descripcion = stringResource(R.string.inicio_ultima_salida, salida.nombre, tiempo, mejor)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Medidas.zonaTactil)
                .clip(Radios.formaTarjeta)
                .clickable(onClickLabel = stringResource(R.string.inicio_ultima_abrir), role = Role.Button) {
                    onAbrir(salida.tramoId)
                }.semantics(mergeDescendants = true) { contentDescription = descripcion }
                .testTag(ETIQUETA_ULTIMA_SALIDA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        MiniaturaTraza(salida.traza, LADO_MINIATURA)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            Text(
                text = stringResource(R.string.inicio_ultima_titulo),
                style = EtiquetaIcono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = salida.nombre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s), verticalAlignment = Alignment.Bottom) {
                Text(text = tiempo, style = CifraPequena, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(
                    text = stringResource(R.string.inicio_ultima_mejor, mejor),
                    style = Ayuda,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Icon(
            imageVector = Iconos.Siguiente,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.size(Espacio.s))
    FilledTonalButton(
        onClick = onRepetir,
        modifier = Modifier.fillMaxWidth().heightIn(min = Medidas.zonaTactil).testTag(ETIQUETA_REPETIR_ULTIMO),
    ) {
        Icon(imageVector = Iconos.Repetir, contentDescription = null, modifier = Modifier.size(ICONO_BOTON))
        Spacer(Modifier.width(Espacio.s))
        Text(text = stringResource(R.string.inicio_repetir_ultimo), style = MaterialTheme.typography.labelLarge)
    }
}

/** Primer uso: la misma disposición que la tarjeta con salida, con la silueta punteada y sin acciones. */
@Composable
private fun PrimerTramo() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Medidas.zonaTactil)
                .semantics(mergeDescendants = true) {}
                .testTag(ETIQUETA_BIENVENIDA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        SiluetaPunteada()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            Text(
                text = stringResource(R.string.inicio_primer_tramo),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.inicio_bienvenida),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Silueta de un tramo cualquiera a trazos, en el mismo cuadro que la miniatura real. Es decorativa. */
@Composable
private fun SiluetaPunteada() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = OPACIDAD_SILUETA)
    Canvas(
        Modifier
            .size(LADO_MINIATURA)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, Radios.formaTarjeta),
    ) {
        val w = size.width
        val h = size.height
        val ruta =
            Path().apply {
                moveTo(w * 0.18f, h * 0.82f)
                cubicTo(w * 0.60f, h * 0.90f, w * 0.10f, h * 0.55f, w * 0.50f, h * 0.50f)
                cubicTo(w * 0.90f, h * 0.45f, w * 0.40f, h * 0.20f, w * 0.82f, h * 0.18f)
            }
        val tramo = TRAMO_PUNTEADO_DP.dp.toPx()
        drawPath(
            ruta,
            color,
            style =
                Stroke(
                    width = GROSOR_SILUETA_DP.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(tramo, tramo)),
                ),
        )
        drawCircle(color, GROSOR_SILUETA_DP.dp.toPx() * 1.5f, Offset(w * 0.18f, h * 0.82f))
    }
}
