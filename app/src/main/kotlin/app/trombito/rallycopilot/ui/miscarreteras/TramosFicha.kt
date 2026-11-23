package app.trombito.rallycopilot.ui.miscarreteras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.ui.comun.FilaWrc
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.mistramos.GrupoCarretera
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas

/** Prefijo de la etiqueta de prueba de cada fila de tramo: `ficha-tramo-<id del tramo>`. */
const val ETIQUETA_FICHA_TRAMO_PREFIJO = "ficha-tramo-"
const val ETIQUETA_FICHA_DETALLE = "ficha-detalle"
const val ETIQUETA_FICHA_ABRIR_TRAMO = "ficha-abrir-tramo"
const val ETIQUETA_FICHA_TRAMOS_VACIO = "ficha-tramos-vacio"
const val ETIQUETA_FICHA_GRUPO_PREFIJO = "ficha-grupo-"

/** El rango de kilómetros de un tramo de vía con hitos («km 20,1 → 32,4»); null si no los tiene. */
@Composable
internal fun rangoDeTramo(tramo: ResumenTramo): String? {
    val desde = tramo.via?.pkDesde
    val hasta = tramo.via?.pkHasta
    return if (desde != null && hasta != null) {
        stringResource(R.string.ficha_rango_km, formatoKm(minOf(desde, hasta)), formatoKm(maxOf(desde, hasta)))
    } else {
        null
    }
}

/** «5,0 km · 3 pasadas», con «· Media 03:30,1» cuando hay varias pasadas que promediar. */
@Composable
private fun medidasDeTramo(tramo: ResumenTramo): String {
    val base =
        stringResource(
            R.string.ficha_tramo_medidas,
            FormatoEs.distancia(tramo.longitudM),
            pluralStringResource(R.plurals.ficha_pasadas, tramo.pasadas, tramo.pasadas),
        )
    val media = tramo.mediaMs?.takeIf { tramo.pasadas >= 2 }
    return if (media != null) {
        base + " · " + stringResource(R.string.ficha_tramo_media, FormatoEs.tiempo(media))
    } else {
        base
    }
}

/**
 * Los tramos de la carretera por sentido: una cabecera con el destino y, debajo, una fila por tramo con sus kilómetros,
 * su mejor tiempo y la media. Tocar una fila abre el tramo.
 */
@Composable
internal fun ListaTramosFicha(
    grupos: List<GrupoCarretera>,
    onAbrirTramo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Text(
            stringResource(R.string.ficha_tramos_titulo),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        if (grupos.isEmpty()) {
            Text(
                stringResource(R.string.ficha_tramos_vacio),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(ETIQUETA_FICHA_TRAMOS_VACIO),
            )
        }
        grupos.forEach { grupo ->
            Text(
                etiquetaSentido(grupo.sentido, grupo.destino),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(ETIQUETA_FICHA_GRUPO_PREFIJO + grupo.clave),
            )
            grupo.tramos.forEach { tramo -> FilaTramoFicha(tramo, onAbrirTramo) }
        }
    }
}

@Composable
private fun FilaTramoFicha(
    tramo: ResumenTramo,
    onAbrirTramo: (String) -> Unit,
) {
    val rango = rangoDeTramo(tramo)
    val titulo = tramo.nombrePropio ?: rango ?: tramo.nombre
    val mejor =
        tramo.mejorTotalMs?.let { stringResource(R.string.ficha_tramo_mejor, FormatoEs.tiempo(it)) }
            ?: stringResource(R.string.ficha_tramo_sin_tiempo)
    val detalle = listOfNotNull(rango.takeIf { tramo.nombrePropio != null }, medidasDeTramo(tramo)).joinToString(" · ")
    FilaWrc(
        titulo = titulo,
        detalle = detalle,
        valor = mejor,
        navega = true,
        onClick = { onAbrirTramo(tramo.id) },
        modifier = Modifier.testTag(ETIQUETA_FICHA_TRAMO_PREFIJO + tramo.id),
    )
}

/** El tramo tocado en la regla: su rango, hacia dónde va, sus medidas y un botón para abrirlo. */
@Composable
internal fun DetalleTramoFicha(
    tramo: ResumenTramo,
    destino: String?,
    onAbrir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sentido = tramo.via?.sentido
    val mejor = tramo.mejorTotalMs?.let { stringResource(R.string.ficha_tramo_mejor, FormatoEs.tiempo(it)) }
    TarjetaWrc(modifier.testTag(ETIQUETA_FICHA_DETALLE), activa = true) {
        Text(
            rangoDeTramo(tramo) ?: tramo.nombre,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (sentido != null) {
            Text(
                etiquetaSentido(sentido, destino),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            medidasDeTramo(tramo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            mejor ?: stringResource(R.string.ficha_tramo_sin_tiempo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Button(
            onClick = onAbrir,
            modifier = Modifier.heightIn(min = Medidas.zonaTactil).testTag(ETIQUETA_FICHA_ABRIR_TRAMO),
        ) {
            Text(stringResource(R.string.ficha_tramo_abrir))
        }
    }
}
