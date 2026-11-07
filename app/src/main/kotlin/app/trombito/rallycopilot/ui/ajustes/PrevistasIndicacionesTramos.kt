package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.CifraMedia
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono
import app.trombito.rallycopilot.ui.theme.LocalColoresRally
import app.trombito.rallycopilot.ui.theme.Movimiento
import app.trombito.rallycopilot.ui.theme.RojoAcento

const val ETIQUETA_PREVISTA_CRONO = "prevista-crono"
const val ETIQUETA_PREVISTA_INDICACION_PREFIJO = "prevista-indicacion-"

private val LADO_INDICACION = 56.dp
private const val LUCES_SALIDA = 5
private const val OPACIDAD_APAGADA = 0.35f

/** Indicaciones: los tres iconos con su nombre; el que está desactivado se ve apagado y tachado. */
@Composable
internal fun PrevistaIndicaciones(ajustes: AjustesConduccion) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        IndicacionPrevista(
            clave = "cruces",
            icono = painterResource(R.drawable.ic_nota_cruce),
            nombre = stringResource(R.string.ajustes_prevista_cruces),
            activa = ajustes.avisarCruces,
        )
        IndicacionPrevista(
            clave = "salidas",
            icono = painterResource(R.drawable.ic_nota_salida_autopista),
            nombre = stringResource(R.string.ajustes_prevista_salidas),
            activa = ajustes.avisarSalidasAutopista,
        )
        IndicacionPrevista(
            clave = "rotondas",
            icono = painterResource(R.drawable.ic_nota_rotonda),
            nombre = stringResource(R.string.ajustes_prevista_rotondas),
            activa = ajustes.avisarRotondas,
        )
    }
}

/** Un icono con su nombre; si [activa] es falso se ve apagado y tachado con una raya roja. */
@Composable
internal fun IndicacionPrevista(
    clave: String,
    icono: Painter,
    nombre: String,
    activa: Boolean,
) {
    val descripcion =
        stringResource(
            if (activa) R.string.ajustes_prevista_indicaciones_si else R.string.ajustes_prevista_indicaciones_no,
            nombre,
        )
    val tachado by animateFloatAsState(if (activa) 0f else 1f, tween(Movimiento.MEDIA), label = "tachado")
    val color = MaterialTheme.colorScheme.primary
    Column(
        modifier =
            Modifier
                .testTag(ETIQUETA_PREVISTA_INDICACION_PREFIJO + clave)
                .semantics { contentDescription = descripcion },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        Box(Modifier.size(LADO_INDICACION), contentAlignment = Alignment.Center) {
            Icon(
                painter = icono,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(40.dp).alpha(1f - tachado * (1f - OPACIDAD_APAGADA)),
            )
            Canvas(Modifier.size(LADO_INDICACION).alpha(tachado)) {
                val margen = 6.dp.toPx()
                drawLine(
                    color = RojoAcento,
                    start = Offset(margen, margen),
                    end = Offset(size.width - margen, size.height - margen),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Text(
            text = nombre,
            style = EtiquetaIcono,
            color = if (activa) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * Tramos: sin cronómetro, un reloj apagado; con él, el crono con su delta (salida automática) o «LISTO» y las cinco
 * luces de la cuenta atrás (salida manual).
 */
@Composable
internal fun PrevistaTramos(ajustes: AjustesConduccion) {
    val descripcion =
        stringResource(
            when {
                !ajustes.cronometrar -> R.string.ajustes_prevista_crono_inactivo
                ajustes.salidaManual -> R.string.ajustes_prevista_crono_manual
                else -> R.string.ajustes_prevista_crono_activo
            },
        )
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    ETIQUETA_PREVISTA_CRONO,
                ).semantics { contentDescription = descripcion },
        verticalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        when {
            !ajustes.cronometrar -> RelojApagado()
            ajustes.salidaManual -> RelojListo()
            else -> RelojCorriendo()
        }
    }
}

@Composable
private fun RelojApagado() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
        Icon(
            Iconos.Cronometro,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(R.string.ajustes_prevista_crono_inactivo),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun RelojListo() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
        Icon(
            Iconos.Cronometro,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(R.string.ajustes_prevista_crono_listo),
            style = CifraMedia,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s), modifier = Modifier.clearAndSetSemantics {}) {
        repeat(LUCES_SALIDA) {
            Box(Modifier.size(20.dp).background(RojoAcento, CircleShape))
        }
    }
    Pie(R.string.ajustes_prevista_crono_pie_manual)
}

@Composable
private fun RelojCorriendo() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
        Icon(
            Iconos.Cronometro,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(R.string.ajustes_prevista_crono_tiempo),
            style = CifraMedia,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            text = stringResource(R.string.ajustes_prevista_crono_delta),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = LocalColoresRally.current.deltaMejor,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
    Pie(R.string.ajustes_prevista_crono_pie_auto)
}

@Composable
private fun Pie(
    @androidx.annotation.StringRes texto: Int,
) {
    Text(
        text = stringResource(texto),
        style = Ayuda,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clearAndSetSemantics {},
    )
}
