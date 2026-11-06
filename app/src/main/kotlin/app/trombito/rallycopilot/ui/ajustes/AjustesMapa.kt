package app.trombito.rallycopilot.ui.ajustes

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.nombre
import app.trombito.rallycopilot.ui.comun.recurso

const val ETIQUETA_CAPA_PREFIJO = "ajustes-capa-"
const val ETIQUETA_AJUSTES_RELIEVE = "ajustes-relieve"

private val DIBUJO_POSICION = 40.dp

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

@StringRes
private fun etiquetaNivelZoom(nivel: Int): Int =
    when (nivel) {
        AjustesConduccion.NIVEL_MIN -> R.string.ajustes_zoom_corto_muy_lejos
        -1 -> R.string.ajustes_zoom_corto_lejos
        0 -> R.string.ajustes_zoom_corto_normal
        1 -> R.string.ajustes_zoom_corto_cerca
        else -> R.string.ajustes_zoom_corto_muy_cerca
    }

@StringRes
private fun IconoPosicion.etiqueta(): Int =
    when (this) {
        IconoPosicion.FLECHA -> R.string.ajustes_icono_corto_flecha
        IconoPosicion.FLECHA_WRC -> R.string.ajustes_icono_corto_flecha_wrc
        IconoPosicion.COCHE_RALLY -> R.string.ajustes_icono_corto_coche
        IconoPosicion.CASCO -> R.string.ajustes_icono_corto_casco
    }

/** Mapa: orientación, icono de posición, mapa por defecto, relieve y zoom; el mapa de ejemplo de arriba los refleja. */
@Composable
internal fun ControlesMapa(
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
) {
    Opcion(R.string.ajustes_orientacion_titulo, R.string.ajustes_orientacion_detalle) {
        OpcionesOrientacion(ajustes.orientacion, acciones.onOrientacion)
    }
    Opcion(R.string.ajustes_icono_titulo, R.string.ajustes_icono_detalle) {
        OpcionesIcono(ajustes.icono, acciones.onIcono)
    }
    Opcion(R.string.ajustes_capa_titulo, R.string.ajustes_capa_detalle) {
        OpcionesCapa(ajustes.capa, acciones.onCapa)
    }
    FilaInterruptor(
        titulo = stringResource(R.string.ajustes_relieve),
        marcado = ajustes.relieve,
        onCambio = acciones.onRelieve,
        etiqueta = ETIQUETA_AJUSTES_RELIEVE,
        detalle = stringResource(R.string.ajustes_relieve_corto),
        info = stringResource(R.string.ajustes_relieve_detalle),
    )
    Opcion(R.string.ajustes_zoom_titulo, R.string.ajustes_zoom_detalle) {
        OpcionesZoom(ajustes.nivelZoom, acciones.onNivelZoom)
    }
}

@Composable
private fun OpcionesZoom(
    nivel: Int,
    onNivel: (Int) -> Unit,
) {
    FilaOpciones {
        (AjustesConduccion.NIVEL_MIN..AjustesConduccion.NIVEL_MAX).forEachIndexed { indice, valor ->
            BotonIconoEtiquetado(
                etiqueta = stringResource(etiquetaNivelZoom(valor)),
                descripcion = stringResource(textoNivelZoom(valor)),
                seleccionado = valor == nivel,
                onClick = { onNivel(valor) },
                etiquetaPrueba = ETIQUETA_ZOOM_PREFIJO + valor,
                modifier = Modifier.weight(1f),
            ) { Icon(Iconos.Lupa, contentDescription = null, modifier = Modifier.size(TAMANOS_ZOOM[indice])) }
        }
    }
}

@Composable
private fun OpcionesIcono(
    icono: IconoPosicion,
    onIcono: (IconoPosicion) -> Unit,
) {
    FilaOpciones {
        IconoPosicion.entries.forEach { opcion ->
            BotonIconoEtiquetado(
                etiqueta = stringResource(opcion.etiqueta()),
                descripcion = stringResource(opcion.nombre()),
                seleccionado = opcion == icono,
                onClick = { onIcono(opcion) },
                etiquetaPrueba = ETIQUETA_ICONO_PREFIJO + opcion.name.lowercase(),
                modifier = Modifier.weight(1f),
            ) { Image(painterResource(opcion.recurso()), contentDescription = null, Modifier.size(DIBUJO_POSICION)) }
        }
    }
}

@Composable
private fun OpcionesOrientacion(
    orientacion: Orientacion,
    onOrientacion: (Orientacion) -> Unit,
) {
    FilaOpciones {
        BotonIconoEtiquetado(
            etiqueta = stringResource(R.string.ajustes_orientacion_rumbo),
            descripcion = stringResource(R.string.ajustes_orientacion_rumbo),
            seleccionado = orientacion == Orientacion.RUMBO,
            onClick = { onOrientacion(Orientacion.RUMBO) },
            etiquetaPrueba = ETIQUETA_ORIENTACION_RUMBO,
            modifier = Modifier.weight(1f),
        ) { Icon(Iconos.Rumbo, contentDescription = null) }
        BotonIconoEtiquetado(
            etiqueta = stringResource(R.string.ajustes_orientacion_norte),
            descripcion = stringResource(R.string.ajustes_orientacion_norte),
            seleccionado = orientacion == Orientacion.NORTE,
            onClick = { onOrientacion(Orientacion.NORTE) },
            etiquetaPrueba = ETIQUETA_ORIENTACION_NORTE,
            modifier = Modifier.weight(1f),
        ) { Icon(Iconos.Brujula, contentDescription = null) }
    }
}

@Composable
private fun OpcionesCapa(
    capa: CapaMapa,
    onCapa: (CapaMapa) -> Unit,
) {
    FilaOpciones {
        CapaMapa.entries.forEach { opcion ->
            val satelite = opcion == CapaMapa.SATELITE
            val nombre = if (satelite) R.string.ajustes_capa_satelite else R.string.ajustes_capa_mapa
            BotonIconoEtiquetado(
                etiqueta = null,
                descripcion = stringResource(nombre),
                seleccionado = opcion == capa,
                onClick = { onCapa(opcion) },
                etiquetaPrueba = ETIQUETA_CAPA_PREFIJO + opcion.name.lowercase(),
                modifier = Modifier.weight(1f),
            ) { Icon(if (satelite) Iconos.Satelite else Iconos.Mapa, contentDescription = null) }
        }
    }
}
