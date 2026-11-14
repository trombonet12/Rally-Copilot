package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.BotonRedondoMapa
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.navegacion.guiado.ColoresGuiado
import kotlin.math.roundToInt

const val ETIQUETA_CALOR_FICHA = "calor-ficha"
const val ETIQUETA_CALOR_FICHA_TITULO = "calor-ficha-titulo"
const val ETIQUETA_CALOR_FICHA_PASADAS = "calor-ficha-pasadas"
const val ETIQUETA_CALOR_FICHA_KM = "calor-ficha-km"
const val ETIQUETA_CALOR_FICHA_VELOCIDAD = "calor-ficha-velocidad"
const val ETIQUETA_CALOR_FICHA_CERRAR = "calor-ficha-cerrar"

private const val OPACIDAD_FONDO = 0.94f
private val FORMA = RoundedCornerShape(12.dp)
private val ANCHO_MAXIMO = 520.dp
private val LADO_ICONO_ESTADISTICA = 20.dp

/**
 * Ficha pequeña de una carretera de la capa de calor tocada en el mapa, con el estilo de las demás tarjetas sobre el
 * mapa (carbón con filo ámbar): arriba la referencia y el nombre; debajo, con icono, las pasadas, los kilómetros y la
 * velocidad media. Se cierra con la X o tocando otra cosa del mapa.
 */
@Composable
fun TarjetaCalor(
    ficha: FichaCalor,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .widthIn(max = ANCHO_MAXIMO)
                .fillMaxWidth()
                .testTag(ETIQUETA_CALOR_FICHA)
                .background(ColoresGuiado.Carbon.copy(OPACIDAD_FONDO), FORMA)
                .border(2.dp, ColoresGuiado.Laton, FORMA)
                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Iconos.Calor,
            contentDescription = null,
            tint = ColoresGuiado.Laton,
            modifier = Modifier.size(36.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = tituloDeLaFicha(ficha),
                color = ColoresGuiado.Laton,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(ETIQUETA_CALOR_FICHA_TITULO),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                EstadisticaCalor(
                    Iconos.Repetir,
                    ficha.pasadas.toString(),
                    pluralStringResource(R.plurals.calor_ficha_pasadas, ficha.pasadas, ficha.pasadas),
                    ETIQUETA_CALOR_FICHA_PASADAS,
                )
                EstadisticaCalor(
                    Iconos.Regla,
                    stringResource(R.string.calor_ficha_km, ficha.km),
                    stringResource(R.string.calor_ficha_km, ficha.km),
                    ETIQUETA_CALOR_FICHA_KM,
                )
                val velocidad = ficha.velMediaKmh?.roundToInt()
                EstadisticaCalor(
                    Iconos.Velocimetro,
                    velocidad?.let { stringResource(R.string.calor_ficha_velocidad_corta, it) } ?: "—",
                    velocidad?.let { stringResource(R.string.calor_ficha_velocidad, it) }
                        ?: stringResource(R.string.calor_ficha_sin_velocidad),
                    ETIQUETA_CALOR_FICHA_VELOCIDAD,
                )
            }
        }
        // Botón carbón fijo (icono claro): con el tema claro un BotonIcono con tinte fijo quedaba invisible.
        BotonRedondoMapa(
            descripcion = stringResource(R.string.calor_ficha_cerrar),
            onClick = onCerrar,
            modifier = Modifier.testTag(ETIQUETA_CALOR_FICHA_CERRAR),
        ) { Icon(Iconos.Cerrar, contentDescription = null) }
    }
}

/** «Ma-10 · Carrer d'Orient», solo la referencia, solo el nombre o «Carretera sin nombre». */
@Composable
private fun tituloDeLaFicha(ficha: FichaCalor): String =
    when {
        ficha.ref != null && ficha.nombre != null -> {
            stringResource(R.string.calor_ficha_titulo, ficha.ref, ficha.nombre)
        }

        else -> {
            ficha.ref ?: ficha.nombre ?: stringResource(R.string.calor_ficha_sin_nombre)
        }
    }

@Composable
private fun EstadisticaCalor(
    icono: ImageVector,
    valor: String,
    descripcion: String,
    etiqueta: String,
) {
    Row(
        modifier = Modifier.testTag(etiqueta).clearAndSetSemantics { contentDescription = descripcion },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icono,
            contentDescription = null,
            tint = ColoresGuiado.Texto,
            modifier = Modifier.size(LADO_ICONO_ESTADISTICA),
        )
        Text(valor, color = ColoresGuiado.Texto, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
