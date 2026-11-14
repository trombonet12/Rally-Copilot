package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.SentidoVia
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Rotulo

const val ETIQUETA_CABECERA_PREFIJO = "tramos-cabecera-"

private val LADO_ICONO_CABECERA = 20.dp

/** Pestaña «Carreteras»: una cabecera por carretera y sentido y debajo sus tramos, de menor a mayor kilómetro. */
@Composable
fun ListaCarreteras(
    grupos: List<GrupoCarretera>,
    trazas: Map<String, List<PuntoGeo>>,
    acciones: AccionesMisTramos,
    pistaLibre: Boolean,
) {
    val primero =
        grupos
            .firstOrNull()
            ?.tramos
            ?.firstOrNull()
            ?.id
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(ETIQUETA_TRAMOS_LISTA),
        contentPadding = PaddingValues(Espacio.l),
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        grupos.forEach { grupo ->
            item(key = "cabecera-${grupo.clave}") { CabeceraCarretera(grupo, Modifier.animateItem()) }
            items(grupo.tramos, key = { it.id }) { tramo ->
                // La pista de la estrella solo señala la primera fila de toda la lista.
                val conPista = pistaLibre && tramo.id == primero
                FilaTramoDeslizable(tramo, acciones, Modifier.animateItem(), trazas[tramo.id], conPista)
            }
        }
    }
}

/** «Ma-15 · hacia Manacor»: la carretera y hacia dónde se va (o, sin destino, hacia dónde crecen los kilómetros). */
@Composable
private fun CabeceraCarretera(
    grupo: GrupoCarretera,
    modifier: Modifier = Modifier,
) {
    val texto = textoCabecera(grupo)
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Espacio.s).testTag(ETIQUETA_CABECERA_PREFIJO + grupo.clave),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        Icon(
            imageVector = Iconos.Curvas,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(LADO_ICONO_CABECERA),
        )
        Text(
            text = texto,
            style = Rotulo,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun textoCabecera(grupo: GrupoCarretera): String =
    when {
        grupo.carretera.isEmpty() -> {
            stringResource(R.string.mistramos_via_cabecera_otras)
        }

        grupo.destino != null -> {
            stringResource(R.string.mistramos_via_cabecera_destino, grupo.carretera, grupo.destino)
        }

        grupo.sentido == SentidoVia.CRECIENTE -> {
            stringResource(R.string.mistramos_via_cabecera_creciente, grupo.carretera)
        }

        grupo.sentido == SentidoVia.DECRECIENTE -> {
            stringResource(R.string.mistramos_via_cabecera_decreciente, grupo.carretera)
        }

        else -> {
            grupo.carretera
        }
    }
