package app.trombito.rallycopilot.ui.navegacion.guiado

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.navegacion.ResumenTrayecto

const val ETIQUETA_TARJETA_LLEGADA = "tarjeta_llegada"
const val ETIQUETA_LLEGADA_TITULO = "tarjeta_llegada_titulo"
const val ETIQUETA_LLEGADA_DESTINO = "tarjeta_llegada_destino"
const val ETIQUETA_LLEGADA_CERRAR = "tarjeta_llegada_cerrar"
const val ETIQUETA_LLEGADA_RESUMEN = "tarjeta_llegada_resumen"

private val COORDENADAS = Regex("""^-?\d+[.,]\d+\s*,\s*-?\d+[.,]\d+$""")

private val FORMA = RoundedCornerShape(14.dp)
private const val OPACIDAD_RESUMEN = 0.8f

/** El nombre del destino, o null si está vacío o es solo un par de coordenadas. */
internal fun nombreLegible(nombre: String): String? =
    nombre.trim().takeIf { it.isNotEmpty() && !COORDENADAS.matches(it) }

/**
 * Tarjeta de fin de tramo (EstadoNavegacion.Llegada): bandera, «Has llegado», el nombre del destino (nunca las
 * coordenadas: sin nombre, «Destino»), tiempo y kilómetros del trayecto si se conocen y un botón para cerrar.
 */
@Composable
fun TarjetaLlegada(
    destino: Destino,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
    resumen: ResumenTrayecto? = null,
) {
    val titulo = stringResource(R.string.guiado_llegada_titulo)
    val nombre = nombreLegible(destino.nombre) ?: stringResource(R.string.guiado_llegada_destino_generico)
    Row(
        modifier =
            modifier
                .testTag(ETIQUETA_TARJETA_LLEGADA)
                .widthIn(max = 420.dp)
                .background(ColoresGuiado.Carbon, FORMA)
                .border(2.dp, ColoresGuiado.Laton, FORMA)
                .padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = IconosGuiado.Bandera,
            contentDescription = null,
            tint = ColoresGuiado.Laton,
            modifier = Modifier.size(40.dp),
        )
        Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
            Text(
                text = titulo,
                modifier = Modifier.testTag(ETIQUETA_LLEGADA_TITULO),
                color = ColoresGuiado.Laton,
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                    ),
            )
            Text(
                text = nombre,
                modifier = Modifier.testTag(ETIQUETA_LLEGADA_DESTINO),
                color = ColoresGuiado.Texto,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (resumen != null) ResumenLlegada(resumen)
        }
        BotonIcono(
            descripcion = stringResource(R.string.guiado_llegada_cerrar),
            onClick = onCerrar,
            modifier = Modifier.testTag(ETIQUETA_LLEGADA_CERRAR),
        ) {
            Icon(imageVector = Iconos.Cerrar, contentDescription = null)
        }
    }
}

/** «25 min · 31,9 km»: lo que se ha tardado y recorrido. */
@Composable
private fun ResumenLlegada(resumen: ResumenTrayecto) {
    Text(
        text =
            stringResource(
                R.string.guiado_llegada_resumen,
                formatearTiempoRestante(resumen.tiempoS),
                FormatoEs.distancia(resumen.distanciaM),
            ),
        modifier = Modifier.testTag(ETIQUETA_LLEGADA_RESUMEN),
        color = ColoresGuiado.Texto.copy(alpha = OPACIDAD_RESUMEN),
        style = MaterialTheme.typography.bodyLarge,
    )
}
