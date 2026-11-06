package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.EstiloManiobras
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.ajustes.Tema
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.EstadoBotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_AJUSTES_MAPAS = "ajustes-mapas-offline"
const val ETIQUETA_AJUSTES_ACERCA_DE = "ajustes-acerca-de"
const val ETIQUETA_ZOOM_PREFIJO = "ajustes-zoom-"
const val ETIQUETA_ICONO_PREFIJO = "ajustes-icono-"
const val ETIQUETA_MANIOBRAS_PREFIJO = "ajustes-maniobras-"
const val ETIQUETA_ORIENTACION_RUMBO = "ajustes-orientacion-rumbo"
const val ETIQUETA_ORIENTACION_NORTE = "ajustes-orientacion-norte"

/** Callbacks de Ajustes. */
data class AccionesAjustes(
    val onVolver: () -> Unit = {},
    val onMapasOffline: () -> Unit = {},
    val onAcercaDe: () -> Unit = {},
    val onNivelZoom: (Int) -> Unit = {},
    val onIcono: (IconoPosicion) -> Unit = {},
    val onOrientacion: (Orientacion) -> Unit = {},
    val onEstiloManiobras: (EstiloManiobras) -> Unit = {},
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
    val onNotasGrandes: (Boolean) -> Unit = {},
    val onExportarCopia: () -> Unit = {},
    val onImportarCopia: () -> Unit = {},
)

/**
 * Ajustes en secciones con cabecera e icono, cada una con su vista previa en vivo arriba: Copiloto (modo y voz),
 * Notas (anticipación, simplificadas y notas grandes), Volumen de la voz, Tramos (cronometrar), Mapa, Escucha,
 * Tema, Datos y Acerca de.
 */
@Composable
fun AjustesScreen(
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
    modifier: Modifier = Modifier,
    copiaEnCurso: Boolean = false,
) {
    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        BarraSuperior(stringResource(R.string.ajustes_titulo), acciones.onVolver)
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Espacio.l)
                    .padding(bottom = Espacio.l),
            verticalArrangement = Arrangement.spacedBy(Espacio.l),
        ) {
            SeccionCopiloto(ajustes, acciones)
            SeccionNotas(ajustes, acciones)
            SeccionVoz(ajustes, acciones)
            SeccionTramos(ajustes.cronometrar, acciones.onCronometrar)
            SeccionMapa(ajustes, acciones)
            SeccionEscucha(ajustes, acciones)
            SeccionTema(ajustes.tema, acciones.onTema)
            SeccionDatos(acciones, copiaEnCurso)
            TarjetaSeccion(
                clave = "acerca",
                icono = Iconos.Informacion,
                titulo = R.string.ajustes_seccion_acerca,
                detalle = R.string.ajustes_seccion_acerca_detalle,
            ) {
                FilaAjuste(
                    stringResource(R.string.ajustes_acerca_de),
                    null,
                    ETIQUETA_AJUSTES_ACERCA_DE,
                    acciones.onAcercaDe,
                )
            }
        }
    }
}

@Composable
internal fun estadoSeleccion(seleccionado: Boolean) =
    EstadoBotonIcono(
        seleccionado = seleccionado,
        descripcionEstado = if (seleccionado) stringResource(R.string.ajustes_seleccionado) else null,
        rol = Role.RadioButton,
    )

/** Una fila que lleva a otra pantalla o lanza una acción; [icono] va a la izquierda si lo hay. */
@Composable
@Suppress("LongParameterList") // título, detalle, etiqueta de pruebas, clic, icono y estado de la fila
internal fun FilaAjuste(
    titulo: String,
    detalle: String?,
    etiqueta: String,
    onClick: () -> Unit,
    icono: ImageVector? = null,
    habilitada: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = habilitada,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag(etiqueta),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = Espacio.l, vertical = Espacio.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            if (icono != null) {
                Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (detalle != null) {
                    Text(
                        text = detalle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (icono == null) {
                Icon(Iconos.Siguiente, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
