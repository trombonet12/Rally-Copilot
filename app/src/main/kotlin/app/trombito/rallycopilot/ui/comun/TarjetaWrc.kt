package app.trombito.rallycopilot.ui.comun

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
import app.trombito.rallycopilot.ui.theme.CarbonTranslucido
import app.trombito.rallycopilot.ui.theme.DarkColors
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally
import app.trombito.rallycopilot.ui.theme.LocalTemaOscuro
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Radios
import app.trombito.rallycopilot.ui.theme.RallyOscuro
import app.trombito.rallycopilot.ui.theme.RojoAcento
import app.trombito.rallycopilot.ui.theme.TextoSobreCarbon

/**
 * Contenido sobre el carbón: aunque la app esté en tema claro, el carbón es oscuro, así que dentro se usa la paleta
 * oscura (texto claro, ámbar). Lo usan [TarjetaWrc] (variante mapa), [BotonRedondoMapa] y [PildoraVelocidad].
 */
@Composable
internal fun TemaSobreCarbon(contenido: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalColoresRally provides RallyOscuro,
        LocalTemaOscuro provides true,
        LocalContentColor provides TextoSobreCarbon,
    ) {
        MaterialTheme(
            colorScheme = DarkColors,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = contenido,
        )
    }
}

/**
 * Tarjeta del sistema WRC: radio 12, interior 16. [VarianteTarjeta.MAPA] = carbón translúcido (0xE60E0F12);
 * [VarianteTarjeta.OPACA] = `surfaceContainerHigh`. [activa] añade el borde ámbar de 1,5 dp (solo para lo activo);
 * [acentoRojo] pinta una barra roja de 4 dp a la izquierda (acento, nunca relleno); [biselada] corta una esquina en
 * diagonal. [forma] sustituye la forma (p. ej. radio 16 del banner de maniobra) y [relleno] el interior de 16 dp
 * (las tarjetas bajas del carrete de notas). Con [onClick] es pulsable (rol botón).
 */
@Suppress("LongParameterList") // API de componente: cada opción visual es un parámetro con valor por defecto
@Composable
fun TarjetaWrc(
    modifier: Modifier = Modifier,
    variante: VarianteTarjeta = VarianteTarjeta.OPACA,
    activa: Boolean = false,
    acentoRojo: Boolean = false,
    biselada: Boolean = false,
    forma: Shape? = null,
    relleno: Dp = Espacio.l,
    onClick: (() -> Unit)? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val formaTarjeta: Shape = forma ?: if (biselada) FormaBiselada() else Radios.formaTarjeta
    val enMapa = variante == VarianteTarjeta.MAPA
    val fondo = if (enMapa) CarbonTranslucido else MaterialTheme.colorScheme.surfaceContainerHigh
    val colorBorde = if (enMapa) AmbarSobreCarbon else MaterialTheme.colorScheme.primary
    val borde = if (activa) BorderStroke(Medidas.bordeActivo, colorBorde) else null
    val interior: @Composable () -> Unit = {
        val dibujoBarra =
            if (acentoRojo) {
                Modifier.drawBehind {
                    drawRect(RojoAcento, Offset.Zero, Size(Medidas.barraAcento.toPx(), size.height))
                }
            } else {
                Modifier
            }
        val margenIzquierdo = if (acentoRojo) relleno + Medidas.barraAcento else relleno
        Box(Modifier.fillMaxWidth().then(dibujoBarra)) {
            Column(
                Modifier.fillMaxWidth().padding(
                    start = margenIzquierdo,
                    top = relleno,
                    end = relleno,
                    bottom = relleno,
                ),
                content = contenido,
            )
        }
    }
    val contenidoTarjeta: @Composable () -> Unit = {
        val color = if (enMapa) TextoSobreCarbon else MaterialTheme.colorScheme.onSurface
        CompositionLocalProvider(LocalContentColor provides color) { interior() }
    }
    val envuelto: @Composable () -> Unit = if (enMapa) ({ TemaSobreCarbon(contenidoTarjeta) }) else contenidoTarjeta
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = formaTarjeta,
            color = fondo,
            border = borde,
        ) { envuelto() }
    } else {
        Surface(modifier = modifier, shape = formaTarjeta, color = fondo, border = borde) { envuelto() }
    }
}
