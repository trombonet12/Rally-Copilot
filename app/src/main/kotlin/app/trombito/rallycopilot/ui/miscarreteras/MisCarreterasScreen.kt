package app.trombito.rallycopilot.ui.miscarreteras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.OrdenCarreteras
import app.trombito.rallycopilot.ui.comun.CargandoWrc
import app.trombito.rallycopilot.ui.comun.EstadoVacioWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.OpcionSegmento
import app.trombito.rallycopilot.ui.comun.PantallaWrc
import app.trombito.rallycopilot.ui.comun.SelectorSegmentadoWrc
import app.trombito.rallycopilot.ui.comun.VarianteVacio
import app.trombito.rallycopilot.ui.mapa.ModoCalor
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_CARRETERAS_VACIO = "carreteras-vacio"
const val ETIQUETA_CARRETERAS_ERROR = "carreteras-error"
const val ETIQUETA_CARRETERAS_LISTA = "carreteras-lista"
const val ETIQUETA_CARRETERAS_ORDEN = "carreteras-orden"
const val ETIQUETA_CARRETERAS_ORDEN_AYUDA = "carreteras-orden-ayuda"

/** Lo que se puede hacer en «Mis carreteras»: volver, abrir una ficha y cambiar el orden y el modo del mapa. */
@Immutable
class AccionesMisCarreteras(
    val onVolver: () -> Unit = {},
    val onFicha: (String) -> Unit = {},
    val onOrden: (OrdenCarreteras) -> Unit = {},
    val onModo: (ModoCalor) -> Unit = {},
)

/**
 * «Mis carreteras»: arriba el mapa de calor de lo conducido (por uso o por velocidad media) y debajo una tarjeta por
 * carretera con sus kilómetros, viajes, ritmo y curvas por kilómetro, ordenable por uso, curvas o reciente; cada una
 * abre su ficha. Cierra con las curvas por grado de todo lo conducido.
 */
@Composable
fun MisCarreterasScreen(
    estado: MisCarreterasUiState,
    acciones: AccionesMisCarreteras,
    modifier: Modifier = Modifier,
    mapa: MapaDelCalor = MapaCalorReal,
) {
    PantallaWrc(
        titulo = stringResource(R.string.carreteras_titulo),
        onVolver = acciones.onVolver,
        modifier = modifier,
    ) {
        when {
            estado.cargando -> {
                CargandoWrc(stringResource(R.string.carreteras_calculando), Modifier.fillMaxSize())
            }

            estado.error -> {
                EstadoVacioWrc(
                    icono = Iconos.Informacion,
                    titulo = stringResource(R.string.carreteras_error_titulo),
                    texto = stringResource(R.string.carreteras_error_texto),
                    etiqueta = ETIQUETA_CARRETERAS_ERROR,
                    variante = VarianteVacio.ERROR,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            estado.vacio -> {
                EstadoVacioWrc(
                    icono = Iconos.Curvas,
                    titulo = stringResource(R.string.carreteras_vacio_titulo),
                    texto = stringResource(R.string.carreteras_vacio_texto),
                    etiqueta = ETIQUETA_CARRETERAS_VACIO,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                Contenido(estado, acciones, mapa)
            }
        }
    }
}

@Composable
private fun Contenido(
    estado: MisCarreterasUiState,
    acciones: AccionesMisCarreteras,
    mapa: MapaDelCalor,
) {
    val zona =
        DatosZonaMapa(
            modo = estado.modoCalor,
            trazos = estado.trazos,
            geoJson = estado.geoJson,
            esquinas = estado.esquinas,
            estiloMapaJson = estado.estiloMapaJson,
            hayVias = estado.hayVias,
        )
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(ETIQUETA_CARRETERAS_LISTA),
        contentPadding = PaddingValues(Espacio.l),
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        item(key = "mapa") { ZonaMapaCalor(zona, acciones.onModo, mapa) }
        if (estado.fichas.isNotEmpty()) {
            item(key = "orden") { CabeceraLista(estado.orden, acciones.onOrden) }
            items(estado.fichas, key = { it.id }) { ficha ->
                TarjetaCarretera(ficha, onClick = { acciones.onFicha(ficha.id) })
            }
        }
        item(key = "curvas") { TarjetaCurvasPorGrado(estado.curvas) }
    }
}

/** El título de la lista, el selector de orden y una línea que dice por qué está ordenada así. */
@Composable
private fun CabeceraLista(
    orden: OrdenCarreteras,
    onOrden: (OrdenCarreteras) -> Unit,
) {
    val opciones =
        listOf(
            OpcionSegmento(OrdenCarreteras.USO, stringResource(R.string.carreteras_orden_uso), Iconos.Regla),
            OpcionSegmento(OrdenCarreteras.CURVAS, stringResource(R.string.carreteras_orden_curvas), Iconos.Curvas),
            OpcionSegmento(
                OrdenCarreteras.RECIENTE,
                stringResource(R.string.carreteras_orden_reciente),
                Iconos.Cronometro,
            ),
        )
    val ayuda =
        when (orden) {
            OrdenCarreteras.USO -> R.string.carreteras_orden_ayuda_uso
            OrdenCarreteras.CURVAS -> R.string.carreteras_orden_ayuda_curvas
            OrdenCarreteras.RECIENTE -> R.string.carreteras_orden_ayuda_reciente
        }
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Text(
            stringResource(R.string.carreteras_lista_titulo),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        SelectorSegmentadoWrc(opciones, orden, onOrden, Modifier.testTag(ETIQUETA_CARRETERAS_ORDEN))
        Text(
            stringResource(ayuda),
            style = Ayuda,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(ETIQUETA_CARRETERAS_ORDEN_AYUDA),
        )
    }
}
