package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.TarjetaDato
import app.trombito.rallycopilot.ui.perfil.GraficoEvolucion
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Titulo

const val ETIQUETA_DETALLE_MEDIA = "detalle-media"
const val ETIQUETA_DETALLE_CURVAS = "detalle-curvas"
const val ETIQUETA_DETALLE_CURVAS_KM = "detalle-curvas-km"
const val ETIQUETA_DETALLE_EVOLUCION = "detalle-evolucion"

private const val METROS_KM = 1_000.0

/** Pasadas que hacen falta para que la gráfica de evolución diga algo. */
private const val PASADAS_PARA_EVOLUCION = 3

/** Tres datos del tramo: velocidad media, curvas y curvas por km (la distancia y la hora ya van en la fila). */
@Composable
internal fun MedidasTramo(
    tramo: ResumenTramo,
    curvas: Int?,
) {
    val sinDato = stringResource(R.string.mistramos_sin_tiempo)
    val porKm = curvas?.let { if (tramo.longitudM > 0.0) it / (tramo.longitudM / METROS_KM) else null }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        TarjetaDato(
            cifra = tramo.velocidadMediaKmh()?.let { Math.round(it).toString() } ?: sinDato,
            etiqueta = stringResource(R.string.detalle_media),
            modifier = Modifier.weight(1f).testTag(ETIQUETA_DETALLE_MEDIA),
            icono = Iconos.Velocimetro,
            unidad = stringResource(R.string.detalle_unidad_kmh),
        )
        TarjetaDato(
            cifra = curvas?.toString() ?: sinDato,
            etiqueta = stringResource(R.string.detalle_curvas),
            modifier = Modifier.weight(1f).testTag(ETIQUETA_DETALLE_CURVAS),
            icono = Iconos.Curvas,
        )
        TarjetaDato(
            cifra = porKm?.let { FormatoEs.decimal(it) } ?: sinDato,
            etiqueta = stringResource(R.string.detalle_curvas_km),
            modifier = Modifier.weight(1f).testTag(ETIQUETA_DETALLE_CURVAS_KM),
            icono = Iconos.CurvasSuaves,
        )
    }
}

/** «Evolución»: el tiempo de cada pasada por fecha; con menos de tres pasadas no dice nada y no se enseña. */
internal fun LazyListScope.seccionEvolucion(pasadas: List<FilaPasada>) {
    if (pasadas.size < PASADAS_PARA_EVOLUCION) return
    item(key = "evolucion") {
        Column(Modifier.testTag(ETIQUETA_DETALLE_EVOLUCION), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            Text(
                text = stringResource(R.string.detalle_evolucion_titulo),
                style = Titulo,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            GraficoEvolucion(evolucionDe(pasadas))
            Text(
                text = stringResource(R.string.detalle_evolucion_leyenda),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
