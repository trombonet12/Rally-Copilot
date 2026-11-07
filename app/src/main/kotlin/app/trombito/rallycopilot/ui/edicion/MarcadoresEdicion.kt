package app.trombito.rallycopilot.ui.edicion

import androidx.compose.ui.geometry.Offset
import app.trombito.rallycopilot.domain.edicion.TramoEditable
import app.trombito.rallycopilot.motor.edicion.Marca
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.TrazaMetros
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.Nota

/** Qué es un marcador de la edición del tramo; cada tipo tiene su color en el mapa y en la leyenda. */
enum class TipoMarcador { NOTA, CUIDADO, MARCA, RADAR, CORTE }

/**
 * Un marcador sobre el mapa de edición: [id] estable, [posicionM] (metros desde la salida, a mitad de la curva en las
 * notas), el [texto] corto de la píldora («D4», «IH», «M», «C1») y dónde cae ([punto]).
 */
data class MarcadorEdicion(
    val id: String,
    val tipo: TipoMarcador,
    val posicionM: Double,
    val texto: String,
    val punto: PuntoGeo,
)

/** Texto corto de una nota: lado y grado («D4», «I2»), con «H» en las horquillas («DH»). */
fun textoCortoNota(nota: Nota): String {
    val lado = if (nota.direccion == Direccion.IZQUIERDA) "I" else "D"
    return lado + (nota.grado?.toString() ?: "H")
}

/** Identificador del marcador de una nota: su inicio en metros, que es lo que la identifica en el tramo. */
fun idMarcadorNota(nota: Nota): String = "n-${nota.posicionM}"

/** Los marcadores del tramo: notas, marcas «marca aquí» y radares con posición y cortes de sector (C1, C2…). */
fun marcadoresDe(
    datos: TramoEditable,
    sectoresM: List<Double>,
    traza: TrazaMetros,
): List<MarcadorEdicion> {
    val notas =
        datos.hoja.notas.map { n ->
            val medio = (n.posicionM + n.finM) / 2.0
            MarcadorEdicion(
                id = idMarcadorNota(n),
                tipo = if (n.cuidado) TipoMarcador.CUIDADO else TipoMarcador.NOTA,
                posicionM = medio,
                texto = textoCortoNota(n),
                punto = traza.puntoEn(medio),
            )
        }
    val marcas =
        datos.marcas.mapIndexed { i, m ->
            val radar = m.marca.etiqueta == Marca.ETIQUETA_RADAR
            val tipo = if (radar) TipoMarcador.RADAR else TipoMarcador.MARCA
            MarcadorEdicion("m-$i", tipo, m.marca.posicionM, if (radar) "R" else "M", traza.puntoEn(m.marca.posicionM))
        }
    val cortes =
        sectoresM.mapIndexed { i, m -> MarcadorEdicion("c-$i", TipoMarcador.CORTE, m, "C${i + 1}", traza.puntoEn(m)) }
    return notas + marcas + cortes
}

/** El marcador cuyo centro en pantalla cae a menos de [radioPx] de [toque]; el más cercano si hay varios. */
fun marcadorCercano(
    marcadores: List<Pair<MarcadorEdicion, Offset>>,
    toque: Offset,
    radioPx: Float,
): MarcadorEdicion? =
    marcadores
        .map { it.first to (it.second - toque).getDistance() }
        .filter { it.second <= radioPx }
        .minByOrNull { it.second }
        ?.first
