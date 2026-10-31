package app.trombito.rallycopilot.ui.navegacion.guiado

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.motor.ruta.FrasesManiobra
import app.trombito.rallycopilot.motor.ruta.Maniobra
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import app.trombito.rallycopilot.ui.maniobras.BannerManiobra
import app.trombito.rallycopilot.ui.maniobras.VarianteManiobra
import app.trombito.rallycopilot.ui.maniobras.formatearDistanciaManiobra
import java.time.ZoneId

const val ETIQUETA_PANEL_GUIADO = "panel_guiado"

private val MARGEN = 8.dp
private val ANCHO_MAX_BANNER_HORIZONTAL = 400.dp

/** Lo que enseña el banner: maniobra, distancia hasta ella y la siguiente; [frase] null = «destino» (sin maniobras). */
internal data class DatosBanner(
    val tipo: TipoManiobra,
    val distanciaM: Double,
    val frase: String?,
    val salidaRotonda: Int?,
    val siguiente: Pair<TipoManiobra, String>?,
)

/**
 * Con progreso: la maniobra actual y su distancia. Sin progreso: la primera maniobra de la ruta y su longitud total.
 * Si ya no quedan maniobras por delante (último tramo), «destino» a la distancia restante.
 */
internal fun datosBanner(estado: EstadoNavegacion.Guiando): DatosBanner? {
    val progreso = estado.progreso
    val actual: Maniobra?
    val siguiente: Maniobra?
    val distanciaM: Double
    if (progreso == null) {
        actual = estado.ruta.maniobras.firstOrNull()
        siguiente = estado.ruta.maniobras.getOrNull(1)
        distanciaM = actual?.longitudM ?: 0.0
    } else {
        actual = progreso.maniobraActual
        siguiente = progreso.maniobraSiguiente
        distanciaM = if (actual == null) progreso.distanciaRestanteM else progreso.distanciaAManiobraM
    }
    return when {
        actual != null -> {
            DatosBanner(
                tipo = actual.tipo,
                distanciaM = distanciaM,
                frase = FrasesManiobra.frase(actual),
                salidaRotonda = actual.salidaRotonda,
                siguiente = siguiente?.let { it.tipo to FrasesManiobra.frase(it) },
            )
        }

        progreso != null -> {
            DatosBanner(TipoManiobra.DESTINO, distanciaM, null, null, null)
        }

        else -> {
            null
        }
    }
}

/**
 * Capa de guiado sobre el mapa (ocupa todo el espacio que se le da y no tapa el centro): arriba el banner de la
 * maniobra actual y la siguiente con el aviso de recálculo debajo, y abajo el pie con llegada, tiempo y km.
 * En vertical va a todo el ancho y centrado; en horizontal el banner queda arriba a la izquierda (≤ 400 dp) y el pie
 * abajo a la izquierda. Sin progreso todavía no hay pie. [margenLateralBanner] aparta solo el banner de los botones
 * laterales (volver y controles) sin estrechar el pie.
 */
@Composable
fun PanelGuiado(
    estado: EstadoNavegacion.Guiando,
    variante: VarianteManiobra,
    modifier: Modifier = Modifier,
    zona: ZoneId = ZoneId.systemDefault(),
    margenLateralBanner: Dp = 0.dp,
) {
    BoxWithConstraints(modifier = modifier.testTag(ETIQUETA_PANEL_GUIADO)) {
        val horizontal = maxWidth > maxHeight
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(horizontal = margenLateralBanner).padding(MARGEN),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(MARGEN),
        ) {
            datosBanner(estado)?.let { Banner(it, variante, horizontal) }
            AvisoRecalculo(estado.recalculando)
        }
        estado.progreso?.let {
            PieGuiado(
                progreso = it,
                modifier =
                    Modifier
                        .align(
                            if (horizontal) Alignment.BottomStart else Alignment.BottomCenter,
                        ).padding(MARGEN),
                zona = zona,
            )
        }
    }
}

@Composable
private fun Banner(
    datos: DatosBanner,
    variante: VarianteManiobra,
    horizontal: Boolean,
) {
    val frase = datos.frase ?: stringResource(R.string.guiado_destino)
    val distancia = formatearDistanciaManiobra(datos.distanciaM)
    val descripcion = stringResource(R.string.guiado_banner_descripcion, frase, distancia)
    Box(
        modifier =
            (if (horizontal) Modifier.widthIn(max = ANCHO_MAX_BANNER_HORIZONTAL) else Modifier.fillMaxWidth())
                .clearAndSetSemantics { contentDescription = descripcion },
    ) {
        BannerManiobra(
            variante = variante,
            tipo = datos.tipo,
            distanciaM = datos.distanciaM,
            frase = frase,
            siguiente = datos.siguiente,
            modifier = if (horizontal) Modifier else Modifier.fillMaxWidth(),
            salidaRotonda = datos.salidaRotonda,
        )
    }
}
