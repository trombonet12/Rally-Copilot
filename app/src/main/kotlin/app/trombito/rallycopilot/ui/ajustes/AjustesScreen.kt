package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import app.trombito.rallycopilot.domain.voz.VelocidadHabla
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.FondoWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_AJUSTES_MAPAS = "ajustes-mapas-offline"
const val ETIQUETA_ZOOM_PREFIJO = "ajustes-zoom-"
const val ETIQUETA_ICONO_PREFIJO = "ajustes-icono-"
const val ETIQUETA_ORIENTACION_RUMBO = "ajustes-orientacion-rumbo"
const val ETIQUETA_ORIENTACION_NORTE = "ajustes-orientacion-norte"

private val ALTO_FILA = 72.dp
private val ICONO_FILA = 32.dp

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
    val onDescargarVoz: (VozCopiloto) -> Unit = {},
    val onActivarConCopiloto: (Boolean) -> Unit = {},
    val onVolumenVoz: (Int) -> Unit = {},
    val onAudioCasco: (Boolean) -> Unit = {},
    val onEscuchaCascoSiempre: (Boolean) -> Unit = {},
    val onEscucharEjemplo: () -> Unit = {},
    val onTema: (Tema) -> Unit = {},
    val onCapa: (CapaMapa) -> Unit = {},
    val onRelieve: (Boolean) -> Unit = {},
    val onCronometrar: (Boolean) -> Unit = {},
    val onSalidaManual: (Boolean) -> Unit = {},
    val onNotasGrandes: (Boolean) -> Unit = {},
    val onAvisarCruces: (Boolean) -> Unit = {},
    val onAvisarPuentes: (Boolean) -> Unit = {},
    val onAvisarSalidasAutopista: (Boolean) -> Unit = {},
    val onAvisarRotondas: (Boolean) -> Unit = {},
    val onAvisarRadares: (Boolean) -> Unit = {},
    val onAvisarIncidencias: (Boolean) -> Unit = {},
    val onAvisarVelocidad: (Boolean) -> Unit = {},
    val onVelocidadHabla: (VelocidadHabla) -> Unit = {},
    val onExportarCopia: () -> Unit = {},
    val onImportarCopia: () -> Unit = {},
    val onCopiaAutomatica: (Boolean) -> Unit = {},
    val onCopiarAhora: () -> Unit = {},
    val onVerTutorial: () -> Unit = {},
    val onAyudaNotas: () -> Unit = {},
    val onAyudaVoz: () -> Unit = {},
    val onAcercaDe: () -> Unit = {},
    val onBorrarCasa: () -> Unit = {},
)

/**
 * Portada de Ajustes: una sola columna de categorías, una fila a todo el ancho cada una con su icono, su título y el
 * valor que tiene ahora. Tocar una abre su subpantalla con la vista previa en vivo.
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
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Espacio.l)
                        .padding(bottom = Espacio.l),
                verticalArrangement = Arrangement.spacedBy(Espacio.m),
            ) {
                SeccionAjustes.entries.forEach { seccion ->
                    FilaPortada(
                        seccion = seccion,
                        valor = valorDeSeccion(seccion, ajustes),
                        onClick = { acciones.onSeccion(seccion) },
                    )
                }
            }
        }
    }
}

/** Fila de una categoría: icono ámbar de 32 dp, título de 16 sp, el valor actual de 14 sp debajo y la flecha. */
@Composable
private fun FilaPortada(
    seccion: SeccionAjustes,
    valor: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TarjetaWrc(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_FILA)
                .testTag(ETIQUETA_SECCION_PREFIJO + seccion.clave),
        relleno = Espacio.m,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            IconoFila(seccion)
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(seccion.titulo()),
                    style = Cuerpo,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = valor,
                    style = Ayuda,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Iconos.Siguiente, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IconoFila(seccion: SeccionAjustes) {
    val recurso = seccion.iconoRecurso()
    if (recurso != null) {
        Icon(
            painter = painterResource(recurso),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ICONO_FILA),
        )
    } else {
        Icon(
            imageVector = seccion.icono(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ICONO_FILA),
        )
    }
}
