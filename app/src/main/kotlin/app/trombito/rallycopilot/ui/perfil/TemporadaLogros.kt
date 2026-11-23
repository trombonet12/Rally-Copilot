package app.trombito.rallycopilot.ui.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.perfil.CarreteraCarne
import app.trombito.rallycopilot.motor.analisis.CategoriaHito
import app.trombito.rallycopilot.motor.analisis.Hito
import app.trombito.rallycopilot.motor.analisis.RecordBatido
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.PlacaCarretera
import app.trombito.rallycopilot.ui.comun.TamanoPlaca
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.mistramos.formatearFechaCorta
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_TEMPORADA_HITOS = "temporada-hitos"
const val ETIQUETA_TEMPORADA_HITO_SIGUIENTE = "temporada-hito-siguiente"
const val ETIQUETA_TEMPORADA_HITO_PREFIJO = "temporada-hito-"
const val ETIQUETA_TEMPORADA_CARNE = "temporada-carne"
const val ETIQUETA_TEMPORADA_RECORDS = "temporada-records"
const val ETIQUETA_TEMPORADA_RECORD_PREFIJO = "temporada-record-"

private const val PORCIENTO = 100
private const val RECORDS_VISIBLES = 10
private val ANCHO_MAXIMO_PLACA = 150.dp

private fun unidadDe(categoria: CategoriaHito): Int =
    when (categoria) {
        CategoriaHito.KM -> R.string.perfil_unidad_km
        CategoriaHito.CURVAS -> R.string.perfil_unidad_curvas
        CategoriaHito.HORQUILLAS -> R.string.perfil_unidad_horquillas
        CategoriaHito.CARRETERAS -> R.string.perfil_unidad_carreteras
        CategoriaHito.RACHA -> R.string.perfil_unidad_semanas
    }

/** Los hitos conseguidos (el más reciente primero, con su fecha) y el siguiente con su barra de progreso. */
@Composable
internal fun TarjetaHitos(
    hitos: List<Hito>,
    siguiente: Hito?,
) {
    val conseguidos = hitos.filter { it.conseguido }.sortedByDescending { it.conseguidoMs }
    TarjetaWrc(Modifier.fillMaxWidth().testTag(ETIQUETA_TEMPORADA_HITOS), relleno = Espacio.m) {
        EtiquetaConIcono(Iconos.Bandera, stringResource(R.string.perfil_hitos_titulo))
        if (siguiente != null) {
            SiguienteHito(siguiente)
        } else {
            Text(
                text = stringResource(R.string.perfil_hitos_todos),
                style = Cuerpo,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (conseguidos.isEmpty()) {
            Text(
                text = stringResource(R.string.perfil_hitos_ninguno),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        conseguidos.forEach { HitoConseguido(it) }
    }
}

@Composable
private fun SiguienteHito(hito: Hito) {
    val unidad = stringResource(unidadDe(hito.categoria))
    val avance = stringResource(R.string.perfil_hitos_avance, hito.valor.toInt(), hito.umbral, unidad)
    val porcentaje = (hito.progreso * PORCIENTO).toInt()
    Column(
        Modifier.fillMaxWidth().testTag(ETIQUETA_TEMPORADA_HITO_SIGUIENTE).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        Text(
            text = stringResource(R.string.perfil_hitos_siguiente, hito.titulo),
            style = Cuerpo.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        BarraProgresoWrc(
            progreso = hito.progreso.toFloat(),
            descripcion = stringResource(R.string.perfil_hito_barra_descripcion, hito.titulo, porcentaje),
        )
        Text(
            text = avance,
            style = Ayuda.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HitoConseguido(hito: Hito) {
    val fecha = hito.conseguidoMs?.let { formatearFechaCorta(it) }.orEmpty()
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(ETIQUETA_TEMPORADA_HITO_PREFIJO + hito.id)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Iconos.Estrella, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = stringResource(R.string.perfil_hito_conseguido, hito.titulo, fecha),
            style = Cuerpo,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Carné de carreteras: una placa por cada carretera distinta conducida, con un contador «×3» si la has repetido. Sin
 * catálogo de carreteras por conducir: solo cuenta lo que has hecho.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TarjetaCarne(
    carne: List<CarreteraCarne>,
    distintas: Int,
) {
    TarjetaWrc(Modifier.fillMaxWidth().testTag(ETIQUETA_TEMPORADA_CARNE), relleno = Espacio.m) {
        EtiquetaConIcono(Iconos.Direcciones, stringResource(R.string.perfil_carne_titulo))
        Text(
            text = pluralStringResource(R.plurals.perfil_carne_resumen, distintas, distintas),
            style = Cuerpo.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (carne.isEmpty()) {
            Text(
                text = stringResource(R.string.perfil_carne_vacio),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
                verticalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                carne.forEach { PlacaDeCarne(it) }
            }
        }
    }
}

@Composable
private fun PlacaDeCarne(carretera: CarreteraCarne) {
    val descripcion =
        stringResource(
            R.string.perfil_carne_placa,
            carretera.nombre,
            pluralStringResource(R.plurals.perfil_viajes, carretera.viajes, carretera.viajes),
        )
    Row(
        Modifier.clearAndSetSemantics { contentDescription = descripcion },
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlacaCarretera(carretera.nombre, Modifier.widthIn(max = ANCHO_MAXIMO_PLACA), TamanoPlaca.PEQUENA)
        if (carretera.viajes > 1) {
            Text(
                text = stringResource(R.string.perfil_carne_contador, carretera.viajes),
                style = Ayuda.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** Vitrina de récords: solo los tiempos que mejoran uno anterior del mismo tramo, con su fecha y la mejora. */
@Composable
internal fun TarjetaRecords(records: List<RecordBatido>) {
    TarjetaWrc(Modifier.fillMaxWidth().testTag(ETIQUETA_TEMPORADA_RECORDS), relleno = Espacio.m) {
        EtiquetaConIcono(Iconos.Estrella, stringResource(R.string.perfil_records_titulo))
        if (records.isEmpty()) {
            Text(
                text = stringResource(R.string.perfil_records_vacio),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        records.take(RECORDS_VISIBLES).forEach { FilaRecord(it) }
    }
}

@Composable
private fun FilaRecord(record: RecordBatido) {
    Column(
        Modifier
            .fillMaxWidth()
            .testTag(ETIQUETA_TEMPORADA_RECORD_PREFIJO + record.pasadaId)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = record.nombreTramo,
            style = Cuerpo.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text =
                stringResource(
                    R.string.perfil_record_detalle,
                    FormatoEs.tiempo(record.tiempoMs),
                    FormatoEs.tiempo(record.mejoraMs),
                    formatearFechaCorta(record.fechaMs),
                ),
            style = Ayuda.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
