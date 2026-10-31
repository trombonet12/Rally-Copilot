package app.trombito.rallycopilot.ui.navegacion.guiado

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.motor.ruta.FrasesManiobra
import app.trombito.rallycopilot.motor.ruta.Maniobra
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import app.trombito.rallycopilot.ui.maniobras.BannerManiobra
import app.trombito.rallycopilot.ui.maniobras.VarianteManiobra
import app.trombito.rallycopilot.ui.maniobras.formatearDistanciaManiobra
import app.trombito.rallycopilot.ui.navegacion.notas.FranjaNotas
import app.trombito.rallycopilot.ui.navegacion.notas.NotasUiState
import java.time.ZoneId

const val ETIQUETA_PANEL_GUIADO = "panel_guiado"

private val MARGEN = 8.dp

/** Lo que enseña el banner: maniobra, distancia hasta ella y la siguiente; [frase] null = «destino» (sin maniobras). */
internal data class DatosBanner(
    val tipo: TipoManiobra,
    val distanciaM: Double,
    val frase: String?,
    /** `ref` de la vía de la maniobra, para la placa; la [frase] ya no lo lleva. */
    val via: String?,
    val salidaRotonda: Int?,
    val siguiente: Pair<TipoManiobra, String>?,
)

/**
 * Con progreso: la maniobra actual y su distancia. Sin progreso: la primera maniobra que no sea la salida, a la
 * distancia desde el inicio de la ruta.
 * Si ya no quedan maniobras por delante (último tramo), «destino» a la distancia restante.
 */
internal fun datosBanner(estado: EstadoNavegacion.Guiando): DatosBanner? {
    val progreso = estado.progreso
    val actual: Maniobra?
    val siguiente: Maniobra?
    val distanciaM: Double
    if (progreso == null) {
        // Aún sin fijación: el banner nunca anuncia la salida; enseña el primer giro y lo que falta hasta él.
        val maniobras = estado.ruta.maniobras
        val indice = maniobras.indexOfFirst { it.tipo != TipoManiobra.SALIDA }
        actual = maniobras.getOrNull(indice)
        siguiente = if (indice >= 0) maniobras.getOrNull(indice + 1) else null
        distanciaM = if (indice > 0) maniobras.take(indice).sumOf { it.longitudM } else 0.0
    } else {
        actual = progreso.maniobraActual
        siguiente = progreso.maniobraSiguiente
        distanciaM = if (actual == null) progreso.distanciaRestanteM else progreso.distanciaAManiobraM
    }
    return when {
        actual != null -> {
            val (frase, via) = separarViaDeFrase(FrasesManiobra.frase(actual), actual.vias)
            DatosBanner(
                tipo = actual.tipo,
                distanciaM = distanciaM,
                frase = frase,
                via = via,
                salidaRotonda = actual.salidaRotonda,
                siguiente = siguiente?.let { it.tipo to FrasesManiobra.frase(it) },
            )
        }

        progreso != null -> {
            DatosBanner(TipoManiobra.DESTINO, distanciaM, null, null, null, null)
        }

        else -> {
            null
        }
    }
}

/**
 * Capa de guiado sobre el mapa (ocupa todo el espacio que se le da y no tapa el centro): arriba el banner de la
 * maniobra actual y la siguiente, a todo el ancho (los botones están en el panel inferior), con el aviso de
 * recálculo debajo, y abajo el pie con llegada, tiempo y km. Entre el banner y el aviso va la franja de notas (a todo
 * el ancho; no pinta nada si no hay notas). El pie va centrado en vertical y a la izquierda en horizontal. Sin
 * progreso todavía no hay pie.
 */
@Composable
fun PanelGuiado(
    estado: EstadoNavegacion.Guiando,
    variante: VarianteManiobra,
    modifier: Modifier = Modifier,
    zona: ZoneId = ZoneId.systemDefault(),
    notas: NotasUiState = NotasUiState(),
) {
    BoxWithConstraints(modifier = modifier.testTag(ETIQUETA_PANEL_GUIADO)) {
        val horizontal = maxWidth > maxHeight
        Column(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(MARGEN),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(MARGEN),
        ) {
            datosBanner(estado)?.let { Banner(it, variante) }
            FranjaNotas(notas)
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
) {
    val frase = datos.frase ?: stringResource(R.string.guiado_destino)
    val distancia = formatearDistanciaManiobra(datos.distanciaM)
    // La descripción hablada lleva la vía en la frase, como la oye el conductor.
    val hablada = datos.via?.let { "$frase, $it" } ?: frase
    val descripcion = stringResource(R.string.guiado_banner_descripcion, hablada, distancia)
    Box(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = descripcion }) {
        BannerManiobra(
            variante = variante,
            tipo = datos.tipo,
            distanciaM = datos.distanciaM,
            frase = frase,
            siguiente = datos.siguiente,
            modifier = Modifier.fillMaxWidth(),
            salidaRotonda = datos.salidaRotonda,
            via = datos.via,
        )
    }
}
