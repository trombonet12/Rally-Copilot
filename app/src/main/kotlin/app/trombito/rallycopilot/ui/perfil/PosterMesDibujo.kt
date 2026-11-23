package app.trombito.rallycopilot.ui.perfil

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.toArgb
import app.trombito.rallycopilot.ui.comun.colores
import app.trombito.rallycopilot.ui.comun.tipoPlaca
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
import app.trombito.rallycopilot.ui.theme.CarbonOpaco
import app.trombito.rallycopilot.ui.theme.RojoAcento
import app.trombito.rallycopilot.ui.theme.TextoSecundarioSobreCarbon
import app.trombito.rallycopilot.ui.theme.TextoSobreCarbon

// Póster 4:5, el formato que mejor se ve en cualquier chat o red. Medidas en píxeles.
internal const val ANCHO_POSTER = 1080
internal const val ALTO_POSTER = 1350

private const val MARGEN = 64f
private const val ANCHO_UTIL = ANCHO_POSTER - 2 * MARGEN
private const val ANCHO_INFERIOR = 640f
private const val FRANJA = 28f
private const val RADIO = 28f
private const val LADO_MASCOTA = 300

private const val TAM_MARCA = 34f
private const val TAM_TITULO = 60f
private const val TAM_KM = 210f
private const val TAM_ETIQUETA_KM = 44f
private const val TAM_CIFRA = 84f
private const val TAM_ETIQUETA_CIFRA = 30f
private const val TAM_NIVEL = 64f
private const val TAM_PUNTOS = 36f
private const val TAM_HITO = 38f
private const val TAM_PLACA = 40f
private const val TAM_PIE = 30f

private const val Y_MARCA = 110f
private const val Y_TITULO = 186f
private const val Y_KM = 430f
private const val Y_ETIQUETA_KM = 494f
private const val Y_CIFRAS = 650f
private const val SALTO_ETIQUETA_CIFRA = 44f
private const val Y_PANEL = 740f
private const val ALTO_PANEL = 230f
private const val MARGEN_PANEL = 40f
private const val Y_NIVEL_EN_PANEL = 98f
private const val Y_BARRA_EN_PANEL = 128f
private const val Y_PUNTOS_EN_PANEL = 206f
private const val ALTO_BARRA = 22f
private const val Y_HITOS = 1050f
private const val SALTO_HITO = 56f
private const val RADIO_VINETA = 9f
private const val SANGRIA_HITO = 30f
private const val Y_PLACAS = 1190f
private const val ALTO_PLACA = 70f
private const val RELLENO_PLACA = 18f
private const val HUECO_PLACA = 16f
private const val BORDE_PLACA = 4f
private const val BASE_TEXTO_PLACA = 0.72f
private const val Y_PIE = 1306f
private const val ESPACIADO_MARCA = 0.2f
private const val FONDO_PANEL = 0xFF22252A.toInt()
private const val FONDO_BARRA = 0xFF2C2F35.toInt()

private fun pincel(
    color: Int,
    tamano: Float,
    estilo: Int = Typeface.BOLD,
): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = tamano
        typeface = Typeface.create(Typeface.DEFAULT, estilo)
        // Cifras de ancho fijo, como en la interfaz.
        fontFeatureSettings = "tnum"
    }

private fun relleno(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

/** Reduce el texto hasta que cabe en [anchoMax] px. */
private fun Paint.ajustar(
    texto: String,
    anchoMax: Float,
): Paint {
    val ancho = measureText(texto)
    if (ancho > anchoMax && ancho > 0f) textSize *= anchoMax / ancho
    return this
}

/**
 * Dibuja el póster del resumen del mes con [contenido]: carbón oscuro con franja roja de acento, los kilómetros
 * enormes, cuatro cifras, el nivel con su barra, los hitos, las placas de las carreteras estrenadas y Trombito con la
 * bandera ([mascota], opcional). Sin mapa ni ningún dato de ubicación: solo lo que lleva [ContenidoPoster].
 */
internal fun dibujarPoster(
    contenido: ContenidoPoster,
    mascota: Drawable?,
): Bitmap {
    val mapa = Bitmap.createBitmap(ANCHO_POSTER, ALTO_POSTER, Bitmap.Config.ARGB_8888)
    val lienzo = Canvas(mapa)
    lienzo.drawColor(CarbonOpaco.toArgb())
    lienzo.drawRect(0f, 0f, ANCHO_POSTER.toFloat(), FRANJA, relleno(RojoAcento.toArgb()))
    cabecera(lienzo, contenido)
    cifras(lienzo, contenido)
    panelNivel(lienzo, contenido)
    hitos(lienzo, contenido)
    placas(lienzo, contenido)
    lienzo.drawText(contenido.pie, MARGEN, Y_PIE, pincel(TextoSecundarioSobreCarbon.toArgb(), TAM_PIE, Typeface.NORMAL))
    mascota?.let {
        val x = ANCHO_POSTER - MARGEN.toInt() - LADO_MASCOTA
        val y = ALTO_POSTER - MARGEN.toInt() - LADO_MASCOTA
        it.setBounds(x, y, x + LADO_MASCOTA, y + LADO_MASCOTA)
        it.draw(lienzo)
    }
    return mapa
}

private fun cabecera(
    lienzo: Canvas,
    c: ContenidoPoster,
) {
    val marca = pincel(AmbarSobreCarbon.toArgb(), TAM_MARCA).apply { letterSpacing = ESPACIADO_MARCA }
    lienzo.drawText(c.marca, MARGEN, Y_MARCA, marca)
    val titulo = pincel(TextoSobreCarbon.toArgb(), TAM_TITULO).ajustar(c.titulo, ANCHO_UTIL)
    lienzo.drawText(c.titulo, MARGEN, Y_TITULO, titulo)
    val km = pincel(AmbarSobreCarbon.toArgb(), TAM_KM, Typeface.BOLD_ITALIC).ajustar(c.kilometros, ANCHO_UTIL)
    lienzo.drawText(c.kilometros, MARGEN, Y_KM, km)
    val etiqueta = pincel(TextoSecundarioSobreCarbon.toArgb(), TAM_ETIQUETA_KM, Typeface.NORMAL)
    lienzo.drawText(c.etiquetaKilometros, MARGEN, Y_ETIQUETA_KM, etiqueta)
}

private fun cifras(
    lienzo: Canvas,
    c: ContenidoPoster,
) {
    if (c.cifras.isEmpty()) return
    val columna = ANCHO_UTIL / c.cifras.size
    c.cifras.forEachIndexed { i, cifra ->
        val x = MARGEN + i * columna
        val valor = pincel(TextoSobreCarbon.toArgb(), TAM_CIFRA).ajustar(cifra.valor, columna - HUECO_PLACA)
        lienzo.drawText(cifra.valor, x, Y_CIFRAS, valor)
        val etiqueta =
            pincel(TextoSecundarioSobreCarbon.toArgb(), TAM_ETIQUETA_CIFRA, Typeface.NORMAL)
                .ajustar(cifra.etiqueta, columna - HUECO_PLACA)
        lienzo.drawText(cifra.etiqueta, x, Y_CIFRAS + SALTO_ETIQUETA_CIFRA, etiqueta)
    }
}

private fun panelNivel(
    lienzo: Canvas,
    c: ContenidoPoster,
) {
    val izquierda = MARGEN
    val derecha = ANCHO_POSTER - MARGEN
    lienzo.drawRoundRect(RectF(izquierda, Y_PANEL, derecha, Y_PANEL + ALTO_PANEL), RADIO, RADIO, relleno(FONDO_PANEL))
    // Acento rojo a la izquierda, como en las tarjetas de la app.
    val acento = RectF(izquierda, Y_PANEL, izquierda + 2 * BORDE_PLACA, Y_PANEL + ALTO_PANEL)
    lienzo.drawRect(acento, relleno(RojoAcento.toArgb()))
    val x = izquierda + MARGEN_PANEL
    val interior = derecha - izquierda - 2 * MARGEN_PANEL
    lienzo.drawText(
        c.nivel,
        x,
        Y_PANEL + Y_NIVEL_EN_PANEL,
        pincel(TextoSobreCarbon.toArgb(), TAM_NIVEL).ajustar(c.nivel, interior),
    )
    val barra = RectF(x, Y_PANEL + Y_BARRA_EN_PANEL, derecha - MARGEN_PANEL, Y_PANEL + Y_BARRA_EN_PANEL + ALTO_BARRA)
    lienzo.drawRoundRect(barra, ALTO_BARRA / 2, ALTO_BARRA / 2, relleno(FONDO_BARRA))
    val lleno = RectF(barra.left, barra.top, barra.left + barra.width() * c.progreso.coerceIn(0f, 1f), barra.bottom)
    lienzo.drawRoundRect(lleno, ALTO_BARRA / 2, ALTO_BARRA / 2, relleno(AmbarSobreCarbon.toArgb()))
    val puntos = pincel(TextoSecundarioSobreCarbon.toArgb(), TAM_PUNTOS, Typeface.NORMAL).ajustar(c.puntos, interior)
    lienzo.drawText(c.puntos, x, Y_PANEL + Y_PUNTOS_EN_PANEL, puntos)
}

private fun hitos(
    lienzo: Canvas,
    c: ContenidoPoster,
) {
    c.hitos.forEachIndexed { i, titulo ->
        val y = Y_HITOS + i * SALTO_HITO
        lienzo.drawCircle(MARGEN + RADIO_VINETA, y - RADIO_VINETA, RADIO_VINETA, relleno(AmbarSobreCarbon.toArgb()))
        val texto = pincel(TextoSobreCarbon.toArgb(), TAM_HITO, Typeface.NORMAL).ajustar(titulo, ANCHO_INFERIOR)
        lienzo.drawText(titulo, MARGEN + SANGRIA_HITO, y, texto)
    }
}

private fun placas(
    lienzo: Canvas,
    c: ContenidoPoster,
) {
    var x = MARGEN
    for (ref in c.refsCarreteras) {
        val colores = tipoPlaca(ref).colores()
        val texto = pincel(colores.texto.toArgb(), TAM_PLACA, Typeface.BOLD)
        val ancho = texto.measureText(ref) + 2 * RELLENO_PLACA
        if (x + ancho > MARGEN + ANCHO_INFERIOR) break
        val caja = RectF(x, Y_PLACAS, x + ancho, Y_PLACAS + ALTO_PLACA)
        lienzo.drawRoundRect(caja, RADIO / 3, RADIO / 3, relleno(colores.fondo.toArgb()))
        val borde =
            relleno(colores.borde.toArgb()).apply {
                style = Paint.Style.STROKE
                strokeWidth = BORDE_PLACA
            }
        lienzo.drawRoundRect(caja, RADIO / 3, RADIO / 3, borde)
        lienzo.drawText(ref, x + RELLENO_PLACA, Y_PLACAS + ALTO_PLACA * BASE_TEXTO_PLACA, texto)
        x += ancho + HUECO_PLACA
    }
}
