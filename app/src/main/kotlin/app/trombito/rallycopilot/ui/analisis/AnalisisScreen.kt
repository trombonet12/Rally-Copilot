package app.trombito.rallycopilot.ui.analisis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.AnalisisPasada
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.comun.EstadoVacio
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_ANALISIS_CARGANDO = "analisis-cargando"
const val ETIQUETA_ANALISIS_NO_ENCONTRADA = "analisis-no-encontrada"
const val ETIQUETA_ANALISIS_ILEGIBLE = "analisis-ilegible"
const val ETIQUETA_ANALISIS_SIN_MAPA = "analisis-sin-mapa"

private const val PESO_MAPA_VERTICAL = 0.5f
private const val PESO_DATOS_VERTICAL = 0.5f

/** El mapa del análisis: recibe el estilo, el análisis (traza coloreada) y el marcador de la repetición. */
typealias MapaDelAnalisis = @Composable (
    estiloJson: String,
    analisis: AnalisisPasada,
    marcador: PuntoGeo?,
    modifier: Modifier,
) -> Unit

/** El mapa real, MapLibre con la traza coloreada por velocidad. */
val MapaAnalisisReal: MapaDelAnalisis = { estiloJson, analisis, marcador, modificador ->
    MapaAnalisis(estiloJson, analisis.segmentos, analisis.escala, marcador, modificador)
}

/**
 * Análisis de una pasada. En vertical: resumen (tiempo y delta), el mapa con la leyenda y el mando de la repetición, y
 * debajo, desplazable, el selector de la pasada comparada, los gráficos y la tabla de parciales. En horizontal: el
 * mapa a un lado y todo lo demás al otro. Sin lógica: estado inmutable y callbacks; el mapa lo pone [mapa] para poder
 * probar la pantalla sin MapLibre.
 */
@Composable
fun AnalisisScreen(
    estado: AnalisisUiState,
    acciones: AccionesAnalisis,
    mapa: MapaDelAnalisis,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        BarraAnalisis(estado, acciones.onVolver)
        val analisis = estado.analisis
        when {
            estado.cargando -> Cargando()
            estado.problema != null || analisis == null -> Problema(estado.problema)
            else -> Contenido(estado, analisis, acciones, mapa)
        }
    }
}

@Composable
private fun Cargando() {
    Box(Modifier.fillMaxSize().testTag(ETIQUETA_ANALISIS_CARGANDO), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun Problema(problema: ProblemaAnalisis?) {
    if (problema == ProblemaAnalisis.ILEGIBLE) {
        EstadoVacio(
            icono = Iconos.Grafico,
            titulo = stringResource(R.string.analisis_ilegible_titulo),
            texto = stringResource(R.string.analisis_ilegible_texto),
            etiqueta = ETIQUETA_ANALISIS_ILEGIBLE,
        )
    } else {
        EstadoVacio(
            icono = Iconos.Bandera,
            titulo = stringResource(R.string.analisis_no_existe_titulo),
            texto = stringResource(R.string.analisis_no_existe_texto),
            etiqueta = ETIQUETA_ANALISIS_NO_ENCONTRADA,
        )
    }
}

@Composable
private fun Contenido(
    estado: AnalisisUiState,
    analisis: AnalisisPasada,
    acciones: AccionesAnalisis,
    mapa: MapaDelAnalisis,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                ZonaMapa(estado, analisis, acciones, mapa, Modifier.weight(1f).fillMaxHeight().padding(Espacio.s))
                Column(Modifier.weight(1f)) {
                    ResumenAnalisis(estado, Modifier.padding(bottom = Espacio.s))
                    DatosDelAnalisis(estado, analisis, acciones, Modifier.weight(1f))
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ResumenAnalisis(estado, Modifier.padding(bottom = Espacio.s))
                ZonaMapa(
                    estado,
                    analisis,
                    acciones,
                    mapa,
                    Modifier.weight(PESO_MAPA_VERTICAL).fillMaxWidth().padding(horizontal = Espacio.s),
                )
                DatosDelAnalisis(estado, analisis, acciones, Modifier.weight(PESO_DATOS_VERTICAL))
            }
        }
    }
}

/** Selector de comparada, gráficos y tabla: la parte que se desplaza. */
@Composable
private fun DatosDelAnalisis(
    estado: AnalisisUiState,
    analisis: AnalisisPasada,
    acciones: AccionesAnalisis,
    modifier: Modifier = Modifier,
) {
    val datos =
        DatosGraficos(
            velocidad = analisis.velocidad,
            comparada = estado.velocidadComparada,
            delta = analisis.delta,
            cortesM = analisis.sectores.dropLast(1).map { it.hastaM },
            distanciaMaxM = analisis.sectores.lastOrNull()?.hastaM ?: 0.0,
        )
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Espacio.l),
        verticalArrangement = Arrangement.spacedBy(Espacio.l),
    ) {
        SelectorComparada(estado, acciones.onComparar)
        SeccionGraficos(datos, estado.cursorM, acciones.onCursor)
        TablaParciales(analisis)
    }
}

/**
 * El mapa protagonista, con la leyenda de velocidad en una línea debajo y el mando de la repetición de 56 dp tras ella
 * (nada tapa el mapa); sin datos de mapa, un aviso en su lugar.
 */
@Composable
private fun ZonaMapa(
    estado: AnalisisUiState,
    analisis: AnalisisPasada,
    acciones: AccionesAnalisis,
    mapa: MapaDelAnalisis,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            val estilo = estado.estiloMapaJson
            if (estilo != null) {
                mapa(estilo, analisis, estado.posicion?.punto, Modifier.fillMaxSize())
            } else {
                Text(
                    text = stringResource(R.string.analisis_sin_mapa),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier.align(Alignment.Center).padding(Espacio.xl).testTag(ETIQUETA_ANALISIS_SIN_MAPA),
                )
            }
        }
        if (estado.estiloMapaJson != null) {
            LeyendaVelocidad(analisis.escala, Modifier.padding(horizontal = Espacio.xs))
        }
        ControlesRepeticion(estado = estado.repeticion, duracionMs = analisis.totalMs, acciones = acciones)
    }
}
