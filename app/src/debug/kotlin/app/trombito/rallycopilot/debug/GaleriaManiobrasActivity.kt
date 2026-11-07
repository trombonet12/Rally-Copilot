package app.trombito.rallycopilot.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.EstadoRuta
import app.trombito.rallycopilot.motor.ruta.Maniobra
import app.trombito.rallycopilot.motor.ruta.Ruta
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import app.trombito.rallycopilot.ui.maniobras.BannerManiobra
import app.trombito.rallycopilot.ui.maniobras.IconoManiobra
import app.trombito.rallycopilot.ui.maniobras.VarianteManiobra
import app.trombito.rallycopilot.ui.navegacion.guiado.PanelGuiado
import app.trombito.rallycopilot.ui.navegacion.guiado.TarjetaLlegada
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import kotlin.random.Random

/**
 * Solo debug. Galería para comparar las tres variantes de iconos y banners de maniobra a tamaño real:
 * `adb shell am start -n app.trombito.rallycopilot.dev/app.trombito.rallycopilot.debug.GaleriaManiobrasActivity`
 */
class GaleriaManiobrasActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RallyCopilotTheme { Galeria() } }
    }
}

// ROTONDA_SALIDA comparte dibujo con ROTONDA_ENTRADA: no se repite.
private val TIPOS = TipoManiobra.entries.filter { it != TipoManiobra.ROTONDA_SALIDA }

private val CARBON = Color(0xFF0E0F12)

@Composable
private fun Galeria() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CARBON)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Maniobras · 72 dp · sobre carbón", color = Color.White)
        VarianteManiobra.entries.forEach { variante ->
            Text(variante.etiqueta, color = Color(0xFFE5AB46))
            Rejilla(variante)
        }
        Text("Sobre satélite (simulado)", color = Color.White)
        VarianteManiobra.entries.forEach { variante ->
            FondoSatelite {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(variante.etiqueta, color = Color.White)
                    Rejilla(variante)
                }
            }
        }
        Text("Banners", color = Color.White)
        FondoSatelite {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BannerEjemplo()
            }
        }
        Text("Guiado · vertical", color = Color.White)
        FondoSatelite {
            PanelGuiado(
                estado = EJEMPLO_GUIANDO,
                modifier = Modifier.fillMaxWidth().height(380.dp),
            )
        }
        Text("Guiado · horizontal, recalculando y llegada", color = Color.White)
        FondoSatelite {
            PanelGuiado(
                estado = EJEMPLO_GUIANDO.copy(recalculando = true),
                modifier = Modifier.width(640.dp).height(300.dp),
            )
        }
        FondoSatelite { TarjetaLlegada(destino = EJEMPLO_GUIANDO.destino, onCerrar = {}) }
    }
}

@Composable
private fun Rejilla(variante: VarianteManiobra) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TIPOS.forEach { tipo ->
            IconoManiobra(
                tipo = tipo,
                variante = variante,
                salidaRotonda = if (tipo == TipoManiobra.ROTONDA_ENTRADA) 2 else null,
            )
        }
    }
}

@Composable
private fun BannerEjemplo() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BannerManiobra(
            tipo = TipoManiobra.IZQUIERDA,
            distanciaM = 352.0,
            frase = "Izquierda cerrada, Ma-10",
            siguiente = TipoManiobra.DERECHA to "derecha",
            modifier = Modifier.fillMaxWidth(),
        )
        BannerManiobra(
            tipo = TipoManiobra.ROTONDA_ENTRADA,
            distanciaM = 1234.0,
            frase = "Rotonda, segunda salida",
            siguiente = TipoManiobra.RECTO to "recto",
            modifier = Modifier.fillMaxWidth(),
            salidaRotonda = 2,
        )
        BannerManiobra(
            tipo = TipoManiobra.SALIDA_AUTOVIA_DERECHA,
            distanciaM = 8900.0,
            frase = "salida",
            siguiente = TipoManiobra.ROTONDA_ENTRADA to "rotonda, segunda salida",
            modifier = Modifier.fillMaxWidth(),
            via = "Ma-13",
            siguienteSalida = 2,
        )
        BannerManiobra(
            tipo = TipoManiobra.RECTO,
            distanciaM = 300.0,
            frase = "recto",
            siguiente = TipoManiobra.ROTONDA_ENTRADA to "rotonda, primera salida, Carrer de Aragó",
            modifier = Modifier.fillMaxWidth(),
            siguienteSalida = 1,
            siguienteVia = "Carrer de Aragó",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..4).forEach { salida ->
                IconoManiobra(TipoManiobra.ROTONDA_ENTRADA, VarianteManiobra.CHEVRON_HUD, salidaRotonda = salida)
            }
        }
    }
}

/** Foto-textura clara que imita un satélite: degradado verde a ocre con manchas de cultivo deterministas. */
@Composable
private fun FondoSatelite(contenido: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(
                Brush.linearGradient(
                    listOf(Color(0xFF7E9B52), Color(0xFFB7B26A), Color(0xFFC9A66B)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
            val azar = Random(7)
            repeat(70) {
                val centro = Offset(azar.nextFloat() * size.width, azar.nextFloat() * size.height)
                val tono = Color(0xFF2F4A1E).copy(alpha = 0.04f + azar.nextFloat() * 0.10f)
                val ancho = 40f + azar.nextFloat() * 220f
                drawRect(tono, topLeft = centro, size = Size(ancho, 20f + azar.nextFloat() * 90f))
            }
        }
        Box(modifier = Modifier.padding(8.dp)) { contenido() }
    }
}

private fun maniobraEjemplo(
    tipo: TipoManiobra,
    vias: List<String> = emptyList(),
    salidaRotonda: Int? = null,
) = Maniobra(tipo, "", vias, 0, 1, 500.0, 40.0, salidaRotonda)

private val IZQUIERDA_MA10 = maniobraEjemplo(TipoManiobra.IZQUIERDA, vias = listOf("Ma-10"))
private val ROTONDA_SEGUNDA = maniobraEjemplo(TipoManiobra.ROTONDA_ENTRADA, salidaRotonda = 2)

private val EJEMPLO_GUIANDO =
    EstadoNavegacion.Guiando(
        destino = Destino("Cala Figuera", PuntoGeo(39.33, 3.15)),
        perfil = PerfilRuta.SINUOSA,
        ruta =
            Ruta(
                puntos = listOf(PuntoGeo(39.5, 2.9), PuntoGeo(39.33, 3.15)),
                maniobras = listOf(IZQUIERDA_MA10, ROTONDA_SEGUNDA),
                longitudM = 12_400.0,
                tiempoS = 3900.0,
            ),
        progreso =
            EstadoRuta(
                progresoM = 100.0,
                distanciaRestanteM = 12_400.0,
                tiempoRestanteS = 3900.0,
                llegadaMs = System.currentTimeMillis() + 3_900_000L,
                maniobraActual = IZQUIERDA_MA10,
                distanciaAManiobraM = 352.0,
                maniobraSiguiente = ROTONDA_SEGUNDA,
                distanciaALaRutaM = 4.0,
                llegado = false,
            ),
        recalculando = false,
    )
