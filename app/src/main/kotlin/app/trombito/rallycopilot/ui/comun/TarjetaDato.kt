package app.trombito.rallycopilot.ui.comun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.ui.theme.CifraPequena
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono

private val LADO_ICONO_DATO = 16.dp

/**
 * Tarjeta de dato del sistema WRC: icono pequeño y [etiqueta] arriba; debajo la [cifra] en cursiva de ancho fijo
 * ([estiloCifra]) con su [unidad]. Con [biselada] se corta una esquina en diagonal (solo para lo protagonista). Es un
 * solo nodo accesible que se lee «etiqueta, cifra unidad».
 */
@Suppress("LongParameterList") // API de componente: cada opción visual es un parámetro con valor por defecto
@Composable
fun TarjetaDato(
    cifra: String,
    etiqueta: String,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
    unidad: String? = null,
    estiloCifra: TextStyle = CifraPequena,
    biselada: Boolean = false,
    variante: VarianteTarjeta = VarianteTarjeta.OPACA,
) {
    TarjetaWrc(modifier = modifier, variante = variante, biselada = biselada, relleno = Espacio.m) {
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            CabeceraDato(icono, etiqueta)
            Row(
                horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(text = cifra, style = estiloCifra, color = MaterialTheme.colorScheme.onSurface)
                if (unidad != null) {
                    Text(
                        text = unidad,
                        style = EtiquetaIcono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Espacio.xs),
                    )
                }
            }
        }
    }
}

/**
 * Variante con frase de [TarjetaDato] (el tablero del piloto): en lugar de una cifra, una [frase] corta de cuerpo
 * («Ritmo estable en las curvas lentas»), con el mismo icono y la misma etiqueta encima.
 */
@Suppress("LongParameterList") // API de componente: cada opción visual es un parámetro con valor por defecto
@Composable
fun TarjetaDatoFrase(
    frase: String,
    etiqueta: String,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
    biselada: Boolean = false,
    variante: VarianteTarjeta = VarianteTarjeta.OPACA,
) {
    TarjetaWrc(modifier = modifier, variante = variante, biselada = biselada, relleno = Espacio.m) {
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            CabeceraDato(icono, etiqueta)
            Text(text = frase, style = Cuerpo, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun CabeceraDato(
    icono: ImageVector?,
    etiqueta: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs), verticalAlignment = Alignment.CenterVertically) {
        if (icono != null) {
            Icon(
                imageVector = icono,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(LADO_ICONO_DATO),
            )
        }
        Text(text = etiqueta, style = EtiquetaIcono, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
