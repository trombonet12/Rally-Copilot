package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.copiloto.AccionesAvisos
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_VOZ_OPCION_PREFIJO
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_VOZ_PROBAR_PREFIJO
import app.trombito.rallycopilot.ui.copiloto.IconoModo
import app.trombito.rallycopilot.ui.copiloto.InterruptoresAvisos
import app.trombito.rallycopilot.ui.copiloto.SelectorVelocidadHabla
import app.trombito.rallycopilot.ui.copiloto.detalle
import app.trombito.rallycopilot.ui.copiloto.nombre
import app.trombito.rallycopilot.ui.copiloto.rememberSolicitudMicrofono
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Radios

const val ETIQUETA_AJUSTES_MODO_PREFIJO = "ajustes-modo-"
const val ETIQUETA_AJUSTES_VOZ_SIN_ELEGIR = "ajustes-voz-sin-elegir"
const val ETIQUETA_AJUSTES_ACTIVAR_COPILOTO = "ajustes-activar-con-copiloto"

/** Copiloto: los cuatro modos con icono y una línea cada uno. La vista previa de arriba enseña el elegido. */
@Composable
internal fun ControlesCopiloto(
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
) {
    Opcion(R.string.copiloto_modo_titulo, R.string.ajustes_modo_detalle) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            ModoCopiloto.entries.forEach { modo ->
                FilaModo(
                    modo = modo,
                    seleccionado = modo == ajustes.modoCopiloto,
                    onElegir = { acciones.onModoCopiloto(modo) },
                )
            }
        }
    }
}

/** Voz: las voces del catálogo con ▶ para oírlas y el volumen propio del copiloto. */
@Composable
internal fun ControlesVoz(
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
) {
    Opcion(R.string.copiloto_voz_titulo, R.string.ajustes_voz_detalle) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            if (ajustes.voz == null) {
                Text(
                    text = stringResource(R.string.copiloto_voz_sin_elegir),
                    style = Ayuda,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(ETIQUETA_AJUSTES_VOZ_SIN_ELEGIR),
                )
            }
            VozCopiloto.entries.forEach { voz ->
                FilaVoz(
                    voz = voz,
                    seleccionada = voz == ajustes.voz,
                    onElegir = { acciones.onVozCopiloto(voz) },
                    onProbar = { acciones.onProbarVoz(voz) },
                )
            }
        }
    }
    Opcion(R.string.ajustes_volumen_titulo, R.string.ajustes_volumen_aviso) {
        ControlVolumen(ajustes.volumenVoz, acciones.onVolumenVoz)
    }
    Opcion(R.string.habla_titulo, R.string.habla_detalle) {
        SelectorVelocidadHabla(ajustes.velocidadHabla, acciones.onVelocidadHabla)
    }
    Opcion(R.string.avisos_titulo, R.string.avisos_detalle) {
        val avisos = AccionesAvisos(acciones.onAvisarRadares, acciones.onAvisarIncidencias, acciones.onAvisarVelocidad)
        InterruptoresAvisos(ajustes.avisarRadares, ajustes.avisarIncidencias, ajustes.avisarVelocidad, avisos)
    }
}

/** Escucha: activar la palabra «Copiloto» durante la conducción (pide el micrófono antes de activarla). */
@Composable
internal fun ControlesEscucha(
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
) {
    val pedirMicrofono = rememberSolicitudMicrofono { acciones.onActivarConCopiloto(true) }
    FilaInterruptor(
        titulo = stringResource(R.string.ajustes_copiloto_palabra),
        marcado = ajustes.activarConCopiloto,
        onCambio = { activar -> if (activar) pedirMicrofono() else acciones.onActivarConCopiloto(false) },
        etiqueta = ETIQUETA_AJUSTES_ACTIVAR_COPILOTO,
        detalle = stringResource(R.string.ajustes_copiloto_palabra_corto),
        info = stringResource(R.string.ajustes_copiloto_palabra_detalle),
    )
}

@Composable
private fun FilaModo(
    modo: ModoCopiloto,
    seleccionado: Boolean,
    onElegir: () -> Unit,
) {
    val colores = MaterialTheme.colorScheme
    Surface(
        color = if (seleccionado) colores.primaryContainer else colores.surfaceContainerHigh,
        shape = Radios.formaTarjeta,
        border = if (seleccionado) BorderStroke(Medidas.bordeActivo, colores.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(ETIQUETA_AJUSTES_MODO_PREFIJO + modo.name.lowercase())
                    .selectable(selected = seleccionado, role = Role.RadioButton, onClick = onElegir)
                    .heightIn(min = Medidas.accionPrincipal)
                    .padding(horizontal = Espacio.l, vertical = Espacio.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            IconoModo(modo)
            Column(Modifier.weight(1f)) {
                Text(text = stringResource(modo.nombre()), style = Cuerpo, color = colores.onSurface)
                Text(
                    text = stringResource(modo.detalle()),
                    style = Ayuda,
                    color = colores.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            RadioButton(selected = seleccionado, onClick = null)
        }
    }
}

/** Una voz con su nombre del catálogo («Dave (davefx)», «Sharvard»), radio para elegirla y ▶ aparte para oírla. */
@Composable
private fun FilaVoz(
    voz: VozCopiloto,
    seleccionada: Boolean,
    onElegir: () -> Unit,
    onProbar: () -> Unit,
) {
    val colores = MaterialTheme.colorScheme
    val nombre = stringResource(voz.nombreCatalogo())
    val clave = voz.name.lowercase()
    Surface(
        color = if (seleccionada) colores.primaryContainer else colores.surfaceContainerHigh,
        shape = Radios.formaTarjeta,
        border = if (seleccionada) BorderStroke(Medidas.bordeActivo, colores.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = Espacio.l, end = Espacio.s), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .testTag(ETIQUETA_VOZ_OPCION_PREFIJO + clave)
                        .selectable(selected = seleccionada, role = Role.RadioButton, onClick = onElegir)
                        .heightIn(min = Medidas.accionPrincipal),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espacio.m),
            ) {
                RadioButton(selected = seleccionada, onClick = null)
                Text(text = nombre, style = Cuerpo, color = colores.onSurface)
            }
            BotonIcono(
                descripcion = stringResource(R.string.copiloto_voz_probar, nombre),
                onClick = onProbar,
                modifier = Modifier.testTag(ETIQUETA_VOZ_PROBAR_PREFIJO + clave),
            ) { Icon(Iconos.Empezar, contentDescription = null) }
        }
    }
}
