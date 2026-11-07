package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.alertas.AlertasPanel
import app.trombito.rallycopilot.ui.alertas.MediaTramoUi
import app.trombito.rallycopilot.ui.comun.ETIQUETA_PILDORA_VELOCIDAD
import app.trombito.rallycopilot.ui.comun.ETIQUETA_SENAL_LIMITE
import app.trombito.rallycopilot.ui.comun.PildoraVelocidad
import app.trombito.rallycopilot.ui.comun.rememberLectorPantallaActivo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally

/** La píldora de velocidad (la velocidad actual, con o sin límite). */
const val ETIQUETA_VELOCIDAD = ETIQUETA_PILDORA_VELOCIDAD

/** La señal de límite dentro de la píldora; no existe sin límite de la vía. */
const val ETIQUETA_LIMITE = ETIQUETA_SENAL_LIMITE

/** Nodo de accesibilidad de «por encima del límite» sobre la píldora (existe solo mientras se pasa del límite). */
const val ETIQUETA_EXCESO = "panel-exceso"

/** Media del radar de tramo junto a la píldora. */
const val ETIQUETA_MEDIA = "panel-media"

private val FondoMedia = Color(0xFF0E0F12)
private val TextoMedia = Color(0xFFECEDEF)
private val ANCHO_MEDIA = 76.dp
private val RADIO_MEDIA = 10.dp
private val BORDE_MEDIA = 3.dp

/**
 * Píldora de velocidad y límite de la conducción, con la media del radar de tramo a su lado si hay uno en curso.
 * Sin fijación de GPS ([sinSenal]) la velocidad es «—». Con [limiteDesconocido] (vía sin límite en los datos) lleva la
 * señal gris «?». Pasarse del límite pulsa el borde en rojo (sin relleno) y
 * avisa a TalkBack una sola vez, cuando empieza (salvo con TalkBack activo: ya lo dice el copiloto).
 */
@Composable
@Suppress("LongParameterList") // velocidad, límite, alertas, señal y su aspecto, ya resueltos por la pantalla
internal fun PildoraConduccion(
    velocidadKmh: Int?,
    limiteKmh: Int?,
    alertas: AlertasPanel,
    sinSenal: Boolean,
    modifier: Modifier = Modifier,
    limiteDesconocido: Boolean = false,
) {
    val velocidad = velocidadKmh.takeUnless { sinSenal }
    val excede = alertas.rojo && velocidad != null
    val lectorActivo = rememberLectorPantallaActivo()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box {
            PildoraVelocidad(velocidad, limiteKmh, excede = excede, limiteDesconocido = limiteDesconocido)
            // Nodo propio sobre la píldora: su región viva se anuncia al aparecer el exceso, no con cada cifra.
            if (excede) {
                val aviso = stringResource(R.string.panel_exceso_descripcion)
                Box(
                    Modifier
                        .matchParentSize()
                        .testTag(ETIQUETA_EXCESO)
                        .semantics {
                            contentDescription = aviso
                            // Con TalkBack el copiloto ya dice el exceso: no hablan dos voces.
                            if (!lectorActivo) liveRegion = LiveRegionMode.Polite
                        },
                )
            }
        }
        alertas.tramo?.let { MediaTramo(it) }
    }
}

/**
 * «MEDIA 78»: velocidad media del radar de tramo, en rojo si supera el límite; con el tramo ya terminado la etiqueta
 * es «FINAL» (se ve unos segundos). Sin media todavía (menos de 100 m recorridos) lleva una raya.
 */
@Composable
private fun MediaTramo(tramo: MediaTramoUi) {
    val colores = LocalColoresRally.current
    val color = if (tramo.supera) colores.deltaPeor else colores.aviso
    val forma = RoundedCornerShape(RADIO_MEDIA)
    val media = tramo.mediaKmh
    val base =
        when {
            media == null -> stringResource(R.string.panel_media_sin_dato)
            tramo.terminado -> stringResource(R.string.panel_media_final_descripcion, media)
            else -> stringResource(R.string.panel_media_descripcion, media)
        }
    val descripcion = if (tramo.supera) stringResource(R.string.panel_media_supera, base) else base
    val etiqueta = if (tramo.terminado) R.string.panel_media_final_etiqueta else R.string.panel_media_etiqueta
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .width(ANCHO_MEDIA)
                .testTag(ETIQUETA_MEDIA)
                .background(FondoMedia, forma)
                .border(BORDE_MEDIA, color, forma)
                .padding(vertical = Espacio.xs)
                .semantics(mergeDescendants = true) { contentDescription = descripcion },
    ) {
        Text(
            text = stringResource(etiqueta),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = color,
        )
        Text(
            text = media?.toString() ?: stringResource(R.string.panel_media_vacia),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = if (tramo.supera) colores.deltaPeor else TextoMedia,
        )
    }
}
