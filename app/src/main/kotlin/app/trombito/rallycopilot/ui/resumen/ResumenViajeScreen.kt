package app.trombito.rallycopilot.ui.resumen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.resumen.ViajeResumido
import app.trombito.rallycopilot.motor.analisis.ResumenViaje
import app.trombito.rallycopilot.ui.analisis.LeyendaVelocidad
import app.trombito.rallycopilot.ui.comun.CargandoWrc
import app.trombito.rallycopilot.ui.comun.EstadoVacioWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.MascotaPose
import app.trombito.rallycopilot.ui.comun.PantallaWrc
import app.trombito.rallycopilot.ui.comun.PoseMascota
import app.trombito.rallycopilot.ui.comun.TarjetaDato
import app.trombito.rallycopilot.ui.comun.TarjetaDatoFrase
import app.trombito.rallycopilot.ui.comun.VarianteVacio
import app.trombito.rallycopilot.ui.mistramos.formatearFechaHora
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_RESUMEN_LISTA = "resumen-lista"
const val ETIQUETA_RESUMEN_CABECERA = "resumen-cabecera"
const val ETIQUETA_RESUMEN_VACIO = "resumen-vacio"
const val ETIQUETA_RESUMEN_ERROR = "resumen-error"
const val ETIQUETA_RESUMEN_SIN_MAPA = "resumen-sin-mapa"
const val ETIQUETA_RESUMEN_KM = "resumen-km"
const val ETIQUETA_RESUMEN_EN_MARCHA = "resumen-en-marcha"
const val ETIQUETA_RESUMEN_MEDIA = "resumen-media"
const val ETIQUETA_RESUMEN_TOTAL = "resumen-total"
const val ETIQUETA_RESUMEN_MAXIMA = "resumen-maxima"
const val ETIQUETA_RESUMEN_CONCLUSION_PREFIJO = "resumen-conclusion-"

private val ALTO_MAPA = 260.dp
private val LADO_MASCOTA_CABECERA = 72.dp
private const val SEGUNDOS_HORA = 3_600L
private const val SEGUNDOS_MINUTO = 60L

/**
 * Resumen del viaje al terminar un recorrido (o al reabrirlo desde la lista de viajes): el mapa coloreado por
 * velocidad, los datos del viaje, la llegada frente a lo previsto, las conclusiones, los tramos cronometrados y las
 * carreteras frente a tu media. Sin lógica: estado inmutable y un callback; el mapa lo pone [mapa] para poder probar
 * la pantalla sin MapLibre.
 */
@Composable
fun ResumenViajeScreen(
    estado: ResumenViajeUiState,
    onVolver: () -> Unit,
    mapa: MapaDelResumen,
    modifier: Modifier = Modifier,
) {
    val viaje = estado.viaje
    PantallaWrc(
        titulo = stringResource(R.string.resumen_titulo),
        onVolver = onVolver,
        modifier = modifier,
        subtitulo = viaje?.let { subtituloDe(it) },
    ) {
        when {
            estado.cargando -> {
                CargandoWrc(stringResource(R.string.resumen_cargando), Modifier.fillMaxSize())
            }

            estado.error -> {
                EstadoVacioWrc(
                    icono = Iconos.Informacion,
                    titulo = stringResource(R.string.resumen_error_titulo),
                    texto = stringResource(R.string.resumen_error_texto),
                    etiqueta = ETIQUETA_RESUMEN_ERROR,
                    variante = VarianteVacio.ERROR,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            viaje == null -> {
                EstadoVacioWrc(
                    icono = Iconos.Bandera,
                    titulo = stringResource(R.string.resumen_vacio_titulo),
                    texto = stringResource(R.string.resumen_vacio_texto),
                    etiqueta = ETIQUETA_RESUMEN_VACIO,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                ContenidoResumen(viaje.resumen, estado.estiloMapaJson, mapa, Modifier.weight(1f))
            }
        }
    }
}

/** «23 nov 2026 · 18:42» y, si se guió, «· destino». */
@Composable
private fun subtituloDe(viaje: ViajeResumido): String {
    val fecha = formatearFechaHora(viaje.inicioMs)
    val destino = viaje.destinoNombre?.takeIf { it.isNotBlank() }
    return if (destino == null) fecha else stringResource(R.string.resumen_subtitulo_destino, fecha, destino)
}

@Composable
private fun ContenidoResumen(
    resumen: ResumenViaje,
    estiloMapaJson: String?,
    mapa: MapaDelResumen,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag(ETIQUETA_RESUMEN_LISTA),
        contentPadding = PaddingValues(Espacio.l),
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        item(key = "cabecera") { CabeceraResumen(resumen) }
        item(key = "mapa") { ZonaMapa(resumen, estiloMapaJson, mapa) }
        item(key = "datos") { DatosDelViaje(resumen) }
        if (resumen.llegada.previstoS != null) {
            item(key = "llegada") { TarjetaLlegada(resumen) }
        }
        items(resumen.conclusiones.size, key = { "conclusion-$it" }) { i ->
            TarjetaDatoFrase(
                frase = resumen.conclusiones[i],
                etiqueta = stringResource(R.string.resumen_conclusion),
                icono = Iconos.Informacion,
                modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_RESUMEN_CONCLUSION_PREFIJO + i),
            )
        }
        if (resumen.tramos.isNotEmpty()) {
            item(key = "tramos") { SeccionTramos(resumen) }
        }
        if (resumen.vias.isNotEmpty()) {
            item(key = "vias") { SeccionVias(resumen) }
        }
    }
}

/** Trombito con la bandera de meta y, al lado, que el viaje ha terminado con su distancia y su tiempo total. */
@Composable
private fun CabeceraResumen(resumen: ResumenViaje) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_RESUMEN_CABECERA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        MascotaPose(PoseMascota.BANDERA, lado = LADO_MASCOTA_CABECERA)
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            Text(
                text = stringResource(R.string.resumen_cabecera_titulo),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    stringResource(
                        R.string.resumen_cabecera_detalle,
                        formatearKm(resumen.distanciaM),
                        formatearDuracion(resumen.tiempoTotalS),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** El mapa con la leyenda de velocidad en una línea debajo; sin datos de mapa instalados, un aviso en su lugar. */
@Composable
private fun ZonaMapa(
    resumen: ResumenViaje,
    estiloMapaJson: String?,
    mapa: MapaDelResumen,
) {
    if (resumen.segmentos.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ALTO_MAPA)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            if (estiloMapaJson != null) {
                mapa(estiloMapaJson, resumen, Modifier.fillMaxSize())
            } else {
                Text(
                    text = stringResource(R.string.resumen_sin_mapa),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(Espacio.xl).testTag(ETIQUETA_RESUMEN_SIN_MAPA),
                )
            }
        }
        if (estiloMapaJson != null) LeyendaVelocidad(resumen.escala, Modifier.padding(horizontal = Espacio.xs))
    }
}

/** Distancia, tiempo en marcha y media en una fila; el tiempo total y la máxima debajo. */
@Composable
private fun DatosDelViaje(resumen: ResumenViaje) {
    val enMarcha = duracionDeTarjeta(resumen.tiempoEnMarchaS)
    val kmh = stringResource(R.string.resumen_unidad_kmh)
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.m)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
            TarjetaDato(
                cifra = formatearKm(resumen.distanciaM),
                etiqueta = stringResource(R.string.resumen_dato_km),
                unidad = "km",
                icono = Iconos.Regla,
                modifier = Modifier.weight(1f).testTag(ETIQUETA_RESUMEN_KM),
            )
            TarjetaDato(
                cifra = enMarcha.cifra,
                etiqueta = stringResource(R.string.resumen_dato_en_marcha),
                unidad = enMarcha.unidad,
                icono = Iconos.Cronometro,
                modifier = Modifier.weight(1f).testTag(ETIQUETA_RESUMEN_EN_MARCHA),
            )
            TarjetaDato(
                cifra = resumen.mediaEnMarchaKmh?.let { formatearKmhEntero(it) } ?: "—",
                etiqueta = stringResource(R.string.resumen_dato_media),
                unidad = kmh,
                icono = Iconos.Velocimetro,
                modifier = Modifier.weight(1f).testTag(ETIQUETA_RESUMEN_MEDIA),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
            TarjetaDato(
                cifra = formatearDuracion(resumen.tiempoTotalS),
                etiqueta = stringResource(R.string.resumen_dato_total),
                icono = Iconos.Bandera,
                modifier = Modifier.weight(1f).testTag(ETIQUETA_RESUMEN_TOTAL),
            )
            TarjetaDato(
                cifra = formatearKmhEntero(resumen.velocidadMaxKmh),
                etiqueta = stringResource(R.string.resumen_dato_maxima),
                unidad = kmh,
                icono = Iconos.Rayo,
                modifier = Modifier.weight(1f).testTag(ETIQUETA_RESUMEN_MAXIMA),
            )
        }
    }
}

/** Cifra y unidad de una tarjeta estrecha: «12:34 min» por debajo de la hora y «1:05 h» desde ella. */
internal data class DuracionTarjeta(
    val cifra: String,
    val unidad: String,
)

internal fun duracionDeTarjeta(segundos: Long): DuracionTarjeta {
    val s = segundos.coerceAtLeast(0L)
    return if (s >= SEGUNDOS_HORA) {
        val minutos = s % SEGUNDOS_HORA / SEGUNDOS_MINUTO
        DuracionTarjeta("${s / SEGUNDOS_HORA}:${minutos.toString().padStart(2, '0')}", "h")
    } else {
        DuracionTarjeta(formatearDuracion(s), "min")
    }
}
