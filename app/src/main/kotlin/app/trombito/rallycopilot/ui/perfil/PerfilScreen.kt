package app.trombito.rallycopilot.ui.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.PerfilPiloto
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.EstadoVacio
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.mistramos.formatearKilometros
import app.trombito.rallycopilot.ui.mistramos.formatearVelocidadKmh
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_PERFIL_CARGANDO = "perfil-cargando"
const val ETIQUETA_PERFIL_VACIO = "perfil-vacio"
const val ETIQUETA_PERFIL_KM = "perfil-km"
const val ETIQUETA_PERFIL_TRAMOS = "perfil-tramos"
const val ETIQUETA_PERFIL_PASADAS = "perfil-pasadas"
const val ETIQUETA_PERFIL_VELOCIDAD = "perfil-velocidad"

/** Callbacks del perfil. */
data class AccionesPerfil(
    val onVolver: () -> Unit = {},
    val onSeleccionar: (tramoId: String) -> Unit = {},
    val onVerAnalisis: (tramoId: String, pasadaId: Long) -> Unit = { _, _ -> },
)

/** Perfil del piloto: totales, mejores tiempos por tramo, evolución del tramo elegido e historial reciente. */
@Composable
fun PerfilScreen(
    estado: PerfilUiState,
    acciones: AccionesPerfil,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        BarraSuperior(titulo = stringResource(R.string.inicio_perfil), onVolver = acciones.onVolver)
        val perfil = estado.perfil
        when {
            estado.cargando -> {
                Box(Modifier.fillMaxSize().testTag(ETIQUETA_PERFIL_CARGANDO), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            estado.vacio || perfil == null -> {
                EstadoVacio(
                    icono = Iconos.Cronometro,
                    titulo = stringResource(R.string.perfil_vacio_titulo),
                    texto = stringResource(R.string.perfil_vacio_texto),
                    etiqueta = ETIQUETA_PERFIL_VACIO,
                )
            }

            else -> {
                ContenidoPerfil(estado, perfil, acciones)
            }
        }
    }
}

@Composable
private fun ContenidoPerfil(
    estado: PerfilUiState,
    perfil: PerfilPiloto,
    acciones: AccionesPerfil,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Espacio.l),
        verticalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        item { TotalesPerfil(perfil) }
        item { Titulo(R.string.perfil_mejores_titulo) }
        if (estado.mejores.isEmpty()) {
            item { Aviso(R.string.perfil_sin_tramos) }
        } else {
            mejoresTiempos(estado, acciones)
            item { Titulo(R.string.perfil_evolucion_titulo) }
            item { EvolucionTramo(estado) }
        }
        item { Titulo(R.string.perfil_historial_titulo) }
        historial(perfil, acciones)
    }
}

@Composable
private fun Titulo(texto: Int) {
    Text(
        text = stringResource(texto).uppercase(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Espacio.m).semantics { heading() },
    )
}

@Composable
internal fun Aviso(texto: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Espacio.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            Icon(Iconos.Bandera, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            Text(
                text = stringResource(texto),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TotalesPerfil(perfil: PerfilPiloto) {
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            Total(R.string.perfil_km, formatearKilometros(perfil.kmTotales), ETIQUETA_PERFIL_KM, Modifier.weight(1f))
            Total(R.string.perfil_tramos, perfil.numeroTramos.toString(), ETIQUETA_PERFIL_TRAMOS, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            Total(
                R.string.perfil_pasadas,
                perfil.numeroPasadas.toString(),
                ETIQUETA_PERFIL_PASADAS,
                Modifier.weight(1f),
            )
            Total(
                R.string.perfil_velocidad,
                perfil.velMediaKmh?.let(::formatearVelocidadKmh) ?: stringResource(R.string.mistramos_sin_tiempo),
                ETIQUETA_PERFIL_VELOCIDAD,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Total(
    etiqueta: Int,
    valor: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag(tag).semantics(mergeDescendants = true) {},
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.fillMaxWidth().padding(Espacio.m), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            Text(
                text = stringResource(etiqueta).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = valor,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
