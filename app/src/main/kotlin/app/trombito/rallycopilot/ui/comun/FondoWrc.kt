package app.trombito.rallycopilot.ui.comun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.ui.theme.LocalTemaOscuro
import app.trombito.rallycopilot.ui.theme.Medidas

/** Etiqueta de prueba del degradado superior del mapa. */
const val ETIQUETA_DEGRADADO_MAPA = "degradado-superior-mapa"

/** Etiqueta de prueba del fondo WRC. */
const val ETIQUETA_FONDO_WRC = "fondo-wrc"

private val PASO_CARBONO = 8.dp
private const val OPACIDAD_CARBONO_OSCURO = 0.05f
private const val OPACIDAD_CARBONO_CLARO = 0.04f
private const val OPACIDAD_NEGRO_SUPERIOR = 0.6f

/**
 * Fondo de las pantallas fuera del mapa (Inicio): degradado vertical del carbón y una trama de fibra de carbono muy
 * sutil dibujada en Canvas (dos familias de diagonales). Sirve en tema claro y oscuro; el [contenido] va encima.
 */
@Composable
fun FondoWrc(
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit = {},
) {
    val colores = MaterialTheme.colorScheme
    val oscuro = LocalTemaOscuro.current
    val arriba = if (oscuro) colores.surfaceContainerLow else colores.surfaceContainerLowest
    val abajo = if (oscuro) colores.surfaceContainerLowest else colores.surface
    val traza = if (oscuro) Color.White.copy(OPACIDAD_CARBONO_OSCURO) else Color.Black.copy(OPACIDAD_CARBONO_CLARO)
    Box(
        modifier
            .fillMaxSize()
            .testTag(ETIQUETA_FONDO_WRC)
            .drawWithCache {
                val paso = PASO_CARBONO.toPx()
                val camino = Path()
                var x = -size.height
                while (x < size.width) {
                    camino.moveTo(x, 0f)
                    camino.lineTo(x + size.height, size.height)
                    camino.moveTo(x + size.height, 0f)
                    camino.lineTo(x, size.height)
                    x += paso
                }
                val degradado = Brush.verticalGradient(listOf(arriba, abajo))
                onDrawBehind {
                    drawRect(degradado)
                    drawPath(camino, traza, style = Stroke(width = 1f))
                }
            },
    ) { contenido() }
}

/**
 * Degradado negro de 96 dp (60 % arriba, 0 % abajo) para poner bajo la barra de estado: los rótulos del mapa no
 * chocan con la hora y los iconos del sistema. No intercepta toques.
 */
@Composable
fun DegradadoSuperiorMapa(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Medidas.alturaDegradadoMapa)
            .testTag(ETIQUETA_DEGRADADO_MAPA)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(OPACIDAD_NEGRO_SUPERIOR), Color.Transparent)))
            .clearAndSetSemantics {},
    )
}
