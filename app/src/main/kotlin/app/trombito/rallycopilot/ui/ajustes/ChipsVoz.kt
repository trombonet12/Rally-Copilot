package app.trombito.rallycopilot.ui.ajustes

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.edicion.FrasesOrdenes
import app.trombito.rallycopilot.motor.edicion.Indicacion
import app.trombito.rallycopilot.motor.edicion.Orden
import app.trombito.rallycopilot.motor.edicion.TipoPregunta
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.Iconos

/** El icono de una frase: un vector de [Iconos] o, si [vector] es nulo, un dibujo de roadbook. */
internal class IconoVoz(
    val vector: ImageVector? = null,
    @DrawableRes val dibujo: Int = 0,
)

/** Una frase de la chuleta con su icono; [conSiguiente] la deja en la misma fila que la frase contraria (sin/con). */
internal class ChipVoz(
    val icono: IconoVoz,
    val texto: String,
    val conSiguiente: Boolean = false,
)

/** Reparte los chips en filas de dos columnas; una pareja sin/con ocupa siempre una fila entera. */
internal fun filasDeChips(chips: List<ChipVoz>): List<List<ChipVoz>> {
    val filas = mutableListOf<List<ChipVoz>>()
    var suelta: ChipVoz? = null
    var i = 0
    while (i < chips.size) {
        val chip = chips[i]
        val siguiente = chips.getOrNull(i + 1)
        if (chip.conSiguiente && siguiente != null) {
            suelta?.let { filas += listOf(it) }
            suelta = null
            filas += listOf(chip, siguiente)
            i += 2
        } else {
            val anterior = suelta
            if (anterior == null) {
                suelta = chip
            } else {
                filas += listOf(anterior, chip)
                suelta = null
            }
            i++
        }
    }
    suelta?.let { filas += listOf(it) }
    return filas
}

/** ¿[a] y [b] son las dos caras de un mismo ajuste («sin cruces» / «con cruces», «satélite» / «mapa»…)? */
private fun sonPareja(
    a: Orden?,
    b: Orden?,
): Boolean =
    a != null && b != null && a::class == b::class && a::class in CON_CONTRARIA &&
        (a !is Orden.Avisar || a.indicacion == (b as Orden.Avisar).indicacion)

private val CON_CONTRARIA =
    setOf(
        Orden.Avisar::class,
        Orden.Volumen::class,
        Orden.NotasSimplificadas::class,
        Orden.Satelite::class,
        Orden.NorteArriba::class,
        Orden.Zoom::class,
        Orden.Confirma::class,
    )

private val ORDEN_POR_FRASE: Map<String, Orden> =
    FrasesOrdenes.porOrden.associate { (orden, frases) -> frases.first() to orden }

/**
 * Los chips de [grupo]: cada frase de [ChuletaVoz] con el icono de lo que hace. Las frases de `FrasesOrdenes` se
 * reconocen por su orden; las de lugar y las sueltas (control y edición), por su texto.
 */
internal fun chipsDeGrupo(
    grupo: GrupoVoz,
    sueltas: List<String>,
    lugarIr: String,
    lugarParada: String,
): List<ChipVoz> {
    val frases = ChuletaVoz.frases(grupo, sueltas, lugarIr, lugarParada)
    val ordenes = frases.map { (frase, _) -> ORDEN_POR_FRASE[frase] }
    return frases.mapIndexed { i, (frase, pregunta) ->
        val orden = ordenes[i]
        ChipVoz(
            icono = iconoDe(orden, frase, grupo),
            texto = ChuletaVoz.formato(frase, pregunta),
            conSiguiente = orden != null && sonPareja(orden, ordenes.getOrNull(i + 1)),
        )
    }
}

private fun iconoDe(
    orden: Orden?,
    frase: String,
    grupo: GrupoVoz,
): IconoVoz =
    when (orden) {
        null -> iconoSuelto(frase, grupo)
        is Orden.Pregunta -> IconoVoz(iconoPregunta(orden.tipo))
        else -> iconoOrden(orden)
    }

private fun iconoPregunta(tipo: TipoPregunta): ImageVector =
    when (tipo) {
        TipoPregunta.CUANTO_FALTA -> Iconos.Bandera
        TipoPregunta.HORA_LLEGADA -> Iconos.Cronometro
        TipoPregunta.LIMITE -> Iconos.Velocimetro
        TipoPregunta.DONDE_ESTOY -> Iconos.MiUbicacion
        TipoPregunta.COMO_VOY -> Iconos.Grafico
    }

private fun iconoSuelto(
    frase: String,
    grupo: GrupoVoz,
): IconoVoz =
    when {
        frase.startsWith(FrasesOrdenes.PREFIJO_PARADA) -> IconoVoz(Iconos.AnadirLugar)
        grupo == GrupoVoz.NAVEGACION -> IconoVoz(Iconos.Direcciones)
        grupo == GrupoVoz.CONTROL -> IconoVoz(Iconos.Deslizadores)
        frase.startsWith("quita", ignoreCase = true) -> IconoVoz(Iconos.Borrar)
        frase.startsWith("repite", ignoreCase = true) -> IconoVoz(Iconos.Repetir)
        frase.startsWith("marca", ignoreCase = true) -> IconoVoz(Iconos.Lugar)
        frase.contains("horquilla", ignoreCase = true) -> IconoVoz(dibujo = R.drawable.ic_nota_horquilla_izq)
        frase.startsWith("cuidado", ignoreCase = true) -> IconoVoz(dibujo = R.drawable.ic_nota_cuidado)
        else -> IconoVoz(Iconos.Notas)
    }

@Suppress("CyclomaticComplexMethod") // una rama por tipo de orden: es una tabla de iconos
private fun iconoOrden(orden: Orden): IconoVoz =
    when (orden) {
        is Orden.Modo -> IconoVoz(if (orden.modo == ModoCopiloto.NOTAS) Iconos.Notas else Iconos.Auricular)
        Orden.Silencio -> IconoVoz(Iconos.Pausa)
        is Orden.Volumen -> IconoVoz(Iconos.Volumen)
        is Orden.NotasSimplificadas -> IconoVoz(if (orden.activar) Iconos.CurvasSuaves else Iconos.Curvas)
        is Orden.Avisar -> IconoVoz(dibujo = dibujoDeIndicacion(orden.indicacion))
        is Orden.Satelite -> IconoVoz(if (orden.activar) Iconos.Satelite else Iconos.Mapa)
        is Orden.NorteArriba -> IconoVoz(if (orden.activar) Iconos.Brujula else Iconos.Rumbo)
        is Orden.Zoom -> IconoVoz(if (orden.acercar) Iconos.Mas else Iconos.Menos)
        Orden.Salida -> IconoVoz(Iconos.Semaforo)
        Orden.ParaCrono -> IconoVoz(Iconos.Cronometro)
        Orden.RadarAqui -> IconoVoz(dibujo = R.drawable.ic_nota_cuidado)
        Orden.TripACero -> IconoVoz(Iconos.Repetir)
        Orden.NotaMal -> IconoVoz(Iconos.Deshacer)
        Orden.VolverACasa -> IconoVoz(Iconos.Casa)
        Orden.CancelarRuta -> IconoVoz(Iconos.Cerrar)
        Orden.Otro -> IconoVoz(Iconos.Siguiente)
        is Orden.MasCercano -> IconoVoz(Iconos.Lugar)
        is Orden.Confirma -> IconoVoz(if (orden.si) Iconos.Hecho else Iconos.Cerrar)
        Orden.Ayuda -> IconoVoz(Iconos.Ayuda)
        else -> IconoVoz(Iconos.Microfono)
    }

@DrawableRes
private fun dibujoDeIndicacion(indicacion: Indicacion): Int =
    when (indicacion) {
        Indicacion.CRUCES -> R.drawable.ic_nota_cruce
        Indicacion.PUENTES -> R.drawable.ic_nota_puente
        Indicacion.ROTONDAS -> R.drawable.ic_nota_rotonda
        Indicacion.SALIDAS -> R.drawable.ic_nota_salida_autopista
    }
