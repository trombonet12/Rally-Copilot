package app.trombito.rallycopilot.ui.comun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally

/** Etiqueta del botón de volver, para las pruebas. */
const val ETIQUETA_VOLVER = "barra-volver"

/** Etiqueta de la franja roja bajo el título, para las pruebas. */
const val ETIQUETA_FRANJA_CABECERA = "barra-franja"

/** Etiqueta del subtítulo de la cabecera, para las pruebas. */
const val ETIQUETA_SUBTITULO_CABECERA = "barra-subtitulo"

private val ANCHO_FRANJA = 40.dp
private val ALTO_FRANJA = 3.dp

/**
 * Cabecera WRC de las pantallas secundarias: botón de volver (48 dp), título en ámbar, [subtitulo] opcional (una
 * línea, tono apagado) y, si [conFranja], una franja roja fina de marca bajo el texto. Las [acciones] van a la derecha.
 */
@Suppress("LongParameterList") // API de componente: cada opción visual es un parámetro con valor por defecto
@Composable
fun BarraSuperior(
    titulo: String,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    conFranja: Boolean = true,
    acciones: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Espacio.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotonVolver(onVolver)
        Spacer(Modifier.width(Espacio.xs))
        Column(
            modifier = Modifier.weight(1f).padding(vertical = Espacio.xs),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = titulo.uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitulo != null) {
                Text(
                    text = subtitulo,
                    style = Ayuda,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(ETIQUETA_SUBTITULO_CABECERA),
                )
            }
            if (conFranja) {
                Box(
                    Modifier
                        .padding(top = Espacio.xs)
                        .size(ANCHO_FRANJA, ALTO_FRANJA)
                        .background(LocalColoresRally.current.franja)
                        .testTag(ETIQUETA_FRANJA_CABECERA)
                        .clearAndSetSemantics {},
                )
            }
        }
        acciones()
    }
}

/**
 * Pantalla secundaria del sistema WRC: [FondoWrc] siempre debajo, márgenes seguros, [BarraSuperior] y el [contenido]
 * en una columna (usa `Modifier.weight(1f)` para llenar el resto). Es la plantilla de las pantallas secundarias.
 */
@Suppress("LongParameterList") // API de componente: cabecera, acciones y contenido
@Composable
fun PantallaWrc(
    titulo: String,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    acciones: @Composable RowScope.() -> Unit = {},
    contenido: @Composable ColumnScope.() -> Unit,
) {
    FondoWrc(modifier) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            BarraSuperior(titulo = titulo, onVolver = onVolver, subtitulo = subtitulo, acciones = acciones)
            contenido()
        }
    }
}

@Composable
fun BotonVolver(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onVolver, modifier = modifier.size(48.dp).testTag(ETIQUETA_VOLVER)) {
        Icon(
            imageVector = Iconos.Atras,
            contentDescription = stringResource(R.string.comun_volver),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
