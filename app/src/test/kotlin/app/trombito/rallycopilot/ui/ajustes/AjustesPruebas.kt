package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme

/**
 * Pone Ajustes con estado propio, como con el ViewModel: lo que se toca se aplica. Con [seccion] abre esa subpantalla;
 * sin ella arranca en la portada y tocar una tarjeta navega a su subpantalla (volver regresa a la portada).
 * Los callbacks de [acciones] se llaman además de aplicar el cambio.
 */
@Suppress("LongMethod") // un callback por ajuste: es la lista de lo que el ViewModel aplica
internal fun BancoCompose.ponerAjustes(
    inicial: AjustesConduccion = AjustesConduccion(),
    seccion: SeccionAjustes? = null,
    acciones: AccionesAjustes = AccionesAjustes(),
    copiaEnCurso: Boolean = false,
) {
    poner {
        var ajustes by remember { mutableStateOf(inicial) }
        var actual by remember { mutableStateOf(seccion) }
        val aplicadas =
            acciones.copy(
                onVolver = {
                    if (actual == null) acciones.onVolver() else actual = null
                },
                onSeccion = {
                    actual = it
                    acciones.onSeccion(it)
                },
                onNivelZoom = {
                    ajustes = ajustes.copy(nivelZoom = it)
                    acciones.onNivelZoom(it)
                },
                onIcono = {
                    ajustes = ajustes.copy(icono = it)
                    acciones.onIcono(it)
                },
                onOrientacion = {
                    ajustes = ajustes.copy(orientacion = it)
                    acciones.onOrientacion(it)
                },
                onAnticipacion = {
                    ajustes = ajustes.copy(anticipacion = it)
                    acciones.onAnticipacion(it)
                },
                onNotasSimplificadas = {
                    ajustes = ajustes.copy(notasSimplificadas = it)
                    acciones.onNotasSimplificadas(it)
                },
                onModoCopiloto = {
                    ajustes = ajustes.copy(modoCopiloto = it)
                    acciones.onModoCopiloto(it)
                },
                onVozCopiloto = {
                    ajustes = ajustes.copy(voz = it)
                    acciones.onVozCopiloto(it)
                },
                onVolumenVoz = {
                    ajustes = ajustes.copy(volumenVoz = it)
                    acciones.onVolumenVoz(it)
                },
                onTema = {
                    ajustes = ajustes.copy(tema = it)
                    acciones.onTema(it)
                },
                onCapa = {
                    ajustes = ajustes.copy(capa = it)
                    acciones.onCapa(it)
                },
                onRelieve = {
                    ajustes = ajustes.copy(relieve = it)
                    acciones.onRelieve(it)
                },
                onCronometrar = {
                    ajustes = ajustes.copy(cronometrar = it)
                    acciones.onCronometrar(it)
                },
                onSalidaManual = {
                    ajustes = ajustes.copy(salidaManual = it)
                    acciones.onSalidaManual(it)
                },
                onNotasGrandes = {
                    ajustes = ajustes.copy(notasGrandes = it)
                    acciones.onNotasGrandes(it)
                },
                onAvisarCruces = {
                    ajustes = ajustes.copy(avisarCruces = it)
                    acciones.onAvisarCruces(it)
                },
                onAvisarSalidasAutopista = {
                    ajustes = ajustes.copy(avisarSalidasAutopista = it)
                    acciones.onAvisarSalidasAutopista(it)
                },
                onAvisarRotondas = {
                    ajustes = ajustes.copy(avisarRotondas = it)
                    acciones.onAvisarRotondas(it)
                },
            )
        RallyCopilotTheme {
            val abierta = actual
            if (abierta == null) {
                AjustesScreen(ajustes, aplicadas)
            } else {
                AjustesSeccionScreen(abierta, ajustes, aplicadas, copiaEnCurso = copiaEnCurso)
            }
        }
    }
}

/** Pulsa el interruptor (el hijo pulsable) de la fila con esa etiqueta. */
internal fun BancoCompose.pulsarInterruptor(etiqueta: String) =
    pulsar(conEtiqueta(etiqueta).children.first { esPulsable(it) })

/** Todos los textos de lo que cuelga del nodo con esa etiqueta, unidos. */
internal fun BancoCompose.textoDe(etiqueta: String) = textos(conEtiqueta(etiqueta)).joinToString(" ")

/** `true` si [nodo] o algo de lo que cuelga de él lleva esa etiqueta de prueba. */
internal fun contieneEtiqueta(
    nodo: SemanticsNode,
    etiqueta: String,
): Boolean =
    nodo.config.getOrNull(SemanticsProperties.TestTag) == etiqueta ||
        nodo.children.any { contieneEtiqueta(it, etiqueta) }
