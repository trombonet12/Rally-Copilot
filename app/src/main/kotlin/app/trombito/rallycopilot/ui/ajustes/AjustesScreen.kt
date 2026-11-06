package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.ajustes.Tema
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.FondoWrc
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_AJUSTES_MAPAS = "ajustes-mapas-offline"
const val ETIQUETA_ZOOM_PREFIJO = "ajustes-zoom-"
const val ETIQUETA_ICONO_PREFIJO = "ajustes-icono-"
const val ETIQUETA_ORIENTACION_RUMBO = "ajustes-orientacion-rumbo"
const val ETIQUETA_ORIENTACION_NORTE = "ajustes-orientacion-norte"

private val ALTO_TARJETA = 104.dp
private val ICONO_TARJETA = 32.dp
private val ANCHO_TRES_COLUMNAS = 600.dp

/** Callbacks de Ajustes. */
data class AccionesAjustes(
    val onVolver: () -> Unit = {},
    val onSeccion: (SeccionAjustes) -> Unit = {},
    val onMapasOffline: () -> Unit = {},
    val onNivelZoom: (Int) -> Unit = {},
    val onIcono: (IconoPosicion) -> Unit = {},
    val onOrientacion: (Orientacion) -> Unit = {},
    val onAnticipacion: (NivelAnticipacion) -> Unit = {},
    val onNotasSimplificadas: (Boolean) -> Unit = {},
    val onModoCopiloto: (ModoCopiloto) -> Unit = {},
    val onVozCopiloto: (VozCopiloto) -> Unit = {},
    val onProbarVoz: (VozCopiloto) -> Unit = {},
    val onActivarConCopiloto: (Boolean) -> Unit = {},
    val onVolumenVoz: (Int) -> Unit = {},
    val onEscucharEjemplo: () -> Unit = {},
    val onTema: (Tema) -> Unit = {},
    val onCapa: (CapaMapa) -> Unit = {},
    val onRelieve: (Boolean) -> Unit = {},
    val onCronometrar: (Boolean) -> Unit = {},
    val onSalidaManual: (Boolean) -> Unit = {},
    val onNotasGrandes: (Boolean) -> Unit = {},
    val onAvisarCruces: (Boolean) -> Unit = {},
    val onAvisarSalidasAutopista: (Boolean) -> Unit = {},
    val onAvisarRotondas: (Boolean) -> Unit = {},
    val onExportarCopia: () -> Unit = {},
    val onImportarCopia: () -> Unit = {},
)

/**
 * Portada de Ajustes: una rejilla de tarjetas (dos columnas; tres en pantallas anchas), una por sección, con el
 * valor que tiene ahora cada una. Tocar una abre su subpantalla con la vista previa en vivo.
 */
@Composable
fun AjustesScreen(
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
    modifier: Modifier = Modifier,
) {
    FondoWrc(modifier) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            BarraSuperior(stringResource(R.string.ajustes_titulo), acciones.onVolver)
            BoxWithConstraints(Modifier.weight(1f)) {
                val columnas = if (maxWidth >= ANCHO_TRES_COLUMNAS) 3 else 2
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Espacio.l)
                            .padding(bottom = Espacio.l),
                    verticalArrangement = Arrangement.spacedBy(Espacio.m),
                ) {
                    SeccionAjustes.entries.chunked(columnas).forEach { fila ->
                        FilaTarjetas(fila, columnas, ajustes, acciones)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaTarjetas(
    secciones: List<SeccionAjustes>,
    columnas: Int,
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        secciones.forEach { seccion ->
            TarjetaPortada(
                seccion = seccion,
                valor = valorDeSeccion(seccion, ajustes),
                onClick = { acciones.onSeccion(seccion) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        repeat(columnas - secciones.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** Tarjeta de una sección: icono ámbar de 32 dp arriba, título de 16 sp y el valor actual de 14 sp. */
@Composable
private fun TarjetaPortada(
    seccion: SeccionAjustes,
    valor: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titulo = stringResource(seccion.titulo())
    TarjetaWrc(
        modifier =
            modifier
                .heightIn(min = ALTO_TARJETA)
                .testTag(ETIQUETA_SECCION_PREFIJO + seccion.clave),
        relleno = Espacio.m,
        onClick = onClick,
    ) {
        Icon(
            imageVector = seccion.icono(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ICONO_TARJETA),
        )
        Text(
            text = titulo,
            style = Cuerpo,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Espacio.s),
        )
        Text(
            text = valor,
            style = Ayuda,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
