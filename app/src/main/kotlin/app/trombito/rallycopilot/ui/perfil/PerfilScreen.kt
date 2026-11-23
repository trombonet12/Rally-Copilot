package app.trombito.rallycopilot.ui.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.perfil.TableroPiloto
import app.trombito.rallycopilot.domain.perfil.UltimoViaje
import app.trombito.rallycopilot.motor.analisis.ResumenMes
import app.trombito.rallycopilot.ui.comun.EstadoVacioWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.ListaEsqueletoWrc
import app.trombito.rallycopilot.ui.comun.OpcionSegmento
import app.trombito.rallycopilot.ui.comun.PantallaWrc
import app.trombito.rallycopilot.ui.comun.SelectorSegmentadoWrc
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.comun.VarianteVacio
import app.trombito.rallycopilot.ui.mistramos.formatearDistanciaTramo
import app.trombito.rallycopilot.ui.mistramos.formatearFechaCorta
import app.trombito.rallycopilot.ui.mistramos.formatearFechaHora
import app.trombito.rallycopilot.ui.mistramos.formatearKilometros
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono
import app.trombito.rallycopilot.ui.theme.Medidas
import java.time.Instant
import java.time.ZoneId

const val ETIQUETA_PERFIL_CARGANDO = "perfil-cargando"
const val ETIQUETA_PERFIL_VACIO = "perfil-vacio"
const val ETIQUETA_PERFIL_ERROR = "perfil-error"
const val ETIQUETA_PERFIL_CABECERA = "perfil-cabecera"
const val ETIQUETA_PERFIL_ULTIMO_VIAJE = "perfil-ultimo-viaje"
const val ETIQUETA_PERFIL_SIN_CONCLUSIONES = "perfil-sin-conclusiones"
const val ETIQUETA_PERFIL_VER_VIAJES = "perfil-ver-viajes"
const val ETIQUETA_PERFIL_VER_CARRETERAS = "perfil-ver-carreteras"

/** Las dos pestañas del perfil: el tablero de conclusiones y la temporada (modo carrera). */
enum class PestanaPerfil { TABLERO, TEMPORADA }

const val ETIQUETA_PERFIL_PESTANAS = "perfil-pestanas"

/**
 * Callbacks del perfil: abrir el resumen de un viaje, la lista de viajes y las carreteras, y compartir como imagen el
 * resumen del mes ([onCompartirMes]).
 */
data class AccionesPerfil(
    val onVolver: () -> Unit = {},
    val onVerViaje: (recorridoId: Long) -> Unit = {},
    val onVerViajes: () -> Unit = {},
    val onVerCarreteras: () -> Unit = {},
    val onCompartirMes: (ResumenMes) -> Unit = {},
)

/**
 * Perfil del piloto: con viajes, dos pestañas. El tablero lleva la cabecera, el último viaje y una tarjeta por
 * conclusión; la temporada, la ficha con el nivel, la racha, los kilómetros por semana, los hitos, el carné de
 * carreteras, los récords y el resumen del mes compartible. [falloCompartir] avisa de que no se pudo preparar la
 * imagen y
 * [pestanaInicial] es la pestaña con la que se abre.
 */
@Composable
fun PerfilScreen(
    estado: PerfilUiState,
    acciones: AccionesPerfil,
    modifier: Modifier = Modifier,
    falloCompartir: Boolean = false,
    pestanaInicial: PestanaPerfil = PestanaPerfil.TABLERO,
) {
    PantallaWrc(titulo = stringResource(R.string.inicio_perfil), onVolver = acciones.onVolver, modifier = modifier) {
        when {
            estado.cargando -> {
                Box(Modifier.fillMaxSize().testTag(ETIQUETA_PERFIL_CARGANDO)) { ListaEsqueletoWrc(filas = 3) }
            }

            estado.error -> {
                EstadoVacioWrc(
                    icono = Iconos.Cronometro,
                    titulo = stringResource(R.string.perfil_error_titulo),
                    texto = stringResource(R.string.perfil_error_texto),
                    etiqueta = ETIQUETA_PERFIL_ERROR,
                    variante = VarianteVacio.ERROR,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            estado.vacio -> {
                EstadoVacioWrc(
                    icono = Iconos.Cronometro,
                    titulo = stringResource(R.string.perfil_vacio_titulo),
                    texto = stringResource(R.string.perfil_vacio_texto),
                    etiqueta = ETIQUETA_PERFIL_VACIO,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                PerfilConPestanas(estado, acciones, falloCompartir, pestanaInicial)
            }
        }
    }
}

@Composable
private fun PerfilConPestanas(
    estado: PerfilUiState,
    acciones: AccionesPerfil,
    falloCompartir: Boolean,
    pestanaInicial: PestanaPerfil,
) {
    var pestana by rememberSaveable { mutableStateOf(pestanaInicial) }
    val opciones =
        listOf(
            OpcionSegmento(PestanaPerfil.TABLERO, stringResource(R.string.perfil_pestana_tablero), Iconos.Grafico),
            OpcionSegmento(PestanaPerfil.TEMPORADA, stringResource(R.string.perfil_pestana_temporada), Iconos.Piloto),
        )
    Column(Modifier.fillMaxSize()) {
        SelectorSegmentadoWrc(
            opciones = opciones,
            seleccionada = pestana,
            onSeleccion = { pestana = it },
            modifier =
                Modifier
                    .padding(
                        horizontal = Espacio.l,
                    ).padding(top = Espacio.s)
                    .testTag(ETIQUETA_PERFIL_PESTANAS),
        )
        when (pestana) {
            PestanaPerfil.TABLERO -> TableroPerfil(estado, acciones)
            PestanaPerfil.TEMPORADA -> TemporadaPerfil(estado, acciones, falloCompartir)
        }
    }
}

@Composable
private fun TableroPerfil(
    estado: PerfilUiState,
    acciones: AccionesPerfil,
) {
    val tablero = estado.tablero
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Espacio.l),
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        item { CabeceraPerfil(tablero) }
        tablero.ultimoViaje?.let { ultimo ->
            item { TarjetaUltimoViaje(ultimo) { acciones.onVerViaje(ultimo.id) } }
        }
        if (estado.conclusiones.isEmpty()) {
            item { AvisoSinConclusiones() }
        }
        items(estado.conclusiones, key = { it.tipo.name }) { TarjetaConclusion(it) }
        item {
            EnlacePerfil(
                icono = Iconos.Cronometro,
                texto = stringResource(R.string.perfil_ver_viajes),
                etiqueta = ETIQUETA_PERFIL_VER_VIAJES,
                onClick = acciones.onVerViajes,
            )
        }
        item {
            EnlacePerfil(
                icono = Iconos.Curvas,
                texto = stringResource(R.string.perfil_ver_carreteras),
                etiqueta = ETIQUETA_PERFIL_VER_CARRETERAS,
                onClick = acciones.onVerCarreteras,
            )
        }
    }
}

/** «Desde el 2 nov · 10 viajes · 412,3 km» con cifras de ancho fijo; el año solo si no es el del último viaje. */
@Composable
private fun CabeceraPerfil(tablero: TableroPiloto) {
    val desde = tablero.primerViajeMs?.let { fechaDesde(it, tablero.ultimoViaje?.inicioMs) }.orEmpty()
    val texto =
        stringResource(
            R.string.perfil_cabecera,
            desde,
            pluralStringResource(R.plurals.perfil_viajes, tablero.viajes, tablero.viajes),
            formatearKilometros(tablero.kmTotales),
        )
    Text(
        text = texto,
        style = Cuerpo.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag(ETIQUETA_PERFIL_CABECERA),
    )
}

private fun fechaDesde(
    primeroMs: Long,
    referenciaMs: Long?,
    zona: ZoneId = ZoneId.systemDefault(),
): String {
    val corta = formatearFechaCorta(primeroMs, zona)
    val anio = Instant.ofEpochMilli(primeroMs).atZone(zona).year
    val anioReferencia = referenciaMs?.let { Instant.ofEpochMilli(it).atZone(zona).year }
    return if (anioReferencia == null || anioReferencia == anio) corta else "$corta $anio"
}

/** Tarjeta del último viaje: destino (o «Viaje libre»), fecha y distancia; tocarla abre su resumen. */
@Composable
private fun TarjetaUltimoViaje(
    ultimo: UltimoViaje,
    onClick: () -> Unit,
) {
    val accion = stringResource(R.string.perfil_ultimo_accion)
    TarjetaWrc(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Medidas.zonaTactil)
                .testTag(ETIQUETA_PERFIL_ULTIMO_VIAJE)
                .semantics(mergeDescendants = true) { onClick(label = accion, action = null) },
        acentoRojo = true,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                EtiquetaConIcono(Iconos.Bandera, stringResource(R.string.perfil_ultimo_titulo))
                Text(
                    text = ultimo.destinoNombre ?: stringResource(R.string.perfil_ultimo_libre),
                    style = Cuerpo.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        stringResource(
                            R.string.perfil_ultimo_detalle,
                            formatearFechaHora(ultimo.inicioMs),
                            formatearDistanciaTramo(ultimo.distanciaM),
                        ),
                    style = Ayuda.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Iconos.FlechaDerecha,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Frase corta en una tarjeta baja: aún no hay carretera suficiente para sacar conclusiones. */
@Composable
private fun AvisoSinConclusiones() {
    TarjetaWrc(Modifier.fillMaxWidth().testTag(ETIQUETA_PERFIL_SIN_CONCLUSIONES), relleno = Espacio.m) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
            Icon(Iconos.Bandera, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            Text(
                text = stringResource(R.string.perfil_sin_conclusiones),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Enlace a otra pantalla: una fila de 48 dp como mínimo con icono, texto y flecha. */
@Composable
private fun EnlacePerfil(
    icono: ImageVector,
    texto: String,
    etiqueta: String,
    onClick: () -> Unit,
) {
    TarjetaWrc(
        modifier = Modifier.fillMaxWidth().heightIn(min = Medidas.zonaTactil).testTag(etiqueta),
        relleno = Espacio.m,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
            Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = texto,
                style = Cuerpo.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(Iconos.FlechaDerecha, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
