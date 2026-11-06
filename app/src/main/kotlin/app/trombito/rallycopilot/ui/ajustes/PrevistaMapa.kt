package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.nombre
import app.trombito.rallycopilot.ui.comun.recurso
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
import app.trombito.rallycopilot.ui.theme.CarbonOpaco
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono
import app.trombito.rallycopilot.ui.theme.LocalTemaOscuro
import app.trombito.rallycopilot.ui.theme.Movimiento
import app.trombito.rallycopilot.ui.theme.RojoRuta
import app.trombito.rallycopilot.ui.theme.TextoSobreCarbon

const val ETIQUETA_PREVISTA_MAPA = "prevista-mapa"
const val ETIQUETA_PREVISTA_MAPA_ICONO = "prevista-mapa-icono-"

/** Rumbo (grados, en sentido horario desde arriba) con el que va la carretera dibujada en el mapa de ejemplo. */
private const val RUMBO_EJEMPLO = 28f
private const val PASO_ZOOM = 0.18f
private const val LADO_LIENZO = 520
private const val OPACIDAD_RELIEVE = 0.55f

/**
 * Mapa de ejemplo dibujado: en «rumbo arriba» el mapa gira para que la carretera quede vertical y el icono apunte
 * arriba; en «norte arriba» el mapa queda fijo (la brújula marca el norte arriba) y el icono gira con la carretera.
 * El zoom acerca o aleja el dibujo, la capa cambia los colores y el relieve añade curvas de nivel.
 */
@Composable
internal fun PrevistaMapa(ajustes: AjustesConduccion) {
    val rumbo = ajustes.orientacion == Orientacion.RUMBO
    val giroMapa by animateFloatAsState(if (rumbo) -RUMBO_EJEMPLO else 0f, tween(Movimiento.LARGA), label = "giro-mapa")
    val giroIcono by animateFloatAsState(
        if (rumbo) 0f else RUMBO_EJEMPLO,
        tween(Movimiento.LARGA),
        label = "giro-icono",
    )
    val escala by animateFloatAsState(1f + PASO_ZOOM * ajustes.nivelZoom, tween(Movimiento.LARGA), label = "zoom")
    val descripcion =
        stringResource(
            R.string.ajustes_prevista_mapa_descripcion,
            stringResource(ajustes.orientacion.nombre()),
            stringResource(ajustes.capa.nombreCorto()),
            stringResource(ajustes.icono.nombre()),
        )
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .testTag(ETIQUETA_PREVISTA_MAPA)
                .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) {
        LienzoMapa(
            ajustes.capa,
            ajustes.relieve,
            LocalTemaOscuro.current,
            Modifier.graphicsLayer {
                rotationZ = giroMapa
                scaleX = escala
                scaleY = escala
            },
        )
        Image(
            painter = painterResource(ajustes.icono.recurso()),
            contentDescription = null,
            modifier =
                Modifier
                    .size(56.dp)
                    .graphicsLayer { rotationZ = giroIcono }
                    .testTag(ETIQUETA_PREVISTA_MAPA_ICONO + ajustes.icono.name.lowercase()),
        )
        Etiqueta(stringResource(ajustes.orientacion.nombre()), Modifier.align(Alignment.TopStart))
        Brujula(giroMapa, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun Etiqueta(
    texto: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = texto,
        style = EtiquetaIcono,
        color = TextoSobreCarbon,
        modifier =
            modifier
                .padding(8.dp)
                .background(CarbonOpaco.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clearAndSetSemantics {},
    )
}

/** La brújula marca dónde queda el norte: arriba con «norte arriba» y girada con el mapa en «rumbo arriba». */
@Composable
private fun Brujula(
    giro: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .padding(8.dp)
            .size(32.dp)
            .background(CarbonOpaco.copy(alpha = 0.85f), CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Iconos.Brujula,
            contentDescription = null,
            tint = AmbarSobreCarbon,
            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = giro },
        )
    }
}

@Composable
private fun LienzoMapa(
    capa: CapaMapa,
    relieve: Boolean,
    oscuro: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.requiredSize(LADO_LIENZO.dp)) {
        val satelite = capa == CapaMapa.SATELITE
        drawRect(
            if (satelite) {
                SATELITE_BASE
            } else if (oscuro) {
                VECTOR_OSCURO
            } else {
                VECTOR_CLARO
            },
        )
        if (satelite) manchasDeSatelite() else callesSecundarias(oscuro)
        if (relieve) {
            curvasDeNivel(
                if (satelite) {
                    Color.White
                } else if (oscuro) {
                    Color(0xFF8C93A0)
                } else {
                    Color(0xFF8A7B63)
                },
            )
        }
        carreteraEjemplo()
    }
}

private val VECTOR_OSCURO = Color(0xFF1F2328)
private val VECTOR_CLARO = Color(0xFFE9E6DD)
private val SATELITE_BASE = Color(0xFF2F4A2A)

private class Mancha(
    val x: Float,
    val y: Float,
    val ancho: Float,
    val alto: Float,
    val color: Color,
)

private val MANCHAS_SATELITE =
    listOf(
        Mancha(0.05f, 0.10f, 0.30f, 0.22f, Color(0xFF3F6236)),
        Mancha(0.55f, 0.05f, 0.35f, 0.30f, Color(0xFF5B5239)),
        Mancha(0.10f, 0.60f, 0.28f, 0.30f, Color(0xFF4A6E3C)),
        Mancha(0.62f, 0.55f, 0.30f, 0.38f, Color(0xFF3A5430)),
        Mancha(0.40f, 0.35f, 0.15f, 0.20f, Color(0xFF6A5F42)),
    )

private fun DrawScope.manchasDeSatelite() {
    MANCHAS_SATELITE.forEach { m ->
        drawRect(
            color = m.color,
            topLeft = Offset(size.width * m.x, size.height * m.y),
            size = Size(size.width * m.ancho, size.height * m.alto),
        )
    }
}

private fun DrawScope.callesSecundarias(oscuro: Boolean) {
    val color = if (oscuro) Color(0xFF30353C) else Color(0xFFFFFFFF)
    val trazo = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
    val rejilla =
        Path().apply {
            moveTo(0f, size.height * 0.30f)
            lineTo(size.width, size.height * 0.22f)
            moveTo(0f, size.height * 0.75f)
            lineTo(size.width, size.height * 0.82f)
            moveTo(size.width * 0.18f, 0f)
            lineTo(size.width * 0.25f, size.height)
            moveTo(size.width * 0.82f, 0f)
            lineTo(size.width * 0.75f, size.height)
        }
    drawPath(rejilla, color, style = trazo)
}

private fun DrawScope.curvasDeNivel(color: Color) {
    val centro = Offset(size.width * 0.22f, size.height * 0.25f)
    for (i in 1..CURVAS_DE_NIVEL) {
        val radio = i * 34.dp.toPx()
        drawOval(
            color = color.copy(alpha = OPACIDAD_RELIEVE),
            topLeft = Offset(centro.x - radio * 1.25f, centro.y - radio),
            size = Size(radio * 2.5f, radio * 2f),
            style = Stroke(width = 1.2f.dp.toPx()),
        )
    }
}

private const val CURVAS_DE_NIVEL = 5

/** La carretera pasa por el centro del lienzo con el rumbo [RUMBO_EJEMPLO]: borde carbón y relleno rojo de ruta. */
private fun DrawScope.carreteraEjemplo() {
    val centro = Offset(size.width / 2f, size.height / 2f)
    val radianes = Math.toRadians(RUMBO_EJEMPLO.toDouble())
    val dx = Math.sin(radianes).toFloat()
    val dy = -Math.cos(radianes).toFloat()
    val mitad = size.height / 2f
    val camino =
        Path().apply {
            moveTo(centro.x - dx * mitad, centro.y - dy * mitad)
            lineTo(centro.x + dx * mitad, centro.y + dy * mitad)
        }
    drawPath(camino, CarbonOpaco, style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round))
    drawPath(camino, RojoRuta, style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
}
