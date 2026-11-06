package app.trombito.rallycopilot.ui.comun

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
import app.trombito.rallycopilot.ui.theme.CarbonTranslucido
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Radios
import app.trombito.rallycopilot.ui.theme.TextoSobreCarbon

private const val OPACIDAD_DESHABILITADO = 0.38f

/**
 * Botón redondo que flota sobre el mapa: carbón translúcido con sombra, 48 dp (56 dp si [principal]). [activo] lo
 * resalta en ámbar (borde de 1,5 dp e icono ámbar). [etiqueta] (12 sp) solo cuando el icono no es obvio. Aunque la
 * app esté en tema claro el botón es oscuro: el icono se pinta con la paleta del carbón. [descripcion] es lo que
 * lee TalkBack; el [contenido] pinta el icono (24 dp en listas, 28 dp en cabeceras: ver `Medidas`).
 */
@Suppress("LongParameterList") // API de componente: cada opción visual es un parámetro con valor por defecto
@Composable
fun BotonRedondoMapa(
    descripcion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    principal: Boolean = false,
    activo: Boolean = false,
    habilitado: Boolean = true,
    etiqueta: String? = null,
    descripcionEstado: String? = null,
    contenido: @Composable () -> Unit,
) {
    val lado = if (principal) Medidas.accionPrincipal else Medidas.zonaTactil
    val colorIcono = if (activo) AmbarSobreCarbon else TextoSobreCarbon
    val borde =
        if (activo) {
            Modifier.border(BorderStroke(Medidas.bordeActivo, AmbarSobreCarbon), Radios.formaCirculo)
        } else {
            Modifier
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .alpha(if (habilitado) 1f else OPACIDAD_DESHABILITADO)
                .semantics(mergeDescendants = true) {
                    contentDescription = descripcion
                    role = Role.Button
                    selected = activo
                    descripcionEstado?.let { stateDescription = it }
                    // La acción también en el nodo que se fusiona: TalkBack y las pruebas la encuentran aquí.
                    if (habilitado) {
                        onClick {
                            onClick()
                            true
                        }
                    } else {
                        disabled()
                    }
                },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(lado)
                    .shadow(Medidas.sombraMapa, Radios.formaCirculo, clip = false)
                    .clip(Radios.formaCirculo)
                    .background(CarbonTranslucido)
                    .then(borde)
                    .clickable(enabled = habilitado, role = Role.Button, onClick = onClick),
        ) {
            CompositionLocalProvider(LocalContentColor provides colorIcono) { contenido() }
        }
        if (etiqueta != null) {
            Text(
                text = etiqueta,
                style = EtiquetaIcono,
                color = TextoSobreCarbon,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier =
                    Modifier
                        .padding(top = Espacio.xs)
                        .background(CarbonTranslucido, Radios.formaPildora)
                        .padding(horizontal = Espacio.s, vertical = Espacio.xs / 2)
                        .clearAndSetSemantics {},
            )
        }
    }
}
