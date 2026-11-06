package app.trombito.rallycopilot.ui.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.EntradaHistorial
import app.trombito.rallycopilot.motor.analisis.PerfilPiloto
import app.trombito.rallycopilot.ui.comun.ChipIconoWrc
import app.trombito.rallycopilot.ui.comun.EstadoVacioWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.mistramos.formatearDistanciaTramo
import app.trombito.rallycopilot.ui.mistramos.formatearFechaCorta
import app.trombito.rallycopilot.ui.mistramos.formatearFechaHora
import app.trombito.rallycopilot.ui.mistramos.formatearVelocidadKmh
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally
import app.trombito.rallycopilot.ui.theme.Rotulo
import app.trombito.rallycopilot.ui.tramos.colorDelta
import app.trombito.rallycopilot.ui.tramos.formatearDelta
import app.trombito.rallycopilot.ui.tramos.formatearTiempo
import app.trombito.rallycopilot.motor.analisis.ResumenTramo as ResumenTramoPerfil

const val ETIQUETA_MEJOR_PREFIJO = "perfil-mejor-"
const val ETIQUETA_HISTORIAL_PREFIJO = "perfil-historial-"
const val ETIQUETA_EVOLUCION_TITULO = "perfil-evolucion-titulo"
const val ETIQUETA_EVOLUCION_VACIA = "perfil-evolucion-vacia"
const val ETIQUETA_EVOLUCION_PUNTO = "perfil-evolucion-punto"
const val ETIQUETA_MEJOR_ANALISIS_PREFIJO = "perfil-analisis-mejor-"

/** Pasadas mínimas de un tramo para que la gráfica de evolución diga algo. */
private const val PASADAS_PARA_GRAFICO = 3

private val ALTO_FILA = 56.dp
private val LADO_TACTIL = 48.dp

/** Tiempo de una fila: 20 sp, cursiva Black, sin el apretado de las cifras grandes. */
private val CifraFila = Rotulo.copy(fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = 0.sp)

/** Filas de «Mejores tiempos»: una por tramo; tocarla elige el tramo de la evolución. */
internal fun LazyListScope.mejoresTiempos(
    estado: PerfilUiState,
    acciones: AccionesPerfil,
) {
    item { Aviso(R.string.perfil_mejores_ayuda) }
    items(estado.mejores, key = { it.tramoId }) { tramo ->
        val pasadaId = tramo.mejorPasadaId?.toLongOrNull()
        FilaMejor(
            tramo = tramo,
            seleccionado = tramo.tramoId == estado.seleccionado?.tramoId,
            onClick = { acciones.onSeleccionar(tramo.tramoId) },
            onVerAnalisis = pasadaId?.let { id -> { acciones.onVerAnalisis(tramo.tramoId, id) } },
        )
    }
}

@Composable
private fun FilaMejor(
    tramo: ResumenTramoPerfil,
    seleccionado: Boolean,
    onClick: () -> Unit,
    onVerAnalisis: (() -> Unit)?,
) {
    val colores = MaterialTheme.colorScheme
    TarjetaWrc(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_FILA)
                .testTag(ETIQUETA_MEJOR_PREFIJO + tramo.tramoId)
                .semantics { selected = seleccionado },
        activa = seleccionado,
        relleno = Espacio.m,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                Text(
                    text = tramo.nombre,
                    style = Cuerpo.copy(fontWeight = FontWeight.SemiBold),
                    color = colores.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detalleTramo(tramo),
                    style = Ayuda,
                    color = colores.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = tramo.mejorMs?.let { formatearTiempo(it) }.orEmpty(),
                style = CifraFila,
                color = colores.primary,
                maxLines = 1,
            )
            if (onVerAnalisis != null) {
                IconButton(
                    onClick = onVerAnalisis,
                    modifier = Modifier.size(LADO_TACTIL).testTag(ETIQUETA_MEJOR_ANALISIS_PREFIJO + tramo.tramoId),
                ) {
                    Icon(
                        Iconos.Grafico,
                        contentDescription = stringResource(R.string.perfil_ver_analisis_mejor, tramo.nombre),
                        tint = colores.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun detalleTramo(tramo: ResumenTramoPerfil): String {
    val pasadas = pluralStringResource(R.plurals.mistramos_pasadas, tramo.numeroPasadas, tramo.numeroPasadas)
    val base = "${formatearDistanciaTramo(tramo.distanciaM)} · $pasadas"
    return tramo.velMejorKmh?.let { "$base · ${formatearVelocidadKmh(it)}" } ?: base
}

/** Evolución del tramo elegido: la gráfica con tres pasadas o más; con menos, un aviso y los puntos que hay. */
@Composable
internal fun EvolucionTramo(estado: PerfilUiState) {
    val tramo = estado.seleccionado ?: return
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        Text(
            text = tramo.nombre,
            style = Cuerpo.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(ETIQUETA_EVOLUCION_TITULO),
        )
        if (tramo.evolucion.size < PASADAS_PARA_GRAFICO) {
            EvolucionIncompleta(tramo)
        } else {
            GraficoEvolucion(tramo.evolucion)
            LeyendaRecords()
        }
    }
}

@Composable
private fun EvolucionIncompleta(tramo: ResumenTramoPerfil) {
    EstadoVacioWrc(
        icono = Iconos.Grafico,
        titulo = stringResource(R.string.perfil_evolucion_vacia_titulo),
        texto = stringResource(R.string.perfil_evolucion_vacia_texto),
        etiqueta = ETIQUETA_EVOLUCION_VACIA,
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Espacio.s, Alignment.CenterHorizontally),
    ) {
        tramo.evolucion.forEach { punto ->
            ChipIconoWrc(
                icono = if (punto.esRecord) Iconos.Estrella else Iconos.Cronometro,
                texto = "${formatearTiempo(punto.tiempoMs)} · ${formatearFechaCorta(punto.fechaMs)}",
                modifier = Modifier.testTag(ETIQUETA_EVOLUCION_PUNTO),
            )
        }
    }
}

/** Historial reciente: tiempo y diferencia contra el mejor tiempo del tramo. */
internal fun LazyListScope.historial(
    perfil: PerfilPiloto,
    acciones: AccionesPerfil,
) {
    if (perfil.historial.isEmpty()) {
        item { Aviso(R.string.perfil_historial_vacio) }
    }
    items(perfil.historial, key = { it.pasadaId }) { entrada ->
        val pasadaId = entrada.pasadaId.toLongOrNull()
        FilaHistorial(entrada, pasadaId?.let { id -> { acciones.onVerAnalisis(entrada.tramoId, id) } })
    }
}

@Composable
private fun FilaHistorial(
    entrada: EntradaHistorial,
    onVerAnalisis: (() -> Unit)?,
) {
    val descripcion = stringResource(R.string.perfil_ver_analisis_pasada, entrada.nombre)
    TarjetaWrc(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_FILA)
                .testTag(ETIQUETA_HISTORIAL_PREFIJO + entrada.pasadaId)
                .semantics(mergeDescendants = true) { onClick(label = descripcion, action = null) },
        relleno = Espacio.m,
        onClick = onVerAnalisis,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = entrada.nombre,
                    style = Cuerpo.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatearFechaHora(entrada.fechaMs),
                    style = Ayuda,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatearTiempo(entrada.totalMs),
                    style = CifraFila,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                DiferenciaHistorial(entrada)
            }
        }
    }
}

/** Un récord se dice una vez (estrella ámbar y «Récord»); el resto, su diferencia con el mejor tiempo. */
@Composable
private fun DiferenciaHistorial(entrada: EntradaHistorial) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        if (entrada.esRecord) {
            Icon(
                Iconos.Estrella,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(ICONO_RECORD),
            )
            Text(
                text = stringResource(R.string.perfil_record),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text =
                    if (entrada.deltaMejorMs == 0L) {
                        stringResource(R.string.mistramos_mejor)
                    } else {
                        formatearDelta(entrada.deltaMejorMs)
                    },
                style = MaterialTheme.typography.labelLarge,
                color = colorDelta(entrada.deltaMejorMs, LocalColoresRally.current, MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private val ICONO_RECORD = 18.dp
