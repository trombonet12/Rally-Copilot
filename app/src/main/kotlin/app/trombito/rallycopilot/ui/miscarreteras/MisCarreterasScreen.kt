package app.trombito.rallycopilot.ui.miscarreteras

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.Carretera
import app.trombito.rallycopilot.motor.analisis.CurvasPorGrado
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.CargandoWrc
import app.trombito.rallycopilot.ui.comun.EstadoVacioWrc
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.PlacaCarretera
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.RojoAcento
import app.trombito.rallycopilot.ui.theme.Rotulo

const val ETIQUETA_CARRETERAS_VACIO = "carreteras-vacio"
const val ETIQUETA_CARRETERAS_ERROR = "carreteras-error"
const val ETIQUETA_CARRETERAS_LISTA = "carreteras-lista"
const val ETIQUETA_CARRETERAS_KM = "carreteras-km"
const val ETIQUETA_CARRETERAS_RECORRIDOS = "carreteras-recorridos"
const val ETIQUETA_CARRETERAS_CURVAS = "carreteras-curvas"
const val ETIQUETA_CARRETERAS_CURVAS_FILA_PREFIJO = "carreteras-curvas-"
const val ETIQUETA_CARRETERAS_TOP = "carreteras-top"
const val ETIQUETA_CARRETERAS_TOP_FILA_PREFIJO = "carreteras-top-"
const val ETIQUETA_CARRETERAS_TOP_VACIO = "carreteras-top-vacio"

private val ALTO_BARRA = 12.dp
private val ANCHO_ETIQUETA_CURVAS = 84.dp
private val ANCHO_CUENTA_CURVAS = 40.dp
private val ANCHO_PLACA = 72.dp
private const val FRACCION_MIN_BARRA = 0.03f

/** «Mis carreteras»: mapa de calor de las vías conducidas, kilómetros, curvas por grado y carreteras con más curvas. */
@Composable
fun MisCarreterasScreen(
    estado: MisCarreterasUiState,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        BarraSuperior(titulo = stringResource(R.string.carreteras_titulo), onVolver = onVolver)
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
                Contenido(estado)
            }
        }
    }
}

@Composable
private fun Contenido(estado: MisCarreterasUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(ETIQUETA_CARRETERAS_LISTA),
        contentPadding = PaddingValues(Espacio.l),
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        item(key = "mapa") {
            val aviso =
                if (estado.hayVias) {
                    R.string.carreteras_mapa_sin_coincidencias
                } else {
                    R.string.carreteras_mapa_sin_vias
                }
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                MapaCalorCarreteras(estado.trazos, stringResource(aviso))
                if (estado.trazos.isNotEmpty()) LeyendaCalor()
            }
        }
        item(key = "totales") { Totales(estado) }
        item(key = "curvas") { TarjetaCurvas(estado.curvas) }
        item(key = "top") { TarjetaMasCurvas(estado.carreteras) }
    }
}

@Composable
private fun Totales(estado: MisCarreterasUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
        TarjetaCifra(
            valor = FormatoEs.distancia(estado.metrosTotales),
            titulo = R.string.carreteras_distancia_titulo,
            etiqueta = ETIQUETA_CARRETERAS_KM,
            modifier = Modifier.weight(1f),
        )
        TarjetaCifra(
            valor = estado.recorridos.toString(),
            titulo = R.string.carreteras_recorridos_titulo,
            etiqueta = ETIQUETA_CARRETERAS_RECORRIDOS,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TarjetaCifra(
    valor: String,
    @StringRes titulo: Int,
    etiqueta: String,
    modifier: Modifier = Modifier,
) {
    TarjetaWrc(modifier.testTag(etiqueta).semantics(mergeDescendants = true) {}) {
        Text(
            text = valor,
            style = Rotulo,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(titulo),
            style = Ayuda,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Una barra de la tarjeta de curvas: texto del grado, cuántas curvas y qué fracción del máximo es. */
private class FilaCurvas(
    val etiqueta: String,
    val clave: String,
    val cuenta: Int,
)

@Composable
private fun TarjetaCurvas(curvas: CurvasPorGrado) {
    val filas =
        (1..GRADOS).map { FilaCurvas(stringResource(R.string.carreteras_grado, it), "g$it", curvas.deGrado(it)) } +
            FilaCurvas(stringResource(R.string.carreteras_escuadra), "escuadra", curvas.escuadras) +
            FilaCurvas(stringResource(R.string.carreteras_horquilla), "horquilla", curvas.horquillas)
    val maximo = filas.maxOf { it.cuenta }.coerceAtLeast(1)
    TarjetaWrc(Modifier.testTag(ETIQUETA_CARRETERAS_CURVAS)) {
        Text(
            stringResource(R.string.carreteras_curvas_titulo),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            pluralStringResource(R.plurals.carreteras_curvas_total, curvas.total, curvas.total),
            style = Ayuda,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Espacio.s),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            filas.forEach { FilaBarra(it, maximo) }
        }
    }
}

@Composable
private fun FilaBarra(
    fila: FilaCurvas,
    maximo: Int,
) {
    val descripcion =
        stringResource(
            R.string.carreteras_curvas_fila,
            fila.etiqueta,
            pluralStringResource(R.plurals.carreteras_curvas_cuenta, fila.cuenta, fila.cuenta),
        )
    val fraccion = if (fila.cuenta == 0) 0f else (fila.cuenta.toFloat() / maximo).coerceAtLeast(FRACCION_MIN_BARRA)
    val color = if (fila.clave == "horquilla") RojoAcento else MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(ETIQUETA_CARRETERAS_CURVAS_FILA_PREFIJO + fila.clave)
            .clearAndSetSemantics { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        Text(
            fila.etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(ANCHO_ETIQUETA_CURVAS),
            maxLines = 1,
        )
        Box(
            Modifier
                .weight(1f)
                .height(ALTO_BARRA)
                .clip(RoundedCornerShape(ALTO_BARRA / 2))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraccion)
                    .clip(RoundedCornerShape(ALTO_BARRA / 2))
                    .background(color),
            )
        }
        Text(
            fila.cuenta.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.width(ANCHO_CUENTA_CURVAS),
            maxLines = 1,
        )
    }
}

@Composable
private fun TarjetaMasCurvas(carreteras: List<Carretera>) {
    TarjetaWrc(Modifier.testTag(ETIQUETA_CARRETERAS_TOP)) {
        Text(
            stringResource(R.string.carreteras_mas_curvas_titulo),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = Espacio.s).semantics { heading() },
        )
        if (carreteras.isEmpty()) {
            Text(
                stringResource(R.string.carreteras_mas_curvas_vacio),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(ETIQUETA_CARRETERAS_TOP_VACIO),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.m)) {
            carreteras.forEachIndexed { i, carretera -> FilaCarretera(i, carretera) }
        }
    }
}

@Composable
private fun FilaCarretera(
    indice: Int,
    carretera: Carretera,
) {
    val medidas =
        stringResource(
            R.string.carreteras_fila_medidas,
            FormatoEs.distancia(carretera.metros),
            pluralStringResource(R.plurals.carreteras_pasadas, carretera.pasadas, carretera.pasadas),
        )
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(ETIQUETA_CARRETERAS_TOP_FILA_PREFIJO + indice)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        Box(Modifier.width(ANCHO_PLACA), contentAlignment = Alignment.CenterStart) {
            val ref = carretera.ref
            if (ref != null) {
                PlacaCarretera(ref)
            } else {
                Icon(
                    Iconos.Curvas,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Medidas.iconoCabecera),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            // Sin nombre, la placa ya dice cuál es la carretera.
            carretera.nombre?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(medidas, style = Ayuda, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            pluralStringResource(R.plurals.carreteras_curvas_cuenta, carretera.curvas, carretera.curvas),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private const val GRADOS = 6
