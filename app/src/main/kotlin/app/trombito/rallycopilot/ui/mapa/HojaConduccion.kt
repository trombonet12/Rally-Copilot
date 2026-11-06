package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.HojaInferiorWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.LADO_TACTIL
import app.trombito.rallycopilot.ui.comun.PlacaCarretera
import app.trombito.rallycopilot.ui.comun.TamanoPlaca
import app.trombito.rallycopilot.ui.navegacion.BotonBuscarGuiando
import app.trombito.rallycopilot.ui.navegacion.BotonSalirMapa
import app.trombito.rallycopilot.ui.navegacion.BotonTerminar
import app.trombito.rallycopilot.ui.navegacion.guiado.PieGuiado
import app.trombito.rallycopilot.ui.navegacion.guiado.proximaParada
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally
import app.trombito.rallycopilot.ui.tramos.CronoMiniEnPanel
import app.trombito.rallycopilot.ui.tramos.EscalaCronoMini

/** La hoja de conducción (96-112 dp): ✕ a la izquierda, el centro de cada modo y la lupa a la derecha. */
const val ETIQUETA_HOJA = "panel"

/** Etiqueta de la lupa de la hoja en el modo libre, que lleva al campo «¿A dónde?». */
const val ETIQUETA_BUSCAR_DESTINO = "mapa-buscar-destino"

/** Etiqueta de los kilómetros recorridos del modo libre. */
const val ETIQUETA_KM_RECORRIDOS = "hoja-km-recorridos"

/** Etiqueta de la fila de la vía en la hoja (placa y nombre). */
const val ETIQUETA_VIA = "panel-via"

private const val MAX_PLACAS = 2

/** Lo que la hoja de conducción necesita pintar, agrupado. */
internal class DatosHoja(
    val estado: MapaUiState,
    val control: EstadoNavegacion,
    val crono: CronoEnCurso?,
)

/** Lo que hace cada botón de la hoja. */
internal class AccionesHoja(
    val onSalir: () -> Unit,
    val onTerminar: () -> Unit,
    /** La lupa en modo libre: lleva al campo «¿A dónde?»; null (sin campo, en notas grandes) no la pinta. */
    val onBuscarDestino: (() -> Unit)?,
    /** La lupa guiando: abre la búsqueda sobre el mapa. */
    val onBuscarGuiando: () -> Unit,
)

/** Los `ref` de OSM separados por «;», sin vacíos ni repetidos y como mucho [MAX_PLACAS]. */
internal fun refsDePlaca(ref: String?): List<String> =
    ref
        .orEmpty()
        .split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(MAX_PLACAS)

/**
 * La hoja compacta de la conducción, anclada abajo. Libre: ✕ (cierra el mapa) · cronómetro grande ámbar (o «LISTO» y
 * el botón «SALIDA» de la salida manual) con, debajo, la carretera y los kilómetros recorridos · lupa. Guiado: ✕
 * (termina) · tiempo restante, kilómetros y hora de llegada con el cronómetro pequeño al lado · lupa. Sin señal de
 * GPS el cronómetro lleva un icono de pausa. Con tramo conocido el cronómetro grande va arriba (HUD): aquí no se
 * repite.
 */
@Composable
internal fun HojaConduccion(
    datos: DatosHoja,
    acciones: AccionesHoja,
    modifier: Modifier = Modifier,
    reloj: () -> Long = System::currentTimeMillis,
) {
    val guiando = datos.control as? EstadoNavegacion.Guiando
    HojaInferiorWrc(modifier.testTag(ETIQUETA_HOJA), compacta = true) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_FILA_HOJA),
            horizontalArrangement = Arrangement.spacedBy(Espacio.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (guiando != null) BotonTerminar(acciones.onTerminar) else BotonSalirMapa(acciones.onSalir)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                if (guiando != null) CentroGuiado(guiando, datos, reloj) else CentroLibre(datos, reloj)
            }
            if (guiando != null) {
                BotonBuscarGuiando(acciones.onBuscarGuiando)
            } else if (acciones.onBuscarDestino != null) {
                BotonBuscarGuiando(
                    onClick = acciones.onBuscarDestino,
                    etiqueta = ETIQUETA_BUSCAR_DESTINO,
                    descripcion = R.string.mapa_buscar_destino,
                )
            } else {
                Spacer(Modifier.size(LADO_TACTIL))
            }
        }
    }
}

private val ALTO_FILA_HOJA = 72.dp

/** Cronómetro (o LISTO / SALIDA) y, debajo, la carretera y los kilómetros recorridos. */
@Composable
private fun CentroLibre(
    datos: DatosHoja,
    reloj: () -> Long,
) {
    val libre = datos.control as? EstadoNavegacion.Libre
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Cronometro(datos, EscalaCronoMini.GRANDE, reloj)
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.m), verticalAlignment = Alignment.CenterVertically) {
            FilaVia(datos.estado)
            libre?.let {
                val km = FormatoEs.kilometrosDesdeMetros(it.distanciaM)
                val descripcion = stringResource(R.string.hoja_km_recorridos, km)
                Text(
                    text = km,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag(ETIQUETA_KM_RECORRIDOS).semantics { contentDescription = descripcion },
                )
            }
        }
    }
}

/** El tiempo restante, kilómetros y llegada; el cronómetro pequeño a su lado. */
@Composable
private fun CentroGuiado(
    guiando: EstadoNavegacion.Guiando,
    datos: DatosHoja,
    reloj: () -> Long,
) {
    val progreso = guiando.progreso
    val adelanta = (datos.crono?.deltaMs ?: 0L) < 0L
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.m), verticalAlignment = Alignment.CenterVertically) {
        if (progreso != null) {
            PieGuiado(progreso, proxima = proximaParada(guiando, progreso), adelanta = adelanta)
        }
        Cronometro(datos, EscalaCronoMini.COMPACTO, reloj)
    }
}

/** El cronómetro pequeño con el icono de pausa si se perdió el GPS; con HUD arriba no se repite aquí. */
@Composable
private fun Cronometro(
    datos: DatosHoja,
    escala: EscalaCronoMini,
    reloj: () -> Long,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs), verticalAlignment = Alignment.CenterVertically) {
        CronoMiniEnPanel(datos.crono, reloj = reloj, escala = escala)
        if (datos.estado.sinSenalGps && datos.crono != null) {
            Icon(
                imageVector = Iconos.Pausa,
                contentDescription = stringResource(R.string.hoja_crono_en_pausa),
                tint = LocalColoresRally.current.aviso,
                modifier = Modifier.size(if (escala == EscalaCronoMini.GRANDE) 28.dp else 20.dp),
            )
        }
    }
}

/**
 * La placa (28 dp) de la vía en la que se va, o su nombre si no tiene `ref`. Solo con la vía que coincide con el
 * camino al que se ajustó la posición ([MapaUiState.viaDelPanel]): si no, no se enseña nada antes que una placa falsa.
 */
@Composable
private fun FilaVia(estado: MapaUiState) {
    val via = estado.viaDelPanel
    val refs = refsDePlaca(via?.ref)
    val nombre = via?.nombre?.trim()?.takeIf { it.isNotEmpty() }
    if (refs.isEmpty() && nombre == null) return
    Row(
        modifier = Modifier.testTag(ETIQUETA_VIA),
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        refs.forEach { PlacaCarretera(it, tamano = TamanoPlaca.PEQUENA) }
        if (refs.isEmpty() && nombre != null) {
            Text(
                text = nombre,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
