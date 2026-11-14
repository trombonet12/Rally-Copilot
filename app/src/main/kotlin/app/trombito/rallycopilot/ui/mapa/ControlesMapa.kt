package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.BotonRedondoMapa
import app.trombito.rallycopilot.ui.comun.EstadoBotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.TemaSobreCarbon
import app.trombito.rallycopilot.ui.theme.CarbonTranslucido
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_CAPAS_BOTON = "mapa-capas-boton"
const val ETIQUETA_CAPAS_PANEL = "mapa-capas-panel"
const val ETIQUETA_RELIEVE = "mapa-relieve"
const val ETIQUETA_ORIENTACION = "mapa-orientacion"
const val ETIQUETA_CALOR = "mapa-calor"
const val ETIQUETA_CALOR_USO = "mapa-calor-uso"
const val ETIQUETA_CALOR_VELOCIDAD = "mapa-calor-velocidad"

private val RADIO_PANEL = 28.dp

/**
 * Botones redondos del mapa, solo con iconos, para el panel negro inferior: capas (que despliega [PanelCapas] sobre el
 * mapa) y orientación (rumbo arriba / norte arriba). Van con la bandera de terminar a los lados de la velocidad.
 */
@Composable
internal fun BotonCapas(
    abierto: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BotonRedondoMapa(
        descripcion = stringResource(R.string.mapa_capas_boton),
        onClick = onClick,
        modifier = modifier.testTag(ETIQUETA_CAPAS_BOTON),
        activo = abierto,
    ) { Icon(Iconos.Capas, contentDescription = null) }
}

/**
 * Base Mapa/Satélite e interruptor de relieve y, al lado, el interruptor de la capa de calor con sus dos modos (uso y
 * velocidad media); se pinta sobre el mapa, encima del botón de capas.
 */
@Composable
internal fun PanelCapas(
    estado: MapaUiState,
    acciones: AccionesMapa,
    modifier: Modifier = Modifier,
) {
    val descripcion = stringResource(R.string.mapa_capa_selector)
    // Carbón fijo, como los botones de la columna: en tema claro el panel no mezcla dos estilos.
    TemaSobreCarbon {
        Surface(
            shape = RoundedCornerShape(RADIO_PANEL),
            color = CarbonTranslucido,
            modifier = modifier.testTag(ETIQUETA_CAPAS_PANEL).semantics { contentDescription = descripcion },
        ) {
            Row(Modifier.padding(Espacio.xs), horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                    BotonCapa(CapaMapa.VECTORIAL, estado, acciones.onCapa)
                    BotonCapa(CapaMapa.SATELITE, estado, acciones.onCapa)
                    BotonRelieve(estado.relieve, acciones.onRelieve)
                }
                Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                    BotonCalor(estado.calor.activo, acciones.onCalor)
                    BotonModoCalor(ModoCalor.USO, estado.calor, acciones.onModoCalor)
                    BotonModoCalor(ModoCalor.VELOCIDAD, estado.calor, acciones.onModoCalor)
                }
            }
        }
    }
}

@Composable
private fun BotonCapa(
    capa: CapaMapa,
    estado: MapaUiState,
    onCapa: (CapaMapa) -> Unit,
) {
    val satelite = capa == CapaMapa.SATELITE
    val elegida = estado.capa == capa
    val disponible = !satelite || estado.puedeSatelite
    val textoEstado =
        when {
            !disponible -> stringResource(R.string.mapa_capa_satelite_no_instalado)
            elegida -> stringResource(R.string.mapa_estado_seleccionado)
            else -> null
        }
    BotonIcono(
        descripcion = stringResource(if (satelite) R.string.mapa_capa_satelite else R.string.mapa_capa_mapa),
        onClick = { onCapa(capa) },
        modifier = Modifier.testTag(if (satelite) ETIQUETA_CAPA_SATELITE else ETIQUETA_CAPA_MAPA),
        estado =
            EstadoBotonIcono(
                seleccionado = elegida,
                habilitado = disponible,
                descripcionEstado = textoEstado,
                rol = Role.RadioButton,
            ),
    ) { Icon(if (satelite) Iconos.Satelite else Iconos.Mapa, contentDescription = null) }
}

@Composable
private fun BotonRelieve(
    activo: Boolean,
    onRelieve: (Boolean) -> Unit,
) {
    BotonIcono(
        descripcion = stringResource(R.string.mapa_relieve),
        onClick = { onRelieve(!activo) },
        modifier = Modifier.testTag(ETIQUETA_RELIEVE),
        estado =
            EstadoBotonIcono(
                seleccionado = activo,
                descripcionEstado =
                    stringResource(if (activo) R.string.mapa_estado_activado else R.string.mapa_estado_desactivado),
                rol = Role.Switch,
            ),
    ) { Icon(Iconos.Relieve, contentDescription = null) }
}

@Composable
private fun BotonCalor(
    activo: Boolean,
    onCalor: (Boolean) -> Unit,
) {
    BotonIcono(
        descripcion = stringResource(R.string.calor_capa),
        onClick = { onCalor(!activo) },
        modifier = Modifier.testTag(ETIQUETA_CALOR),
        estado =
            EstadoBotonIcono(
                seleccionado = activo,
                descripcionEstado =
                    stringResource(if (activo) R.string.mapa_estado_activado else R.string.mapa_estado_desactivado),
                rol = Role.Switch,
            ),
    ) { Icon(Iconos.Calor, contentDescription = null) }
}

/** Uno de los dos modos de la capa de calor; sin la capa encendida se ven apagados y no se pueden cambiar. */
@Composable
private fun BotonModoCalor(
    modo: ModoCalor,
    calor: CalorUi,
    onModo: (ModoCalor) -> Unit,
) {
    val velocidad = modo == ModoCalor.VELOCIDAD
    val elegido = calor.modo == modo
    BotonIcono(
        descripcion = stringResource(if (velocidad) R.string.calor_modo_velocidad else R.string.calor_modo_uso),
        onClick = { onModo(modo) },
        modifier = Modifier.testTag(if (velocidad) ETIQUETA_CALOR_VELOCIDAD else ETIQUETA_CALOR_USO),
        estado =
            EstadoBotonIcono(
                seleccionado = elegido && calor.activo,
                habilitado = calor.activo,
                descripcionEstado = if (elegido) stringResource(R.string.mapa_estado_seleccionado) else null,
                rol = Role.RadioButton,
            ),
    ) { Icon(if (velocidad) Iconos.Velocimetro else Iconos.Repetir, contentDescription = null) }
}

/**
 * El botón de la brújula: con la cámara siguiendo al coche alterna rumbo arriba / norte arriba; si el mapa se movió
 * ([siguiendo] falso) pasa a ser el de «mi ubicación» y vuelve a centrar en el coche.
 */
@Composable
internal fun BotonOrientacion(
    orientacion: Orientacion,
    onOrientacion: (Orientacion) -> Unit,
    modifier: Modifier = Modifier,
    siguiendo: Boolean = true,
    onRecentrar: () -> Unit = {},
) {
    if (!siguiendo) {
        BotonRedondoMapa(
            descripcion = stringResource(R.string.mapa_recentrar),
            onClick = onRecentrar,
            modifier = modifier.testTag(ETIQUETA_RECENTRAR),
        ) { Icon(Iconos.MiUbicacion, contentDescription = null) }
        return
    }
    val rumbo = orientacion == Orientacion.RUMBO
    val icono: ImageVector = if (rumbo) Iconos.Rumbo else Iconos.Brujula
    BotonRedondoMapa(
        descripcion = stringResource(if (rumbo) R.string.mapa_orientacion_rumbo else R.string.mapa_orientacion_norte),
        onClick = { onOrientacion(if (rumbo) Orientacion.NORTE else Orientacion.RUMBO) },
        modifier = modifier.testTag(ETIQUETA_ORIENTACION),
    ) { Icon(icono, contentDescription = null) }
}
