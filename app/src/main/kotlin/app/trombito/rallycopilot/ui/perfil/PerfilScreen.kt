package app.trombito.rallycopilot.ui.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.PerfilPiloto
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.EstadoVacioWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.ListaEsqueletoWrc
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.mistramos.formatearKilometros
import app.trombito.rallycopilot.ui.mistramos.formatearVelocidadKmh
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Rotulo
import app.trombito.rallycopilot.ui.theme.Titulo

const val ETIQUETA_PERFIL_CARGANDO = "perfil-cargando"
const val ETIQUETA_PERFIL_VACIO = "perfil-vacio"
const val ETIQUETA_PERFIL_KM = "perfil-km"
const val ETIQUETA_PERFIL_TRAMOS = "perfil-tramos"
const val ETIQUETA_PERFIL_PASADAS = "perfil-pasadas"
const val ETIQUETA_PERFIL_VELOCIDAD = "perfil-velocidad"

/** Cifra de una tarjeta de totales: 28 sp, cursiva Black, sin el apretado de las cifras grandes. */
private val CifraTotal = Rotulo.copy(fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = 0.sp)

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
                Box(Modifier.fillMaxSize().testTag(ETIQUETA_PERFIL_CARGANDO)) { ListaEsqueletoWrc(filas = 3) }
            }

            estado.vacio || perfil == null -> {
                EstadoVacioWrc(
                    icono = Iconos.Cronometro,
                    titulo = stringResource(R.string.perfil_vacio_titulo),
                    texto = stringResource(R.string.perfil_vacio_texto),
                    etiqueta = ETIQUETA_PERFIL_VACIO,
                    modifier = Modifier.fillMaxSize(),
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
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        item { TotalesPerfil(perfil) }
        item { TituloSeccion(R.string.perfil_mejores_titulo) }
        if (estado.mejores.isEmpty()) {
            item { Aviso(R.string.perfil_sin_tramos) }
        } else {
            mejoresTiempos(estado, acciones)
            item { TituloSeccion(R.string.perfil_evolucion_titulo) }
            item { EvolucionTramo(estado) }
        }
        item { TituloSeccion(R.string.perfil_historial_titulo) }
        historial(perfil, acciones)
    }
}

@Composable
private fun TituloSeccion(texto: Int) {
    Text(
        text = stringResource(texto),
        style = Titulo,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Espacio.s).semantics { heading() },
    )
}

/** Frase corta en una tarjeta baja: lo que falta para que la sección tenga contenido. */
@Composable
internal fun Aviso(texto: Int) {
    TarjetaWrc(Modifier.fillMaxWidth(), relleno = Espacio.m) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            Icon(Iconos.Bandera, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            Text(
                text = stringResource(texto),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Cuatro tarjetas en fila: icono ámbar de 24 dp, cifra de 28 sp en cursiva y la etiqueta (que ya da la unidad). */
@Composable
private fun TotalesPerfil(perfil: PerfilPiloto) {
    Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Total(
            icono = Iconos.Curvas,
            etiqueta = R.string.perfil_km,
            valor = formatearKilometros(perfil.kmTotales).removeSuffix(" km"),
            tag = ETIQUETA_PERFIL_KM,
            modifier = Modifier.weight(1f),
        )
        Total(
            icono = Iconos.Bandera,
            etiqueta = R.string.perfil_tramos,
            valor = perfil.numeroTramos.toString(),
            tag = ETIQUETA_PERFIL_TRAMOS,
            modifier = Modifier.weight(1f),
        )
        Total(
            icono = Iconos.Cronometro,
            etiqueta = R.string.perfil_pasadas,
            valor = perfil.numeroPasadas.toString(),
            tag = ETIQUETA_PERFIL_PASADAS,
            modifier = Modifier.weight(1f),
        )
        Total(
            icono = Iconos.Rayo,
            etiqueta = R.string.perfil_velocidad,
            valor =
                perfil.velMediaKmh?.let { formatearVelocidadKmh(it).removeSuffix(" km/h") }
                    ?: stringResource(R.string.mistramos_sin_tiempo),
            tag = ETIQUETA_PERFIL_VELOCIDAD,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Total(
    icono: ImageVector,
    etiqueta: Int,
    valor: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    TarjetaWrc(
        modifier = modifier.fillMaxHeight().testTag(tag).semantics(mergeDescendants = true) {},
        relleno = Espacio.s,
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icono,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(Medidas.iconoLista),
            )
            Text(
                text = valor,
                style = CifraTotal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.padding(top = Espacio.xs),
            )
            Text(
                text = stringResource(etiqueta),
                style = EtiquetaIcono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
