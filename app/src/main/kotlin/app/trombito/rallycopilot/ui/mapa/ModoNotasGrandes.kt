package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.ui.alertas.AlertasUiState
import app.trombito.rallycopilot.ui.alertas.BannerAlerta
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.EstadoBotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.maniobras.VarianteManiobra
import app.trombito.rallycopilot.ui.navegacion.guiado.BannerGuiado
import app.trombito.rallycopilot.ui.navegacion.guiado.PieGuiado
import app.trombito.rallycopilot.ui.navegacion.notas.FilaNota
import app.trombito.rallycopilot.ui.navegacion.notas.MEDIDAS_GRANDE
import app.trombito.rallycopilot.ui.navegacion.notas.MEDIDAS_ROADBOOK_LADO
import app.trombito.rallycopilot.ui.navegacion.notas.NotaEnorme
import app.trombito.rallycopilot.ui.navegacion.notas.NotasUiState
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.tramos.CronoSuperior

const val ETIQUETA_NOTAS_GRANDES_BOTON = "mapa-notas-grandes-boton"
const val ETIQUETA_ROADBOOK = "mapa-roadbook"
const val ETIQUETA_ROADBOOK_VACIO = "mapa-roadbook-vacio"

private val MARGEN = 8.dp
private const val PESO_NOTA_ENORME = 1.25f

/** El botón del panel que alterna entre el mapa y el modo de notas grandes (se recuerda en los ajustes). */
@Composable
internal fun BotonNotasGrandes(
    activo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estadoBoton =
        if (activo) R.string.notas_grandes_boton_estado_activo else R.string.notas_grandes_boton_estado_inactivo
    BotonIcono(
        descripcion = stringResource(R.string.notas_grandes_boton),
        onClick = onClick,
        modifier = modifier.testTag(ETIQUETA_NOTAS_GRANDES_BOTON),
        estado = EstadoBotonIcono(seleccionado = activo, descripcionEstado = stringResource(estadoBoton)),
    ) { Icon(Iconos.NotasGrandes, contentDescription = null) }
}

/** Lo que pinta el modo de notas grandes, agrupado. */
internal class ContenidoRoadbook(
    val notas: NotasUiState,
    val guiando: EstadoNavegacion.Guiando?,
    val variante: VarianteManiobra,
    val alertas: AlertasUiState,
    val crono: CronoEnCurso?,
)

/**
 * Modo de notas grandes (roadbook de WRC) en lugar del mapa: la nota actual enorme (icono, grado y modificadores,
 * distancia), las dos siguientes debajo, el banner de maniobra y el de alerta compactos arriba y el HUD del cronómetro
 * si hay tramo. En apaisado la nota enorme va a la izquierda y el resto a la derecha. La velocidad y el límite
 * siguen en el panel. Sin notas por delante dice que no hay nada que cantar.
 */
@Composable
internal fun ZonaRoadbook(
    contenido: ContenidoRoadbook,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag(ETIQUETA_ROADBOOK)) {
        if (horizontal) RoadbookHorizontal(contenido) else RoadbookVertical(contenido)
    }
}

@Composable
private fun RoadbookVertical(c: ContenidoRoadbook) {
    Column(
        modifier = Modifier.fillMaxSize().padding(MARGEN),
        verticalArrangement = Arrangement.spacedBy(MARGEN),
    ) {
        Cabecera(c, compacto = false)
        val primera = c.notas.visibles.firstOrNull()
        if (primera == null) {
            SinNotas(Modifier.weight(1f).fillMaxWidth())
        } else {
            NotaEnorme(primera, c.notas.simplificadas, Modifier.weight(1f).fillMaxWidth())
            c.notas.visibles.drop(1).forEachIndexed { i, visible ->
                FilaNota(visible, i + 1, c.notas.simplificadas, MEDIDAS_GRANDE, Modifier.fillMaxWidth())
            }
        }
        c.guiando?.progreso?.let { PieGuiado(it, Modifier.align(Alignment.CenterHorizontally)) }
    }
}

@Composable
private fun RoadbookHorizontal(c: ContenidoRoadbook) {
    Row(
        modifier = Modifier.fillMaxSize().padding(MARGEN),
        horizontalArrangement = Arrangement.spacedBy(MARGEN),
    ) {
        val primera = c.notas.visibles.firstOrNull()
        if (primera == null) {
            SinNotas(Modifier.weight(PESO_NOTA_ENORME).fillMaxHeight())
        } else {
            NotaEnorme(
                primera,
                c.notas.simplificadas,
                Modifier.weight(PESO_NOTA_ENORME).fillMaxHeight(),
                horizontal = true,
            )
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(MARGEN)) {
            Cabecera(c, compacto = true)
            c.notas.visibles.drop(1).forEachIndexed { i, visible ->
                FilaNota(visible, i + 1, c.notas.simplificadas, MEDIDAS_ROADBOOK_LADO, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Maniobra, alerta y cronómetro, compactos. */
@Composable
private fun Cabecera(
    c: ContenidoRoadbook,
    compacto: Boolean,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MARGEN)) {
        c.guiando?.let { BannerGuiado(it, c.variante, compacto = true) }
        BannerAlerta(c.alertas.aviso, c.alertas.frescura, compacto = true)
        CronoSuperior(c.crono, compacto = compacto || c.guiando != null)
    }
}

@Composable
private fun SinNotas(modifier: Modifier) {
    Column(
        modifier.testTag(ETIQUETA_ROADBOOK_VACIO),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Espacio.m, Alignment.CenterVertically),
    ) {
        Icon(
            Iconos.NotasGrandes,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(72.dp),
        )
        Text(
            text = stringResource(R.string.notas_grandes_vacio_titulo),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.notas_grandes_vacio_texto),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
