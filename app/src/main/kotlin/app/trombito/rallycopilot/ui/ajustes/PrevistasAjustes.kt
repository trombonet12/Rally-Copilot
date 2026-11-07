package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.copiloto.IconoModo
import app.trombito.rallycopilot.ui.copiloto.detalle
import app.trombito.rallycopilot.ui.copiloto.nombre
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono
import app.trombito.rallycopilot.ui.theme.Movimiento
import app.trombito.rallycopilot.ui.theme.RojoAcento

private val ALTO_COPILOTO = 200.dp
private val ALTO_NOTAS = 184.dp
private val ALTO_ESTANDAR = 168.dp
private val ALTO_BARRA = 44.dp
private val MARGEN_BARRA = 12.dp
private val ANCHO_ETIQUETA_AVISO = 44.dp
private const val OPACIDAD_SIN_VOZ = 0.35f
private const val GROSOR_PISTA = 4f
private const val RADIO_AVISO = 9f
private const val LADO_CURVA = 12f

/** Alto de la vista previa fija de una sección (160 a 200 dp), o null si no tiene (Datos y Acerca de). */
internal fun altoPrevista(seccion: SeccionAjustes): Dp? =
    when (seccion) {
        SeccionAjustes.COPILOTO -> ALTO_COPILOTO
        SeccionAjustes.NOTAS -> ALTO_NOTAS
        SeccionAjustes.MAPA, SeccionAjustes.VOZ -> ALTO_COPILOTO
        SeccionAjustes.DATOS, SeccionAjustes.AYUDA, SeccionAjustes.ACERCA_DE -> null
        else -> ALTO_ESTANDAR
    }

/**
 * Vista previa fija de una subpantalla: una tarjeta de [alto] que no se desplaza y cambia en vivo al tocar los
 * controles de debajo.
 */
@Composable
internal fun PrevistaDeSeccion(
    seccion: SeccionAjustes,
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
    alto: Dp,
    modifier: Modifier = Modifier,
) {
    TarjetaWrc(
        modifier =
            modifier
                .fillMaxWidth()
                .height(alto)
                .testTag(ETIQUETA_SECCION_PREVISTA_PREFIJO + seccion.clave),
        relleno = Espacio.m,
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            when (seccion) {
                SeccionAjustes.COPILOTO -> PrevistaCopiloto(ajustes, acciones)
                SeccionAjustes.NOTAS -> PrevistaNotas(ajustes)
                SeccionAjustes.VOZ -> PrevistaVoz(ajustes, acciones)
                SeccionAjustes.INDICACIONES -> PrevistaIndicaciones(ajustes)
                SeccionAjustes.TRAMOS -> PrevistaTramos(ajustes)
                SeccionAjustes.MAPA -> PrevistaMapa(ajustes)
                SeccionAjustes.ESCUCHA -> PrevistaEscucha(ajustes.activarConCopiloto)
                SeccionAjustes.TEMA -> PrevistaTema(ajustes.tema)
                SeccionAjustes.DATOS, SeccionAjustes.AYUDA, SeccionAjustes.ACERCA_DE -> Unit
            }
        }
    }
}

/** El modo dice las notas de rally solo en Notas y Completo. */
private fun ModoCopiloto.diceNotas(): Boolean = this == ModoCopiloto.NOTAS || this == ModoCopiloto.COMPLETO

/** Copiloto: la nota de ejemplo (apagada si el modo no la dice), el modo elegido y «Escuchar ejemplo». */
@Composable
private fun PrevistaCopiloto(
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
) {
    val modo = ajustes.modoCopiloto
    val opacidad by animateFloatAsState(
        targetValue = if (modo.diceNotas()) 1f else OPACIDAD_SIN_VOZ,
        animationSpec = tween(Movimiento.MEDIA),
        label = "opacidad-nota",
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        TarjetaNotaEjemplo(ajustes.notasSimplificadas, Modifier.fillMaxWidth().alpha(opacidad))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
            IconoModo(modo)
            Column {
                Text(stringResource(modo.nombre()), style = Cuerpo, fontWeight = FontWeight.Bold)
                Text(
                    text = stringResource(modo.detalle()),
                    style = Ayuda,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        BotonEscucharEjemplo(acciones.onEscucharEjemplo, Modifier.fillMaxWidth())
    }
}

/** Notas: la tarjeta de ejemplo y una barra con el punto donde suena el aviso respecto de la curva. */
@Composable
private fun PrevistaNotas(ajustes: AjustesConduccion) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        TarjetaNotaEjemplo(ajustes.notasSimplificadas, Modifier.fillMaxWidth(), reducida = true)
        BarraAnticipacion(ajustes.anticipacion)
        TextoAnticipacion(ajustes.anticipacion)
    }
}

/** Una pista que acaba en la curva; el punto del aviso se desliza al cambiar el nivel de anticipación. */
@Composable
private fun BarraAnticipacion(nivel: NivelAnticipacion) {
    val maximo = metrosDeAnticipacion(NivelAnticipacion.MUY_PRONTO).toFloat()
    val metros = metrosDeAnticipacion(nivel)
    val fraccion by animateFloatAsState(
        targetValue = metros / maximo,
        animationSpec = tween(Movimiento.MEDIA),
        label = "anticipacion",
    )
    val descripcion = stringResource(R.string.ajustes_prevista_distancia_descripcion, metros)
    val primario = MaterialTheme.colorScheme.primary
    val pista = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(ALTO_BARRA)
            .testTag(ETIQUETA_PREVISTA_BARRA)
            .semantics { contentDescription = descripcion },
    ) {
        val x = MARGEN_BARRA + (maxWidth - MARGEN_BARRA * 2) * (1f - fraccion)
        PistaAnticipacion(this.maxWidth, primario, pista, 1f - fraccion)
        Text(
            text = stringResource(R.string.ajustes_prevista_aviso),
            style = EtiquetaIcono,
            color = primario,
            modifier = Modifier.offset(x = (x - ANCHO_ETIQUETA_AVISO / 2).coerceAtLeast(0.dp)).clearAndSetSemantics {},
        )
        Text(
            text = stringResource(R.string.ajustes_prevista_curva),
            style = EtiquetaIcono,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopEnd).clearAndSetSemantics {},
        )
    }
}

@Composable
private fun PistaAnticipacion(
    ancho: Dp,
    primario: Color,
    pista: Color,
    posicion: Float,
) {
    Canvas(Modifier.width(ancho).height(ALTO_BARRA)) {
        val margen = MARGEN_BARRA.toPx()
        val y = size.height - margen
        val inicio = margen
        val fin = size.width - margen
        val xAviso = inicio + (fin - inicio) * posicion
        drawLine(pista, Offset(inicio, y), Offset(fin, y), GROSOR_PISTA.dp.toPx(), StrokeCap.Round)
        drawLine(primario, Offset(xAviso, y), Offset(fin, y), GROSOR_PISTA.dp.toPx(), StrokeCap.Round)
        drawCircle(primario, RADIO_AVISO.dp.toPx(), Offset(xAviso, y))
        drawCircle(pista, RADIO_AVISO.dp.toPx() / 2, Offset(xAviso, y))
        val lado = LADO_CURVA.dp.toPx()
        drawRect(RojoAcento, Offset(fin - lado / 2, y - lado / 2), Size(lado, lado))
        drawCircle(primario, RADIO_AVISO.dp.toPx(), Offset(xAviso, y), style = Stroke(1.5f.dp.toPx()))
    }
}

/**
 * Voz: el nombre real de la voz, barras de ecualizador que suben y bajan con el volumen y ▶ para oírla; debajo, los
 * tres avisos (apagado = tachado) y la velocidad de habla elegida.
 */
@Composable
private fun PrevistaVoz(
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
) {
    val voz = ajustes.voz
    val nombre = voz?.let { stringResource(it.nombreCatalogo()) }
    val descripcion =
        if (nombre == null) {
            stringResource(R.string.ajustes_prevista_voz_sin_elegir)
        } else {
            stringResource(R.string.ajustes_prevista_voz_descripcion, nombre, ajustes.volumenVoz)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    ETIQUETA_PREVISTA_VOZ,
                ).semantics { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.l),
    ) {
        Ecualizador(ajustes.volumenVoz)
        Column(Modifier.weight(1f).clearAndSetSemantics {}) {
            Text(
                text = nombre ?: stringResource(R.string.ajustes_prevista_voz_sin_elegir),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color =
                    if (nombre ==
                        null
                    ) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                maxLines = 2,
                modifier = Modifier.testTag(ETIQUETA_PREVISTA_VOZ_NOMBRE),
            )
            Text(
                text = stringResource(R.string.ajustes_prevista_voz_volumen, ajustes.volumenVoz),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BotonIcono(
            descripcion = stringResource(R.string.ajustes_prevista_escuchar_descripcion),
            onClick = acciones.onEscucharEjemplo,
            modifier = Modifier.testTag(ETIQUETA_PREVISTA_ESCUCHAR),
        ) { Icon(Iconos.Empezar, contentDescription = null) }
    }
    PrevistaAvisosYHabla(ajustes)
}

private val ALTURAS_ECUALIZADOR = listOf(0.45f, 0.8f, 1f, 0.7f, 0.4f)
private val ALTO_ECUALIZADOR = 64.dp

/** Cinco barras cuya altura sigue al volumen (50 a 150 %). */
@Composable
private fun Ecualizador(volumen: Int) {
    val nivel =
        (volumen - AjustesConduccion.VOLUMEN_VOZ_MIN).toFloat() /
            (AjustesConduccion.VOLUMEN_VOZ_MAX - AjustesConduccion.VOLUMEN_VOZ_MIN)
    Row(
        modifier = Modifier.height(ALTO_ECUALIZADOR).clearAndSetSemantics {},
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        ALTURAS_ECUALIZADOR.forEach { forma ->
            val altura by animateFloatAsState(
                targetValue = 0.2f + 0.8f * forma * (0.25f + 0.75f * nivel),
                animationSpec = tween(Movimiento.MEDIA),
                label = "barra",
            )
            Box(
                Modifier
                    .width(8.dp)
                    .height(ALTO_ECUALIZADOR * altura)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
            )
        }
    }
}

/** Escucha: micrófono con halo ámbar cuando «Copiloto» está activo y el estado en una línea. */
@Composable
private fun PrevistaEscucha(activa: Boolean) {
    val descripcion =
        stringResource(
            if (activa) {
                R.string.ajustes_prevista_escucha_si_descripcion
            } else {
                R.string.ajustes_prevista_escucha_no_descripcion
            },
        )
    val colores = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier.fillMaxWidth().testTag(ETIQUETA_PREVISTA_ESCUCHA).semantics {
                contentDescription =
                    descripcion
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.l),
    ) {
        Surface(
            shape = CircleShape,
            color = if (activa) colores.primaryContainer else colores.surfaceContainerHighest,
            border = if (activa) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Iconos.Microfono,
                    contentDescription = null,
                    tint = if (activa) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Text(
            text =
                stringResource(
                    if (activa) R.string.ajustes_prevista_escucha_si else R.string.ajustes_prevista_escucha_no,
                ),
            style = MaterialTheme.typography.titleMedium,
            color = if (activa) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).clearAndSetSemantics {},
        )
    }
}

const val ETIQUETA_PREVISTA_BARRA = "prevista-barra-anticipacion"
const val ETIQUETA_PREVISTA_VOZ = "prevista-voz"
const val ETIQUETA_PREVISTA_VOZ_NOMBRE = "prevista-voz-nombre"
const val ETIQUETA_PREVISTA_ESCUCHA = "prevista-escucha"
