package app.trombito.rallycopilot.ui.reconocimiento

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.edicion.CambioNota
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.comun.VarianteTarjeta
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally

const val ETIQUETA_RESUMEN_RECONOCIMIENTO = "reconocimiento-resumen"
const val ETIQUETA_RESUMEN_SEGUNDA_PASADA = "reconocimiento-resumen-segunda"
const val ETIQUETA_RESUMEN_TERMINAR = "reconocimiento-resumen-terminar"
const val ETIQUETA_RESUMEN_CAMBIO = "reconocimiento-resumen-cambio"

private val ALTO_TACTIL = 48.dp
private val ALTO_MAXIMO_LISTA = 240.dp
private val ALTO_FILA = 40.dp
private val ANCHO_KM = 64.dp
private val LADO_ICONO = 32.dp
private val LADO_MARCA = 24.dp

/** Callbacks del resumen: «Hacer 2.ª pasada» (solo tras la 1.ª) y «Terminar». */
data class AccionesResumenReconocimiento(
    val onSegundaPasada: () -> Unit = {},
    val onTerminar: () -> Unit = {},
)

/**
 * Resumen de la pasada de reconocimiento que acaba de terminar, en una tarjeta de carbón abajo del mapa: título, lo
 * cambiado (km, antes y después) y los botones. No pinta nada sin [resumen]; quien lo monta solo se lo da con el coche
 * parado o la pasada terminada ([ReconocimientoViewModel]). TalkBack lo lee al salir.
 */
@Composable
fun BoxScope.ResumenReconocimiento(
    resumen: ResumenPasada?,
    acciones: AccionesResumenReconocimiento,
    modifier: Modifier = Modifier,
) {
    if (resumen == null) return
    TarjetaWrc(
        modifier =
            modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(Espacio.l)
                .testTag(ETIQUETA_RESUMEN_RECONOCIMIENTO)
                .semantics { liveRegion = LiveRegionMode.Polite },
        variante = VarianteTarjeta.MAPA,
        opaca = true,
        acentoRojo = true,
    ) {
        Text(
            text = stringResource(R.string.reconocimiento_resumen_titulo, resumen.numero),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = AmbarSobreCarbon,
        )
        Text(
            text = textoCuenta(resumen.cambios.size),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = Espacio.s),
        )
        ListaCambios(resumen.cambios, resumen.iconos)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Espacio.m),
            horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            if (resumen.hayOtraPasada) {
                Button(
                    onClick = acciones.onSegundaPasada,
                    modifier = Modifier.weight(1f).heightIn(min = ALTO_TACTIL).testTag(ETIQUETA_RESUMEN_SEGUNDA_PASADA),
                ) { Text(stringResource(R.string.reconocimiento_segunda_pasada), maxLines = 1) }
            }
            OutlinedButton(
                onClick = acciones.onTerminar,
                modifier = Modifier.weight(1f).heightIn(min = ALTO_TACTIL).testTag(ETIQUETA_RESUMEN_TERMINAR),
            ) { Text(stringResource(R.string.reconocimiento_terminar), maxLines = 1) }
        }
    }
}

@Composable
private fun textoCuenta(cambios: Int): String =
    if (cambios == 0) {
        stringResource(R.string.reconocimiento_resumen_vacio)
    } else {
        pluralStringResource(R.plurals.reconocimiento_resumen_cuenta, cambios, cambios)
    }

@Composable
private fun ListaCambios(
    cambios: List<CambioNota>,
    iconos: Map<String, Int>,
) {
    if (cambios.isEmpty()) return
    LazyColumn(
        modifier = Modifier.heightIn(max = ALTO_MAXIMO_LISTA),
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        items(cambios) { FilaCambio(it, iconos) }
    }
}

/**
 * Un cambio en una fila: el km y, a su derecha, el icono de la nota de antes, una flecha y el de después. Una nota
 * añadida lleva un «+» en vez del de antes y una quitada, un «−» en vez del de después. La frase completa es la
 * descripción para TalkBack; una frase sin icono se escribe.
 */
@Composable
private fun FilaCambio(
    cambio: CambioNota,
    iconos: Map<String, Int>,
) {
    val descripcion = descripcion(cambio)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_FILA)
                .testTag(ETIQUETA_RESUMEN_CAMBIO)
                .semantics(mergeDescendants = true) { contentDescription = descripcion },
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = FormatoEs.distancia(cambio.posicionM),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(ANCHO_KM),
        )
        when (cambio) {
            is CambioNota.Cambiada -> {
                NotaDelCambio(cambio.antes, iconos)
                Marca(Iconos.FlechaDerecha, MaterialTheme.colorScheme.primary)
                NotaDelCambio(cambio.despues, iconos)
            }

            is CambioNota.Anadida -> {
                Marca(Iconos.Mas, LocalColoresRally.current.deltaMejor)
                NotaDelCambio(cambio.despues, iconos)
            }

            is CambioNota.Quitada -> {
                NotaDelCambio(cambio.antes, iconos)
                Marca(Iconos.Menos, MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun NotaDelCambio(
    frase: String,
    iconos: Map<String, Int>,
) {
    val icono = iconos[frase]
    if (icono != null) {
        Icon(
            painter = painterResource(icono),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(LADO_ICONO),
        )
    } else {
        Text(text = frase, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
    }
}

@Composable
private fun Marca(
    icono: ImageVector,
    color: Color,
) {
    Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(LADO_MARCA))
}

@Composable
private fun descripcion(cambio: CambioNota): String {
    val km = FormatoEs.distancia(cambio.posicionM)
    return when (cambio) {
        is CambioNota.Cambiada -> {
            stringResource(R.string.reconocimiento_cambio_cambiada, km, cambio.antes, cambio.despues)
        }

        is CambioNota.Anadida -> {
            stringResource(R.string.reconocimiento_cambio_anadida, km, cambio.despues)
        }

        is CambioNota.Quitada -> {
            stringResource(R.string.reconocimiento_cambio_quitada, km, cambio.antes)
        }
    }
}
