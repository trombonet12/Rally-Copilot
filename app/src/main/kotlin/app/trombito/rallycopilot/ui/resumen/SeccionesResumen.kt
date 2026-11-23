package app.trombito.rallycopilot.ui.resumen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.ResumenViaje
import app.trombito.rallycopilot.motor.analisis.TramoHecho
import app.trombito.rallycopilot.motor.analisis.ViaRecorrida
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.PlacaCarretera
import app.trombito.rallycopilot.ui.comun.TarjetaDato
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.tramos.colorDelta
import app.trombito.rallycopilot.ui.tramos.formatearDelta

const val ETIQUETA_RESUMEN_LLEGADA = "resumen-llegada"
const val ETIQUETA_RESUMEN_LLEGADA_REAL = "resumen-llegada-real"
const val ETIQUETA_RESUMEN_LLEGADA_PREVISTA = "resumen-llegada-prevista"
const val ETIQUETA_RESUMEN_LLEGADA_FRASE = "resumen-llegada-frase"
const val ETIQUETA_RESUMEN_TRAMOS = "resumen-tramos"
const val ETIQUETA_RESUMEN_TRAMO_PREFIJO = "resumen-tramo-"
const val ETIQUETA_RESUMEN_TRAMO_RECORD_PREFIJO = "resumen-tramo-record-"
const val ETIQUETA_RESUMEN_VIAS = "resumen-vias"
const val ETIQUETA_RESUMEN_VIA_PREFIJO = "resumen-via-"
const val ETIQUETA_RESUMEN_VIA_DIFERENCIA_PREFIJO = "resumen-via-diferencia-"

private const val SIN_DATO = "—"

@Composable
private fun TituloSeccion(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

/** La llegada real (la duración del viaje) frente a la prevista, con una frase de cuánto antes o después. */
@Composable
internal fun TarjetaLlegada(resumen: ResumenViaje) {
    val previstoS = resumen.llegada.previstoS ?: return
    val desviacionS = resumen.llegada.desviacionS ?: 0L
    val colores = LocalColoresRally.current
    val puntual = minutosDeDesviacion(desviacionS) == 0
    val duracion = formatearDuracionLarga(desviacionS)
    val frase =
        when {
            puntual -> stringResource(R.string.resumen_llegada_puntual)
            desviacionS < 0 -> stringResource(R.string.resumen_llegada_antes, duracion)
            else -> stringResource(R.string.resumen_llegada_despues, duracion)
        }
    val colorFrase =
        when {
            puntual -> MaterialTheme.colorScheme.onSurface
            desviacionS < 0 -> colores.deltaMejor
            else -> colores.deltaPeor
        }
    Column(
        modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_RESUMEN_LLEGADA),
        verticalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
            TarjetaDato(
                cifra = formatearDuracion(resumen.tiempoTotalS),
                etiqueta = stringResource(R.string.resumen_llegada_real),
                icono = Iconos.Bandera,
                modifier = Modifier.weight(1f).testTag(ETIQUETA_RESUMEN_LLEGADA_REAL),
            )
            TarjetaDato(
                cifra = formatearDuracion(previstoS),
                etiqueta = stringResource(R.string.resumen_llegada_prevista),
                icono = Iconos.Cronometro,
                modifier = Modifier.weight(1f).testTag(ETIQUETA_RESUMEN_LLEGADA_PREVISTA),
            )
        }
        Text(
            text = frase,
            style = Cuerpo.copy(fontWeight = FontWeight.SemiBold),
            color = colorFrase,
            modifier = Modifier.padding(horizontal = Espacio.xs).testTag(ETIQUETA_RESUMEN_LLEGADA_FRASE),
        )
    }
}

/** Los tramos cronometrados del viaje: nombre, tiempo, diferencia con el mejor anterior y la estrella del récord. */
@Composable
internal fun SeccionTramos(resumen: ResumenViaje) {
    TarjetaWrc(Modifier.fillMaxWidth().testTag(ETIQUETA_RESUMEN_TRAMOS)) {
        TituloSeccion(stringResource(R.string.resumen_tramos_titulo))
        resumen.tramos.forEachIndexed { i, tramo -> FilaTramoHecho(i, tramo) }
    }
}

@Composable
private fun FilaTramoHecho(
    indice: Int,
    tramo: TramoHecho,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Medidas.zonaTactil)
                .testTag(ETIQUETA_RESUMEN_TRAMO_PREFIJO + indice)
                .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        if (tramo.esRecord) {
            Icon(
                imageVector = Iconos.Estrella,
                contentDescription = stringResource(R.string.resumen_tramo_record),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(ETIQUETA_RESUMEN_TRAMO_RECORD_PREFIJO + indice),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = tramo.nombre,
                style = Cuerpo.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = FormatoEs.tiempo(tramo.tiempoMs),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DiferenciaDelTramo(tramo.deltaMs)
    }
}

/** La diferencia con el mejor tiempo anterior (verde si es más rápido, rojo si es más lento) o «Primera vez». */
@Composable
private fun DiferenciaDelTramo(deltaMs: Long?) {
    if (deltaMs != null) {
        Text(
            text = formatearDelta(deltaMs),
            style = MaterialTheme.typography.titleMedium,
            color = colorDelta(deltaMs, LocalColoresRally.current, MaterialTheme.colorScheme.onSurface),
        )
    } else {
        Text(
            text = stringResource(R.string.resumen_tramo_primera),
            style = Ayuda,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Las carreteras recorridas con su velocidad media y la diferencia (+/− %) frente a tu media de siempre. */
@Composable
internal fun SeccionVias(resumen: ResumenViaje) {
    TarjetaWrc(Modifier.fillMaxWidth().testTag(ETIQUETA_RESUMEN_VIAS)) {
        TituloSeccion(stringResource(R.string.resumen_vias_titulo))
        resumen.vias.forEachIndexed { i, via -> FilaVia(i, via) }
    }
}

@Composable
private fun FilaVia(
    indice: Int,
    via: ViaRecorrida,
) {
    val ref = via.ref
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Medidas.zonaTactil)
                .testTag(ETIQUETA_RESUMEN_VIA_PREFIJO + indice)
                .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        if (ref != null) PlacaCarretera(ref)
        Column(Modifier.weight(1f)) {
            if (ref == null) {
                Text(
                    text = via.etiqueta.orEmpty(),
                    style = Cuerpo.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(text = textoMedidas(via), style = Ayuda, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = textoTuMedia(via), style = Ayuda, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        via.diferenciaPct?.let { DiferenciaDeLaVia(indice, it) }
    }
}

/** «+6 %» en verde si fuiste más rápido que tu media, «−3 %» en rojo si más lento. */
@Composable
private fun DiferenciaDeLaVia(
    indice: Int,
    pct: Double,
) {
    val colores = LocalColoresRally.current
    val color =
        when {
            Math.round(pct) > 0L -> colores.deltaMejor
            Math.round(pct) < 0L -> colores.deltaPeor
            else -> MaterialTheme.colorScheme.onSurface
        }
    Text(
        text = formatearPorcentaje(pct),
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = Modifier.testTag(ETIQUETA_RESUMEN_VIA_DIFERENCIA_PREFIJO + indice),
    )
}

@Composable
private fun textoMedidas(via: ViaRecorrida): String =
    stringResource(
        R.string.resumen_via_medidas,
        FormatoEs.distancia(via.metros),
        via.velMediaKmh?.let { FormatoEs.velocidad(it) } ?: SIN_DATO,
    )

@Composable
private fun textoTuMedia(via: ViaRecorrida): String =
    via.velMediaHistoricaKmh
        ?.let { stringResource(R.string.resumen_via_tu_media, FormatoEs.velocidad(it)) }
        ?: stringResource(R.string.resumen_via_sin_historico)
