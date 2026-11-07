package app.trombito.rallycopilot.motor.importar

import app.trombito.rallycopilot.motor.edicion.CodecEdiciones
import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

/**
 * Escribe un [TramoArchivo] como GPX 1.1. Lo entiende cualquier app: la traza va en un `trk` (con `ele` si la hay) y
 * las notas y avisos de la hoja, como `wpt` con la frase en `name` y `type` «nota» o «aviso». Lo propio de Rally
 * Copilot (sectores, microsectores y las ediciones de Joan) va en `extensions` del `gpx`, dentro de `rc:tramoRally`
 * (espacio de nombres [ESPACIO_RC]); [LectorGpx] lo recupera en [ResultadoImportacion.Ok.extension]:
 *
 * ```
 * <extensions><rc:tramoRally>
 *   <rc:sectorRally m="700.0"/> <rc:microsectorRally m="1000.0"/>
 *   <rc:edicionRally m="120.5" t="1000" tipo="SUSTITUIR" nota="IZQUIERDA;2;LARGA;100;1;40.0"/>
 * </rc:tramoRally></extensions>
 * ```
 *
 * Los decimales se escriben en plano (sin exponente) y sin perder precisión, así que exportar e importar devuelve la
 * misma traza. Los textos se escapan y se les quitan los caracteres que XML 1.0 no admite.
 */
object EscritorGpx {
    /** Espacio de nombres de la extensión propia de Rally Copilot. */
    const val ESPACIO_RC = "urn:rallycopilot:gpx:1"

    private const val MAX_NOMBRE_ARCHIVO = 60

    /** El documento GPX del [tramo]. */
    fun escribir(tramo: TramoArchivo): String {
        val s = StringBuilder(PREVISION_BASE + tramo.traza.size * PREVISION_PUNTO)
        s.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        s.append("<gpx version=\"1.1\" creator=\"Rally Copilot\" xmlns=\"http://www.topografix.com/GPX/1/1\" ")
        s.append("xmlns:rc=\"").append(ESPACIO_RC).append("\">\n")
        s.append("  <metadata><name>").append(texto(tramo.nombre)).append("</name></metadata>\n")
        for (n in tramo.notas) {
            s
                .append("  <wpt lat=\"")
                .append(decimal(n.punto.lat))
                .append("\" lon=\"")
                .append(decimal(n.punto.lon))
            s.append("\"><name>").append(texto(n.frase)).append("</name>")
            s.append("<cmt>").append(metros(n.posicionM)).append(" m desde la salida</cmt>")
            s.append("<type>").append(if (n.esAviso) "aviso" else "nota").append("</type></wpt>\n")
        }
        s.append("  <trk><name>").append(texto(tramo.nombre)).append("</name><trkseg>\n")
        for (p in tramo.traza) {
            s
                .append(
                    "    <trkpt lat=\"",
                ).append(decimal(p.lat))
                .append("\" lon=\"")
                .append(decimal(p.lon))
                .append("\">")
            p.alt?.let { s.append("<ele>").append(decimal(it)).append("</ele>") }
            s.append("</trkpt>\n")
        }
        s.append("  </trkseg></trk>\n")
        extension(s, tramo)
        s.append("</gpx>\n")
        return s.toString()
    }

    /**
     * Nombre de archivo para [nombre]: minúsculas, sin acentos ni símbolos, con guiones y acabado en `.gpx`
     * («Palma → Valldemossa» da `palma-valldemossa.gpx`); `tramo.gpx` si no queda nada.
     */
    fun nombreArchivo(nombre: String): String {
        val plano =
            Normalizer
                .normalize(nombre, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .take(MAX_NOMBRE_ARCHIVO)
                .trim('-')
        return "${plano.ifEmpty { "tramo" }}.gpx"
    }

    private fun extension(
        s: StringBuilder,
        tramo: TramoArchivo,
    ) {
        s.append("  <extensions><rc:tramoRally>\n")
        for (m in tramo.sectores) s.append("    <rc:sectorRally m=\"").append(decimal(m)).append("\"/>\n")
        for (m in tramo.microsectores) s.append("    <rc:microsectorRally m=\"").append(decimal(m)).append("\"/>\n")
        for (e in tramo.ediciones) {
            s.append("    <rc:edicionRally m=\"").append(decimal(e.posicionM))
            s.append("\" t=\"").append(e.marcaTiempoMs)
            s.append("\" tipo=\"").append(CodecEdiciones.tipo(e.accion)).append('"')
            CodecEdiciones.nota(e.accion)?.let { s.append(" nota=\"").append(texto(it)).append('"') }
            s.append("/>\n")
        }
        s.append("  </rc:tramoRally></extensions>\n")
    }

    /** Un decimal en notación plana y sin pérdida (`Double.toString` es de ida y vuelta, pero usa exponente). */
    private fun decimal(valor: Double): String = BigDecimal(valor.toString()).toPlainString()

    private fun metros(valor: Double): String = valor.toLong().toString()

    /** Escapa el texto y descarta lo que XML 1.0 no puede llevar (controles, sustitutos sueltos, U+FFFE/U+FFFF). */
    private fun texto(original: String): String {
        val s = StringBuilder(original.length)
        var i = 0
        while (i < original.length) {
            val c = original.codePointAt(i)
            i += Character.charCount(c)
            when {
                c == '&'.code -> s.append("&amp;")
                c == '<'.code -> s.append("&lt;")
                c == '>'.code -> s.append("&gt;")
                c == '"'.code -> s.append("&quot;")
                c == '\''.code -> s.append("&apos;")
                esValido(c) -> s.appendCodePoint(c)
            }
        }
        return s.toString()
    }

    private fun esValido(c: Int): Boolean =
        c in CONTROLES_PERMITIDOS ||
            c in ESPACIO..FIN_BMP_BAJO ||
            c in INICIO_BMP_ALTO..FIN_BMP_ALTO ||
            c in INICIO_SUPLEMENTARIOS..FIN_SUPLEMENTARIOS

    private const val PREVISION_BASE = 1024

    // Caracteres permitidos por XML 1.0 (https://www.w3.org/TR/xml/#charsets).
    private val CONTROLES_PERMITIDOS = intArrayOf(0x9, 0xA, 0xD) // tabulador, salto de línea y retorno
    private const val ESPACIO = 0x20
    private const val FIN_BMP_BAJO = 0xD7FF
    private const val INICIO_BMP_ALTO = 0xE000
    private const val FIN_BMP_ALTO = 0xFFFD
    private const val INICIO_SUPLEMENTARIOS = 0x10000
    private const val FIN_SUPLEMENTARIOS = 0x10FFFF
    private const val PREVISION_PUNTO = 64
}
