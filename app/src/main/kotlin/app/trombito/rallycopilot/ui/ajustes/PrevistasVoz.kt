package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.voz.VelocidadHabla
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.copiloto.IconosVoz
import app.trombito.rallycopilot.ui.copiloto.nombre
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono

const val ETIQUETA_PREVISTA_HABLA = "prevista-habla"

/** Los tres avisos por voz (apagado = tachado) y la velocidad de habla, en una fila bajo la voz. */
@Composable
internal fun PrevistaAvisosYHabla(ajustes: AjustesConduccion) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Espacio.s),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        IndicacionPrevista(
            clave = "radares",
            icono = painterResource(R.drawable.ic_alerta_radar),
            nombre = stringResource(R.string.avisos_radares),
            activa = ajustes.avisarRadares,
        )
        IndicacionPrevista(
            clave = "incidencias",
            icono = painterResource(R.drawable.ic_alerta_otra),
            nombre = stringResource(R.string.avisos_incidencias),
            activa = ajustes.avisarIncidencias,
        )
        IndicacionPrevista(
            clave = "velocidad",
            icono = rememberVectorPainter(IconosVoz.Velocimetro),
            nombre = stringResource(R.string.avisos_velocidad),
            activa = ajustes.avisarVelocidad,
        )
        PrevistaHabla(ajustes.velocidadHabla)
    }
}

/** La velocidad de habla elegida: ▶ (normal) o ▶▶ (rápida) con su nombre. */
@Composable
private fun PrevistaHabla(velocidad: VelocidadHabla) {
    val nombre = stringResource(velocidad.nombre())
    val descripcion =
        stringResource(
            if (velocidad == VelocidadHabla.RAPIDA) R.string.habla_prevista_rapida else R.string.habla_prevista_normal,
        )
    val iconoHabla = if (velocidad == VelocidadHabla.RAPIDA) IconosVoz.Rapida else Iconos.Empezar
    Column(
        modifier = Modifier.testTag(ETIQUETA_PREVISTA_HABLA).semantics { contentDescription = descripcion },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        Box(Modifier.size(LADO_HABLA), contentAlignment = Alignment.Center) {
            Icon(
                painter = rememberVectorPainter(iconoHabla),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(ICONO_HABLA),
            )
        }
        Text(
            text = nombre,
            style = EtiquetaIcono,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

private val LADO_HABLA = 56.dp
private val ICONO_HABLA = 40.dp
