package app.trombito.rallycopilot.ui.ajustes

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.EstiloManiobras
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.nombre
import app.trombito.rallycopilot.ui.comun.recurso
import app.trombito.rallycopilot.ui.maniobras.IconoManiobra
import app.trombito.rallycopilot.ui.maniobras.nombre
import app.trombito.rallycopilot.ui.maniobras.variante
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_PREVISTA_MAPA = "prevista-mapa"
const val ETIQUETA_PREVISTA_MAPA_ICONO = "prevista-mapa-icono-"
const val ETIQUETA_PREVISTA_MAPA_MANIOBRA = "prevista-mapa-maniobra-"
const val ETIQUETA_CAPA_PREFIJO = "ajustes-capa-"
const val ETIQUETA_AJUSTES_RELIEVE = "ajustes-relieve"

private val LADO_MINIATURA = 64.dp
private val DIBUJO_MINIATURA = 44.dp
private val DIBUJO_PREVISTA = 56.dp

/** Tamaño del icono de cada nivel de zoom: crece de «más lejos» a «más cerca». */
private val TAMANOS_ZOOM = listOf(16.dp, 20.dp, 24.dp, 28.dp, 32.dp)

@StringRes
private fun textoNivelZoom(nivel: Int): Int =
    when (nivel) {
        AjustesConduccion.NIVEL_MIN -> R.string.ajustes_zoom_muy_lejos
        -1 -> R.string.ajustes_zoom_lejos
        0 -> R.string.ajustes_zoom_normal
        1 -> R.string.ajustes_zoom_cerca
        else -> R.string.ajustes_zoom_muy_cerca
    }

/** Mapa: zoom, orientación, icono de posición, estilo de las maniobras y mapa por defecto, con su vista previa. */
@Composable
internal fun SeccionMapa(
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
) {
    TarjetaSeccion(
        clave = "mapa",
        icono = Iconos.Mapa,
        titulo = R.string.ajustes_seccion_mapa,
        detalle = R.string.ajustes_seccion_mapa_detalle,
        vistaPrevia = { VistaPreviaMapa(ajustes.icono, ajustes.estiloManiobras) },
    ) {
        Opcion(R.string.ajustes_orientacion_titulo, R.string.ajustes_orientacion_detalle) {
            OpcionesOrientacion(ajustes.orientacion, acciones.onOrientacion)
        }
        Opcion(R.string.ajustes_icono_titulo, R.string.ajustes_icono_detalle) {
            OpcionesIcono(ajustes.icono, acciones.onIcono)
        }
        Opcion(R.string.ajustes_maniobras_titulo, R.string.ajustes_maniobras_detalle) {
            OpcionesManiobras(ajustes.estiloManiobras, acciones.onEstiloManiobras)
        }
        Opcion(R.string.ajustes_capa_titulo, R.string.ajustes_capa_detalle) {
            OpcionesCapa(ajustes.capa, acciones.onCapa)
        }
        FilaInterruptor(
            titulo = stringResource(R.string.ajustes_relieve),
            marcado = ajustes.relieve,
            onCambio = acciones.onRelieve,
            etiqueta = ETIQUETA_AJUSTES_RELIEVE,
            detalle = stringResource(R.string.ajustes_relieve_detalle),
        )
        Opcion(R.string.ajustes_zoom_titulo, R.string.ajustes_zoom_detalle) {
            OpcionesZoom(ajustes.nivelZoom, acciones.onNivelZoom)
        }
    }
}

/** Miniaturas del icono de posición y de la maniobra elegidos, tal como saldrán en el mapa. */
@Composable
private fun VistaPreviaMapa(
    icono: IconoPosicion,
    estilo: EstiloManiobras,
) {
    val descripcion =
        stringResource(
            R.string.ajustes_prevista_mapa_descripcion,
            stringResource(icono.nombre()),
            stringResource(estilo.nombre()),
        )
    Row(
        modifier = Modifier.testTag(ETIQUETA_PREVISTA_MAPA).semantics { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.l),
    ) {
        Image(
            painterResource(icono.recurso()),
            contentDescription = null,
            modifier = Modifier.size(DIBUJO_PREVISTA).testTag(ETIQUETA_PREVISTA_MAPA_ICONO + icono.name.lowercase()),
        )
        IconoManiobra(
            tipo = TipoManiobra.DERECHA,
            variante = estilo.variante(),
            modifier = Modifier.testTag(ETIQUETA_PREVISTA_MAPA_MANIOBRA + estilo.name.lowercase()),
            tamano = DIBUJO_PREVISTA,
            decorativo = true,
        )
        Text(
            text = stringResource(R.string.ajustes_prevista_mapa),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun OpcionesZoom(
    nivel: Int,
    onNivel: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s), verticalAlignment = Alignment.CenterVertically) {
        (AjustesConduccion.NIVEL_MIN..AjustesConduccion.NIVEL_MAX).forEachIndexed { indice, valor ->
            BotonIcono(
                descripcion = stringResource(textoNivelZoom(valor)),
                onClick = { onNivel(valor) },
                modifier = Modifier.testTag(ETIQUETA_ZOOM_PREFIJO + valor),
                estado = estadoSeleccion(valor == nivel),
            ) { Icon(Iconos.Lupa, contentDescription = null, modifier = Modifier.size(TAMANOS_ZOOM[indice])) }
        }
    }
}

@Composable
private fun OpcionesIcono(
    icono: IconoPosicion,
    onIcono: (IconoPosicion) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s), verticalAlignment = Alignment.CenterVertically) {
        IconoPosicion.entries.forEach { opcion ->
            BotonIcono(
                descripcion = stringResource(opcion.nombre()),
                onClick = { onIcono(opcion) },
                modifier = Modifier.size(LADO_MINIATURA).testTag(ETIQUETA_ICONO_PREFIJO + opcion.name.lowercase()),
                estado = estadoSeleccion(opcion == icono),
            ) {
                Image(painterResource(opcion.recurso()), contentDescription = null, Modifier.size(DIBUJO_MINIATURA))
            }
        }
    }
}

@Composable
private fun OpcionesManiobras(
    estilo: EstiloManiobras,
    onEstilo: (EstiloManiobras) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s), verticalAlignment = Alignment.CenterVertically) {
        EstiloManiobras.entries.forEach { opcion ->
            BotonIcono(
                descripcion = stringResource(opcion.nombre()),
                onClick = { onEstilo(opcion) },
                modifier = Modifier.size(LADO_MINIATURA).testTag(ETIQUETA_MANIOBRAS_PREFIJO + opcion.name.lowercase()),
                estado = estadoSeleccion(opcion == estilo),
            ) {
                IconoManiobra(
                    tipo = TipoManiobra.DERECHA,
                    variante = opcion.variante(),
                    tamano = DIBUJO_MINIATURA,
                    decorativo = true,
                )
            }
        }
    }
}

@Composable
private fun OpcionesOrientacion(
    orientacion: Orientacion,
    onOrientacion: (Orientacion) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        BotonIcono(
            descripcion = stringResource(R.string.ajustes_orientacion_rumbo),
            onClick = { onOrientacion(Orientacion.RUMBO) },
            modifier = Modifier.testTag(ETIQUETA_ORIENTACION_RUMBO),
            estado = estadoSeleccion(orientacion == Orientacion.RUMBO),
        ) { Icon(Iconos.Rumbo, contentDescription = null) }
        BotonIcono(
            descripcion = stringResource(R.string.ajustes_orientacion_norte),
            onClick = { onOrientacion(Orientacion.NORTE) },
            modifier = Modifier.testTag(ETIQUETA_ORIENTACION_NORTE),
            estado = estadoSeleccion(orientacion == Orientacion.NORTE),
        ) { Icon(Iconos.Brujula, contentDescription = null) }
    }
}

@Composable
private fun OpcionesCapa(
    capa: CapaMapa,
    onCapa: (CapaMapa) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        CapaMapa.entries.forEach { opcion ->
            val satelite = opcion == CapaMapa.SATELITE
            val nombre = if (satelite) R.string.ajustes_capa_satelite else R.string.ajustes_capa_mapa
            BotonIcono(
                descripcion = stringResource(nombre),
                onClick = { onCapa(opcion) },
                modifier = Modifier.testTag(ETIQUETA_CAPA_PREFIJO + opcion.name.lowercase()),
                estado = estadoSeleccion(opcion == capa),
            ) { Icon(if (satelite) Iconos.Satelite else Iconos.Mapa, contentDescription = null) }
        }
    }
}
