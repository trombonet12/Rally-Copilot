package app.trombito.rallycopilot.ui.comun

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.TituloVacio

private const val OPACIDAD_ICONO_TENUE = 0.6f
private val ALTO_MAXIMO_ILUSTRACION = 160.dp

/** Etiqueta de prueba del botón de acción de un estado vacío. */
const val ETIQUETA_ACCION_VACIO = "accion-vacio"

/** Etiqueta de prueba del hueco de ilustración de un estado vacío. */
const val ETIQUETA_ILUSTRACION_VACIO = "ilustracion-vacio"

/** Etiqueta de prueba de Trombito dormido, la ilustración por defecto de [VarianteVacio.VACIO]. */
const val ETIQUETA_MASCOTA_VACIO = "mascota-vacio"

/**
 * Qué estado cuenta la pantalla: [VACIO] (aún no hay nada: icono ámbar tenue), [ERROR] (algo falló: icono en el color
 * de error y aviso a TalkBack al aparecer) o [SIN_MAPAS] (falta el mapa offline: icono ámbar pleno).
 */
enum class VarianteVacio { VACIO, ERROR, SIN_MAPAS }

/**
 * Estado vacío del sistema WRC: ilustración (o icono de 48 dp si no hay), título de 18 sp, una frase que dice qué hacer
 * y, si hay [etiquetaAccion], un botón de 48 dp de alto. [etiqueta] es la etiqueta de prueba del conjunto.
 *
 * En la variante [VarianteVacio.VACIO] sin [ilustracion] ni [painter], la ilustración por defecto es Trombito dormido
 * ([PoseMascota.DORMIDO]); con [mascota] en false vuelve al icono. ERROR y SIN_MAPAS nunca la llevan.
 *
 * La ilustración es un hueco opcional: un [ilustracion] composable (p. ej. Trombito o un dibujo en lienzo) o un
 * [painter]; si hay las dos manda [ilustracion]. Es decorativa (TalkBack la salta). [variante] cambia el color del
 * icono y la semántica.
 */
@Suppress("LongParameterList") // API de componente: icono, textos, ilustración y acción opcional
@Composable
fun EstadoVacioWrc(
    icono: ImageVector,
    titulo: String,
    texto: String,
    etiqueta: String,
    modifier: Modifier = Modifier,
    etiquetaAccion: String? = null,
    onAccion: (() -> Unit)? = null,
    variante: VarianteVacio = VarianteVacio.VACIO,
    ilustracion: (@Composable () -> Unit)? = null,
    painter: Painter? = null,
    mascota: Boolean = true,
) {
    val avisoError =
        if (variante == VarianteVacio.ERROR) {
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        } else {
            Modifier
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(Espacio.xl)
                .testTag(etiqueta)
                .then(avisoError),
        verticalArrangement = Arrangement.spacedBy(Espacio.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CabezaVacio(icono, variante, ilustracion, painter, mascota)
        Text(
            text = titulo,
            style = TituloVacio,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = texto,
            style = Ayuda,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (etiquetaAccion != null && onAccion != null) {
            Button(
                onClick = onAccion,
                modifier = Modifier.heightIn(min = Medidas.zonaTactil).testTag(ETIQUETA_ACCION_VACIO),
            ) {
                Text(text = etiquetaAccion, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun colorIcono(variante: VarianteVacio): Color =
    when (variante) {
        VarianteVacio.VACIO -> MaterialTheme.colorScheme.primary.copy(alpha = OPACIDAD_ICONO_TENUE)
        VarianteVacio.ERROR -> MaterialTheme.colorScheme.error
        VarianteVacio.SIN_MAPAS -> MaterialTheme.colorScheme.primary
    }

/** Ilustración (composable o painter, decorativa) si la hay; si no, el icono de 48 dp del color de la [variante]. */
@Composable
private fun CabezaVacio(
    icono: ImageVector,
    variante: VarianteVacio,
    ilustracion: (@Composable () -> Unit)?,
    painter: Painter?,
    mascota: Boolean,
) {
    if (ilustracion == null && painter == null) {
        if (mascota && variante == VarianteVacio.VACIO) {
            MascotaPose(PoseMascota.DORMIDO, Modifier.testTag(ETIQUETA_MASCOTA_VACIO))
            return
        }
        Icon(
            imageVector = icono,
            contentDescription = null,
            tint = colorIcono(variante),
            modifier = Modifier.size(Medidas.iconoVacio),
        )
        return
    }
    Box(
        modifier =
            Modifier
                .heightIn(max = ALTO_MAXIMO_ILUSTRACION)
                .testTag(ETIQUETA_ILUSTRACION_VACIO)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (ilustracion != null) {
            ilustracion()
        } else if (painter != null) {
            Image(painter = painter, contentDescription = null)
        }
    }
}
