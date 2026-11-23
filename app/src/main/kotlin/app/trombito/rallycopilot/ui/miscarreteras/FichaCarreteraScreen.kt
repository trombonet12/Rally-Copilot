package app.trombito.rallycopilot.ui.miscarreteras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.FichaCarretera
import app.trombito.rallycopilot.ui.comun.CargandoWrc
import app.trombito.rallycopilot.ui.comun.EstadoVacioWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.PantallaWrc
import app.trombito.rallycopilot.ui.comun.PlacaCarretera
import app.trombito.rallycopilot.ui.comun.TamanoPlaca
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.comun.VarianteVacio
import app.trombito.rallycopilot.ui.mapa.ModoCalor
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_FICHA_LISTA = "ficha-lista"
const val ETIQUETA_FICHA_CABECERA = "ficha-cabecera"
const val ETIQUETA_FICHA_ERROR = "ficha-error"
const val ETIQUETA_FICHA_NO_ENCONTRADA = "ficha-no-encontrada"
const val ETIQUETA_FICHA_CURVAS = "ficha-curvas"
const val ETIQUETA_FICHA_CURVAS_FILA_PREFIJO = "ficha-curvas-"

/** Lo que se puede hacer en la ficha: volver, abrir un tramo, tocar una franja de la regla y cambiar el mapa. */
@Immutable
class AccionesFichaCarretera(
    val onVolver: () -> Unit = {},
    val onAbrirTramo: (String) -> Unit = {},
    val onFranja: (String) -> Unit = {},
    val onModo: (ModoCalor) -> Unit = {},
)

/**
 * Ficha de una carretera: lo esencial arriba (placa, kilómetros, viajes, ritmo, curvas por km), el mapa con solo lo
 * conducido en ella, el ritmo en cada sentido, sus curvas por grado, sus tramos de vía como franjas sobre una regla de
 * kilómetros (con el detalle del que se toque) y la lista de esos tramos con su mejor tiempo y su media.
 */
@Composable
fun FichaCarreteraScreen(
    estado: FichaCarreteraUiState,
    acciones: AccionesFichaCarretera,
    modifier: Modifier = Modifier,
    mapa: MapaDelCalor = MapaCalorReal,
) {
    val ficha = estado.ficha
    PantallaWrc(
        titulo = ficha?.let { it.ref ?: it.nombre } ?: stringResource(R.string.ficha_titulo),
        onVolver = acciones.onVolver,
        modifier = modifier,
    ) {
        when {
            estado.cargando -> {
                CargandoWrc(stringResource(R.string.ficha_cargando), Modifier.fillMaxSize())
            }

            estado.error -> {
                EstadoVacioWrc(
                    icono = Iconos.Informacion,
                    titulo = stringResource(R.string.ficha_error_titulo),
                    texto = stringResource(R.string.ficha_error_texto),
                    etiqueta = ETIQUETA_FICHA_ERROR,
                    variante = VarianteVacio.ERROR,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            ficha == null -> {
                EstadoVacioWrc(
                    icono = Iconos.Curvas,
                    titulo = stringResource(R.string.ficha_no_encontrada_titulo),
                    texto = stringResource(R.string.ficha_no_encontrada_texto),
                    etiqueta = ETIQUETA_FICHA_NO_ENCONTRADA,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                Contenido(estado, ficha, acciones, mapa)
            }
        }
    }
}

@Composable
private fun Contenido(
    estado: FichaCarreteraUiState,
    ficha: FichaCarretera,
    acciones: AccionesFichaCarretera,
    mapa: MapaDelCalor,
) {
    val zona =
        DatosZonaMapa(
            modo = estado.modoCalor,
            trazos = estado.trazos,
            geoJson = estado.geoJson,
            esquinas = estado.esquinas,
            estiloMapaJson = estado.estiloMapaJson,
            hayVias = true,
        )
    val elegido = estado.tramos.firstOrNull { it.id == estado.seleccionado }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(ETIQUETA_FICHA_LISTA),
        contentPadding = PaddingValues(Espacio.l),
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        item(key = "cabecera") { CabeceraFicha(ficha) }
        item(key = "mapa") { ZonaMapaCalor(zona, acciones.onModo, mapa) }
        item(key = "sentidos") { SentidosFicha(ficha, estado.destinos) }
        item(key = "curvas") {
            TarjetaCurvasPorGrado(
                ficha.curvas,
                etiqueta = ETIQUETA_FICHA_CURVAS,
                prefijoFila = ETIQUETA_FICHA_CURVAS_FILA_PREFIJO,
            )
        }
        item(key = "regla") { TarjetaRegla(estado, acciones.onFranja) }
        if (elegido != null) {
            item(key = "detalle") {
                val destino = elegido.via?.sentido?.let { estado.destinos[it] }
                DetalleTramoFicha(elegido, destino, onAbrir = { acciones.onAbrirTramo(elegido.id) })
            }
        }
        item(key = "tramos") { ListaTramosFicha(estado.grupos, acciones.onAbrirTramo) }
    }
}

/** La carretera de un vistazo: su placa grande y, a su lado, el nombre, el resumen y la última vez. */
@Composable
private fun CabeceraFicha(ficha: FichaCarretera) {
    TarjetaWrc(Modifier.testTag(ETIQUETA_FICHA_CABECERA)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
            ficha.ref?.let { PlacaCarretera(it, tamano = TamanoPlaca.GRANDE) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                ficha.nombre?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    resumenDeCarretera(ficha),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ficha.ultimaVezMs?.let {
                    Text(
                        stringResource(R.string.carreteras_ultima_vez, formatoFecha(it)),
                        style = Ayuda,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** La regla de kilómetros con los tramos como franjas; sin ningún tramo con kilómetros, una línea que lo explica. */
@Composable
private fun TarjetaRegla(
    estado: FichaCarreteraUiState,
    onFranja: (String) -> Unit,
) {
    TarjetaWrc(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.ficha_regla_titulo),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        val regla = estado.regla
        if (regla == null) {
            Text(
                stringResource(R.string.ficha_regla_sin_km),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(ETIQUETA_FICHA_REGLA_VACIA),
            )
        } else {
            Text(
                stringResource(R.string.ficha_regla_ayuda),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Espacio.s),
            )
            ReglaDeTramos(regla, estado.destinos, estado.seleccionado, onFranja)
        }
    }
}
