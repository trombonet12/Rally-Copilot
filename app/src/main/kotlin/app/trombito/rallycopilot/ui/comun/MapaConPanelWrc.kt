package app.trombito.rallycopilot.ui.comun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.ui.theme.CarbonTranslucido
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Tiempo

/** Etiqueta de prueba de la cabecera de [MapaConPanelWrc]. */
const val ETIQUETA_MAPA_PANEL_CABECERA = "mapa-panel-cabecera"

/** Etiqueta de prueba del panel inferior de [MapaConPanelWrc]. */
const val ETIQUETA_MAPA_PANEL = "mapa-panel"

/** Etiqueta de prueba del dato grande ([DatoGrandeWrc]). */
const val ETIQUETA_DATO_GRANDE = "dato-grande-wrc"

private val UNIDAD_DATO = 14.sp
private const val SEPARADOR_DATO = " · "

/**
 * Plantilla «mapa con panel» del sistema WRC: el [mapa] a pantalla completa, una cabecera translúcida de carbón
 * ([BarraSuperior], en tema claro también) y abajo un panel de carbón ([TarjetaWrc] variante mapa) con el [panel]:
 * normalmente un [DatoGrandeWrc], una línea de ayuda, chips y acciones de icono. Respeta los márgenes seguros sin dejar
 * huecos: el carbón de la cabecera llega hasta el borde superior. Sin lógica: la pone quien la usa.
 */
@Suppress("LongParameterList") // API de plantilla: cabecera, mapa y panel
@Composable
fun MapaConPanelWrc(
    titulo: String,
    onVolver: () -> Unit,
    mapa: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    panel: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        mapa(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .testTag(ETIQUETA_MAPA_PANEL_CABECERA)
                    .background(CarbonTranslucido)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ),
            ) {
                TemaSobreCarbon { BarraSuperior(titulo = titulo, onVolver = onVolver, subtitulo = subtitulo) }
            }
            Box(Modifier.weight(1f))
            TarjetaWrc(
                variante = VarianteTarjeta.MAPA,
                relleno = Espacio.m,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                        ).padding(Espacio.s)
                        .testTag(ETIQUETA_MAPA_PANEL),
                contenido = panel,
            )
        }
    }
}

/** Una cifra del [DatoGrandeWrc]: el [valor] grande y su [unidad] pequeña al lado («1 240» + «teselas»). */
data class ParteDatoWrc(
    val valor: String,
    val unidad: String = "",
)

/**
 * Dato protagonista de un panel sobre el mapa: cifras en estilo [Tiempo] (cursiva Black, ancho fijo) con su unidad
 * pequeña, separadas por un punto medio («1 240 teselas · 86 MB»). Es un solo texto, así que TalkBack lo lee entero y,
 * si no cabe, baja de línea.
 */
@Composable
fun DatoGrandeWrc(
    partes: List<ParteDatoWrc>,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurface
    val colorUnidad = MaterialTheme.colorScheme.onSurfaceVariant
    val texto =
        buildAnnotatedString {
            partes.forEachIndexed { indice, parte ->
                if (indice > 0) {
                    withStyle(SpanStyle(color = colorUnidad, fontSize = UNIDAD_DATO)) { append(SEPARADOR_DATO) }
                }
                append(parte.valor)
                if (parte.unidad.isNotEmpty()) {
                    withStyle(
                        SpanStyle(
                            color = colorUnidad,
                            fontSize = UNIDAD_DATO,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Normal,
                        ),
                    ) { append(" ${parte.unidad}") }
                }
            }
        }
    Text(
        text = texto,
        style = Tiempo,
        color = color,
        modifier = modifier.testTag(ETIQUETA_DATO_GRANDE),
    )
}
