package app.trombito.rallycopilot.ui.tramos

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.navegacion.guiado.ColoresGuiado

/** Líneas del rótulo en vertical: el nombre de un tramo de vía («Ma-15 · km ~20,1→~32,4 · hacia Manacor») se parte. */
internal const val LINEAS_ROTULO_HUD = 2
private const val SEPARADOR_DESTINO = " · hacia "

/** Por debajo de esta distancia (m) a la meta el HUD dice «Meta» en vez de los metros. */
internal const val META_INMINENTE_M = 50.0

/** Paso (m) al que se redondea la distancia que dice el HUD. */
internal const val PASO_FIN_TRAMO_HUD_M = 50.0

/**
 * El rótulo del HUD: en apaisado ([compacto], una sola línea) el nombre de un tramo de vía pierde el destino y se queda
 * en la carretera y el rango de kilómetros («Ma-15 · km ~20,1→~32,4»); el resto de nombres no cambia.
 */
internal fun rotuloCompacto(
    nombre: String,
    compacto: Boolean,
): String = if (compacto) nombre.substringBefore(SEPARADOR_DESTINO) else nombre

/**
 * Lo que dice el HUD de lo que falta para acabar el trozo: «Fin de tramo en 800 m» (a pasos de 50 m; desde el
 * kilómetro, en km) y «Meta» a menos de [META_INMINENTE_M].
 */
internal fun textoFinDeTramo(
    recursos: Resources,
    restanteM: Double,
): String =
    if (restanteM < META_INMINENTE_M) {
        recursos.getString(R.string.crono_hud_meta)
    } else {
        val redondeado = Math.round(restanteM / PASO_FIN_TRAMO_HUD_M) * PASO_FIN_TRAMO_HUD_M
        recursos.getString(R.string.crono_hud_fin_tramo, FormatoEs.distancia(redondeado))
    }

private const val OPACIDAD_FIN_TRAMO = 0.85f

/**
 * El rótulo del trozo y, si se sabe, lo que falta para su meta («Fin de tramo en 800 m»): en vertical uno bajo el otro;
 * en apaisado, en la misma fila para no gastar alto.
 */
@Composable
internal fun RotuloYFin(
    rotulo: String?,
    finTramoM: Double?,
    tamano: TextUnit,
    compacto: Boolean,
) {
    val fin = finTramoM?.let { textoFinDeTramo(LocalContext.current.resources, it) }
    val rotuloTexto: @Composable (Modifier) -> Unit = { modificador ->
        Text(
            text = rotulo.orEmpty(),
            style = estiloCifras(tamano),
            color = ColoresGuiado.Laton,
            maxLines = if (compacto) 1 else LINEAS_ROTULO_HUD,
            overflow = TextOverflow.Ellipsis,
            modifier = modificador.testTag(ETIQUETA_CRONO_ROTULO),
        )
    }
    val finTexto: @Composable () -> Unit = {
        Text(
            text = fin.orEmpty(),
            style = estiloCifras(tamano),
            color = ColoresGuiado.Texto.copy(alpha = OPACIDAD_FIN_TRAMO),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(ETIQUETA_CRONO_FIN_TRAMO),
        )
    }
    if (compacto && rotulo != null && fin != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            rotuloTexto(Modifier.weight(1f, fill = false))
            finTexto()
        }
    } else {
        if (rotulo != null) rotuloTexto(Modifier)
        if (fin != null) finTexto()
    }
}
