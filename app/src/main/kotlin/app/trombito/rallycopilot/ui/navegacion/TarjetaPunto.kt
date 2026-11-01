package app.trombito.rallycopilot.ui.navegacion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.LADO_TACTIL
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_TARJETA_PUNTO = "nav-punto"
const val ETIQUETA_PUNTO_IR = "nav-punto-ir"
const val ETIQUETA_PUNTO_PARADA = "nav-punto-parada"
const val ETIQUETA_PUNTO_CERRAR = "nav-punto-cerrar"

private const val OPACIDAD_SOBRE_MAPA = 0.94f
private val ANCHO_MAXIMO_TARJETA = 520.dp
private val TAMANO_ICONO_BOTON = 20.dp

/** Lo que la tarjeta del punto puede hacer, según haya ruta o no. */
data class OpcionesPunto(
    /** Se puede añadir otra parada (hay sitio en la lista). */
    val puedeAnadir: Boolean = true,
)

/**
 * Tarjeta del punto elegido manteniendo pulsado el mapa: el nombre del lugar o la calle (o «Punto del mapa» con las
 * coordenadas cortas debajo) y dos botones con icono y texto corto, «Ir aquí» y «Añadir parada». La X la cierra; tocar
 * fuera (en el mapa) también, lo gestiona la pantalla.
 */
@Composable
fun TarjetaPunto(
    punto: PuntoElegido,
    opciones: OpcionesPunto,
    acciones: AccionesNavegacion,
    modifier: Modifier = Modifier,
) {
    val colores = MaterialTheme.colorScheme
    val titulo = punto.nombre ?: stringResource(R.string.navegacion_ui_punto_sin_nombre)
    val descripcion = stringResource(R.string.navegacion_ui_punto_tarjeta)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colores.surfaceContainerHigh.copy(OPACIDAD_SOBRE_MAPA),
        modifier =
            modifier
                .widthIn(max = ANCHO_MAXIMO_TARJETA)
                .fillMaxWidth()
                .testTag(ETIQUETA_TARJETA_PUNTO)
                .semantics { contentDescription = descripcion },
    ) {
        Column(Modifier.padding(start = Espacio.m, top = Espacio.xs, end = Espacio.xs, bottom = Espacio.s)) {
            CabeceraPunto(titulo, punto, acciones.onCerrarPunto)
            BotonesPunto(titulo, opciones, acciones)
        }
    }
}

/** Icono, nombre (o «Punto del mapa»), coordenadas cortas y la X que cierra la tarjeta. */
@Composable
private fun CabeceraPunto(
    titulo: String,
    punto: PuntoElegido,
    onCerrar: () -> Unit,
) {
    val colores = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Icon(Iconos.Lugar, contentDescription = null, tint = colores.tertiary)
        Column(Modifier.weight(1f)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                color = colores.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Con nombre, las coordenadas ayudan a reconocer el sitio; sin él, son lo único que hay.
            Text(
                text = coordenadasCortas(punto.punto),
                style = MaterialTheme.typography.bodySmall,
                color = colores.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onCerrar, modifier = Modifier.size(LADO_TACTIL).testTag(ETIQUETA_PUNTO_CERRAR)) {
            Icon(
                imageVector = Iconos.Cerrar,
                contentDescription = stringResource(R.string.navegacion_ui_punto_cerrar),
                tint = colores.onSurface,
            )
        }
    }
}

@Composable
private fun BotonesPunto(
    titulo: String,
    opciones: OpcionesPunto,
    acciones: AccionesNavegacion,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = Espacio.s),
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        // Con ruta en vista previa abre las rutas; guiando cambia el destino y conserva las paradas pendientes.
        BotonPunto(
            etiqueta = ETIQUETA_PUNTO_IR,
            texto = stringResource(R.string.navegacion_ui_punto_ir),
            descripcion = stringResource(R.string.navegacion_ui_punto_ir_descripcion, titulo),
            onClick = acciones.onIrAqui,
            principal = true,
            modifier = Modifier.weight(1f),
        )
        BotonPunto(
            etiqueta = ETIQUETA_PUNTO_PARADA,
            texto = stringResource(R.string.navegacion_ui_punto_parada),
            descripcion =
                if (opciones.puedeAnadir) {
                    stringResource(R.string.navegacion_ui_punto_parada_descripcion, titulo)
                } else {
                    stringResource(R.string.navegacion_ui_paradas_llenas)
                },
            onClick = acciones.onAnadirParadaDelPunto,
            principal = false,
            habilitado = opciones.puedeAnadir,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
@Suppress("LongParameterList") // etiqueta, textos, acción y estado del botón de la tarjeta
private fun BotonPunto(
    etiqueta: String,
    texto: String,
    descripcion: String,
    onClick: () -> Unit,
    principal: Boolean,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    val icono = if (principal) Iconos.Direcciones else Iconos.AnadirLugar
    val contenido: @Composable () -> Unit = {
        Icon(icono, contentDescription = null, modifier = Modifier.size(TAMANO_ICONO_BOTON))
        Text(text = texto, modifier = Modifier.padding(start = Espacio.s), maxLines = 1)
    }
    val base = modifier.heightIn(min = LADO_TACTIL).testTag(etiqueta).semantics { contentDescription = descripcion }
    if (principal) {
        Button(onClick = onClick, enabled = habilitado, modifier = base) { contenido() }
    } else {
        FilledTonalButton(onClick = onClick, enabled = habilitado, modifier = base) { contenido() }
    }
}
