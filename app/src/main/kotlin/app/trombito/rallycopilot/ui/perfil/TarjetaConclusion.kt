package app.trombito.rallycopilot.ui.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.Conclusion
import app.trombito.rallycopilot.motor.analisis.TipoConclusion
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.CifraPequena
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono

const val ETIQUETA_PERFIL_CONCLUSION_PREFIJO = "perfil-conclusion-"

private val LADO_ICONO_ETIQUETA = 16.dp

/** Cifra de una conclusión: la de las tarjetas de dato, a 30 sp y en una o dos líneas (un nombre de calle largo). */
private val CifraConclusion = CifraPequena.copy(fontSize = 30.sp, lineHeight = 34.sp)

/** Una conclusión: etiqueta con icono, la cifra grande y la frase completa debajo. Es un solo nodo accesible. */
@Composable
internal fun TarjetaConclusion(conclusion: Conclusion) {
    TarjetaWrc(
        modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_PERFIL_CONCLUSION_PREFIJO + conclusion.tipo.name),
        biselada = true,
        relleno = Espacio.m,
    ) {
        Column(
            Modifier.semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            EtiquetaConIcono(iconoDe(conclusion.tipo), stringResource(etiquetaDe(conclusion.tipo)))
            Text(
                text = conclusion.cifra,
                style = CifraConclusion,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = conclusion.frase,
                style = Cuerpo,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun EtiquetaConIcono(
    icono: ImageVector,
    texto: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icono,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(LADO_ICONO_ETIQUETA),
        )
        Text(text = texto, style = EtiquetaIcono, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun iconoDe(tipo: TipoConclusion): ImageVector =
    when (tipo) {
        TipoConclusion.RITMO -> Iconos.Velocimetro
        TipoConclusion.ULTIMO_VIAJE -> Iconos.Bandera
        TipoConclusion.CARRETERA_FAVORITA -> Iconos.Direcciones
        TipoConclusion.CARRETERA_REVIRADA -> Iconos.Curvas
        TipoConclusion.CURVA_TIPO -> Iconos.CurvasSuaves
        TipoConclusion.KM_ESTRENADOS -> Iconos.Brujula
        TipoConclusion.RECORDS -> Iconos.Estrella
        TipoConclusion.VELOCIDAD_POR_GRADO -> Iconos.Rayo
    }

private fun etiquetaDe(tipo: TipoConclusion): Int =
    when (tipo) {
        TipoConclusion.RITMO -> R.string.perfil_conclusion_ritmo
        TipoConclusion.ULTIMO_VIAJE -> R.string.perfil_conclusion_ultimo_viaje
        TipoConclusion.CARRETERA_FAVORITA -> R.string.perfil_conclusion_carretera_favorita
        TipoConclusion.CARRETERA_REVIRADA -> R.string.perfil_conclusion_carretera_revirada
        TipoConclusion.CURVA_TIPO -> R.string.perfil_conclusion_curva_tipo
        TipoConclusion.KM_ESTRENADOS -> R.string.perfil_conclusion_km_estrenados
        TipoConclusion.RECORDS -> R.string.perfil_conclusion_records
        TipoConclusion.VELOCIDAD_POR_GRADO -> R.string.perfil_conclusion_velocidad_por_grado
    }
