package app.trombito.rallycopilot.motor.geo

import java.net.URLDecoder

/** Grupo de la expresión de consulta con el nombre entre paréntesis. */
private const val GRUPO_NOMBRE = 3

/** Grupos de la expresión sexagesimal: primer grupo de grados según el eje vaya antes o después, y el eje de detrás. */
private const val GRUPO_GRADOS_DETRAS_DEL_EJE = 2
private const val GRUPO_GRADOS_ANTES_DEL_EJE = 5
private const val GRUPO_EJE_DETRAS = 8

/** Resultado de interpretar un texto pegado o compartido como ubicación. */
sealed interface ResultadoEnlace {
    /** Ubicación resuelta sin red; `nombre` solo si el enlace lo trae. */
    data class Punto(
        val punto: PuntoGeo,
        val nombre: String?,
    ) : ResultadoEnlace

    /** Enlace corto: hay que seguir la redirección (con red) para conocer la ubicación. */
    data class NecesitaRed(
        val url: String,
    ) : ResultadoEnlace

    data object NoReconocido : ResultadoEnlace
}

/**
 * Interpreta texto de ubicación: coordenadas decimales o sexagesimales, Plus Codes, `geo:`, y enlaces de
 * OpenStreetMap y Google Maps (con o sin `https://`). Sin red: los enlaces cortos de Google/OSM devuelven
 * [ResultadoEnlace.NecesitaRed]. Acepta cualquier punto del mundo (la app avisa si cae fuera de Mallorca) y rechaza
 * coordenadas fuera de rango.
 *
 * Decisiones:
 * - Antes de interpretar se normaliza el texto pegado: espacios Unicode (NBSP…) a espacio, marcas invisibles
 *   (LRM, RLM, ZWSP, BOM) fuera y puntuación sobrante en los bordes fuera.
 * - Coordenadas decimales en texto libre: con punto decimal en ambas («39.83, 2.81»; separador coma, punto y coma o
 *   espacio) o, como las copia Google Maps en español, con coma decimal en ambas y separada por «, », «; » o
 *   espacios («39,5789682, 2,6515765»). «39,5,2,6» (sin espacio) es ambiguo y no se reconoce.
 * - Si el texto contiene una URL (típico de «Compartir» de Google Maps: «Nombre https://…») se usa la URL; también
 *   las que no llevan esquema si el dominio es de Google Maps, goo.gl u OpenStreetMap.
 * - Plus Codes completos y cortos («JHX2+XX Palma»); los cortos se recuperan con referencia en el centro de Mallorca.
 * - En Google Maps manda `!3d..!4d..` (el sitio) sobre `@lat,lon` (el centro de la vista).
 */
object EnlaceUbicacion {
    private val URL_EN_TEXTO = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val URL_SIN_ESQUEMA =
        Regex("""(?<![\w@./:-])((?:[a-z0-9-]+\.)+[a-z]{2,}(?:[/?#]\S*)?)""", RegexOption.IGNORE_CASE)
    private val CENTRO_MALLORCA = PuntoGeo(39.6, 2.9)

    fun leer(texto: String): ResultadoEnlace {
        val t = Normalizador.limpiar(texto)
        val url = enlaceEn(t)
        return when {
            t.startsWith("geo:", ignoreCase = true) -> {
                LectorGeo.leer(t)
            }

            url != null -> {
                LectorUrl.leer(url)
            }

            else -> {
                PlusCode.leer(t, CENTRO_MALLORCA)?.let { ResultadoEnlace.Punto(it, null) }
                    ?: Coord.resultado(Coord.deTexto(t)?.let { Marcador(it, null) })
            }
        }
    }

    /** Primera URL del texto: con esquema, o sin él si el dominio es de mapas (se le añade `https://`). */
    private fun enlaceEn(texto: String): String? =
        URL_EN_TEXTO.find(texto)?.value?.let { Normalizador.quitarFinal(it) }
            ?: URL_SIN_ESQUEMA
                .findAll(texto)
                .map { Normalizador.quitarFinal(it.value) }
                .firstOrNull { LectorUrl.esDominioDeMapas(PartesUrl("https://$it").host) }
                ?.let { "https://$it" }
}

private object Normalizador {
    private val ESPACIOS = Regex("""[\p{Zs}\u2028\u2029\t]""")
    private val INVISIBLES = Regex("""[\u00AD\u200B-\u200F\u202A-\u202E\u2060\u2066-\u2069\uFEFF]""")
    private const val BORDES = ".,;:!?()[]{}<>\"'«»“”‘’„"
    private const val CIERRES = ")]}"
    private const val APERTURAS = "([{"

    fun limpiar(texto: String): String =
        quitarFinal(
            texto
                .replace(INVISIBLES, "")
                .replace(ESPACIOS, " ")
                .replace('\u2212', '-')
                .trimStart { it.isWhitespace() || it in BORDES },
        )

    /** Quita espacios y puntuación del final; un `)`, `]` o `}` solo si no cierra un paréntesis del propio texto. */
    fun quitarFinal(texto: String): String {
        var fin = texto.length
        while (fin > 0 && sobra(texto, fin)) fin--
        return texto.substring(0, fin)
    }

    private fun sobra(
        texto: String,
        fin: Int,
    ): Boolean {
        val c = texto[fin - 1]
        val cierre = CIERRES.indexOf(c)
        return when {
            c.isWhitespace() -> true
            cierre >= 0 -> texto.take(fin).count { it == c } > texto.take(fin).count { it == APERTURAS[cierre] }
            else -> c in BORDES
        }
    }
}

private data class Marcador(
    val punto: PuntoGeo,
    val nombre: String?,
)

private object Coord {
    private const val NUM = """-?\d+(?:\.\d+)?"""
    private val PAR_DECIMAL = Regex("""^(-?\d+\.\d+)\s*(?:,|;|\s)\s*(-?\d+\.\d+)$""")

    // Coma decimal (Google Maps en español): exige espacio entre ambos números, o punto y coma.
    private val PAR_COMA = Regex("""^(-?\d{1,3},\d+)(?:\s*;\s*|\s*,?\s+)(-?\d{1,3},\d+)$""")
    private val CONSULTA = Regex("""^(?:loc:)?\s*($NUM)\s*,\s*($NUM)\s*(?:\((.*)\))?$""")

    fun punto(
        lat: String?,
        lon: String?,
    ): PuntoGeo? = punto(lat?.toDoubleOrNull(), lon?.toDoubleOrNull())

    fun punto(
        lat: Double?,
        lon: Double?,
    ): PuntoGeo? = if (lat != null && lon != null && enRango(lat, lon)) PuntoGeo(lat, lon) else null

    private fun enRango(
        lat: Double,
        lon: Double,
    ): Boolean = lat in RANGO_LATITUD && lon in RANGO_LONGITUD

    fun resultado(marcador: Marcador?): ResultadoEnlace =
        marcador?.let { ResultadoEnlace.Punto(it.punto, it.nombre) } ?: ResultadoEnlace.NoReconocido

    /** Texto libre: decimales con punto o coma («39.83, 2.81», «39,83 2,81») o sexagesimales con hemisferio. */
    fun deTexto(texto: String): PuntoGeo? {
        val t = texto.trim()
        return PAR_DECIMAL.matchEntire(t)?.let { punto(it.groupValues[1], it.groupValues[2]) }
            ?: PAR_COMA.matchEntire(t)?.let {
                punto(it.groupValues[1].replace(',', '.'), it.groupValues[2].replace(',', '.'))
            }
            ?: Sexagesimal.leer(t)
    }

    /** Valor de un parámetro de consulta: «lat,lon», «loc:lat,lon» o «lat,lon(Nombre)». */
    fun consulta(texto: String): Marcador? =
        CONSULTA.matchEntire(texto.trim())?.let { m ->
            punto(
                m.groupValues[1],
                m.groupValues[2],
            )?.let { Marcador(it, m.groupValues[GRUPO_NOMBRE].trim().ifEmpty { null }) }
        }

    fun decodificar(texto: String): String =
        runCatching { URLDecoder.decode(texto, Charsets.UTF_8) }.getOrDefault(texto)

    /** Parámetros decodificados; si se repite una clave, vale la primera. */
    fun parametros(consulta: String): Map<String, String> =
        consulta
            .split('&')
            .filter { it.isNotEmpty() }
            .map { it.substringBefore('=') to decodificar(it.substringAfter('=', "")) }
            .distinctBy { it.first }
            .toMap()
}

private object Sexagesimal {
    private const val NUM =
        """(\d{1,3}(?:\.\d+)?)\s*°?\s*""" +
            """(?:(\d{1,2}(?:\.\d+)?)\s*'?\s*""" +
            """(?:(\d{1,2}(?:\.\d+)?)\s*(?:"|'')?)?)?"""

    // Grupos: 1 hemisferio previo, 2-4 grados/minutos/segundos; 5-7 grados/minutos/segundos, 8 hemisferio posterior.
    private val COMPONENTE = Regex("""(?:([NSEW])\s*$NUM|$NUM\s*([NSEW]))""", RegexOption.IGNORE_CASE)
    private const val MIN_POR_GRADO = 60.0
    private const val SEG_POR_GRADO = 3600.0

    /** Grado, minuto y segundo tipográficos (º ˚ ′ ’ ‘ ″ ” “) a sus equivalentes ASCII. */
    private fun simplificar(texto: String): String =
        texto
            .replace('º', '°')
            .replace('˚', '°')
            .replace('′', '\'')
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('″', '"')
            .replace('”', '"')
            .replace('“', '"')

    fun leer(original: String): PuntoGeo? {
        val texto = simplificar(original)
        val encontrados = COMPONENTE.findAll(texto).toList()
        val sobrante = COMPONENTE.replace(texto, "")
        val valores = encontrados.map { valor(it) }
        val limpio = encontrados.size == 2 && sobrante.all { it.isWhitespace() || it == ',' || it == ';' }
        val lat = valores.singleOrNull { it?.first == 'N' }?.second
        val lon = valores.singleOrNull { it?.first == 'E' }?.second
        return if (limpio) Coord.punto(lat, lon) else null
    }

    /** Eje ('N' para latitud, 'E' para longitud) y valor con signo; null si minutos o segundos no son válidos. */
    private fun valor(m: MatchResult): Pair<Char, Double>? {
        val previo = m.groups[1]
        val base = if (previo != null) GRUPO_GRADOS_DETRAS_DEL_EJE else GRUPO_GRADOS_ANTES_DEL_EJE
        val hemisferio = (previo ?: m.groups[GRUPO_EJE_DETRAS])?.value?.uppercase()?.single()
        val grados = numero(m, base)
        val minutos = numero(m, base + 1)
        val segundos = numero(m, base + 2)
        return if (grados != null && hemisferio != null && esValido(grados, minutos, segundos)) {
            val magnitud = grados + (minutos ?: 0.0) / MIN_POR_GRADO + (segundos ?: 0.0) / SEG_POR_GRADO
            val signo = if (hemisferio in "SW") -1.0 else 1.0
            (if (hemisferio in "NS") 'N' else 'E') to signo * magnitud
        } else {
            null
        }
    }

    private fun numero(
        m: MatchResult,
        grupo: Int,
    ): Double? = m.groups[grupo]?.value?.toDoubleOrNull()

    /** Con minutos, los grados son enteros; minutos y segundos < 60. */
    private fun esValido(
        grados: Double,
        minutos: Double?,
        segundos: Double?,
    ): Boolean =
        (minutos == null || (grados % 1.0 == 0.0 && minutos < MIN_POR_GRADO)) &&
            (segundos == null || segundos < MIN_POR_GRADO)
}

private object LectorGeo {
    fun leer(texto: String): ResultadoEnlace {
        val cuerpo = texto.substring("geo:".length)
        val coordenadas = cuerpo.substringBefore('?').substringBefore(';').split(',')
        val base =
            if (coordenadas.size >= 2) Coord.punto(coordenadas[0].trim(), coordenadas[1].trim()) else null
        val principal = base?.takeUnless { it.lat == 0.0 && it.lon == 0.0 } // geo:0,0?q=… = «sin ubicación»
        val q = Coord.parametros(cuerpo.substringAfter('?', ""))["q"]?.trim()
        val deQ = q?.let { Coord.consulta(it) }
        val nombreLibre = q?.takeIf { deQ == null }?.ifEmpty { null }
        return Coord.resultado(
            if (principal != null) Marcador(principal, deQ?.nombre ?: nombreLibre) else deQ,
        )
    }
}

private class PartesUrl(
    url: String,
) {
    val host: String
    val ruta: String
    val consulta: Map<String, String>
    val fragmento: String

    init {
        val sinEsquema = url.substringAfter("://")
        fragmento = sinEsquema.substringAfter('#', "")
        val sinFragmento = sinEsquema.substringBefore('#')
        val hostYRuta = sinFragmento.substringBefore('?')
        host =
            hostYRuta
                .substringBefore('/')
                .substringAfterLast('@')
                .substringBefore(':')
                .lowercase()
        ruta = if ('/' in hostYRuta) "/" + hostYRuta.substringAfter('/') else ""
        consulta = Coord.parametros(sinFragmento.substringAfter('?', ""))
    }
}

private object LectorUrl {
    private val HOSTS_OSM = setOf("openstreetmap.org", "www.openstreetmap.org", "osm.org")
    private val HOST_GOOGLE = Regex("""^(?:.*\.)?google\.[a-z]{2,3}(?:\.[a-z]{2})?$""")

    fun leer(url: String): ResultadoEnlace {
        val partes = PartesUrl(url)
        return when {
            esEnlaceCorto(partes) -> {
                ResultadoEnlace.NecesitaRed(url)
            }

            partes.host in HOSTS_OSM -> {
                Coord.resultado(LectorOsm.leer(partes))
            }

            HOST_GOOGLE.matches(
                partes.host,
            ) && (partes.host.startsWith("maps.") || partes.ruta.startsWith("/maps")) -> {
                Coord.resultado(LectorGoogle.leer(partes, url))
            }

            else -> {
                ResultadoEnlace.NoReconocido
            }
        }
    }

    /** Dominios cuyos enlaces se reconocen aunque se peguen sin `https://`. */
    fun esDominioDeMapas(host: String): Boolean =
        host in HOSTS_OSM || host == "goo.gl" || host == "maps.app.goo.gl" || HOST_GOOGLE.matches(host)

    private fun esEnlaceCorto(p: PartesUrl): Boolean =
        p.host == "maps.app.goo.gl" ||
            (p.host == "goo.gl" && p.ruta.startsWith("/maps")) ||
            (p.host in HOSTS_OSM && p.ruta.startsWith("/go/"))
}

private object LectorOsm {
    private val FRAGMENTO = Regex("""map=\d+(?:\.\d+)?/(-?\d+(?:\.\d+)?)/(-?\d+(?:\.\d+)?)""")

    fun leer(p: PartesUrl): Marcador? =
        (
            Coord.punto(p.consulta["mlat"], p.consulta["mlon"])
                ?: Coord.punto(p.consulta["lat"], p.consulta["lon"])
                ?: FRAGMENTO.find(p.fragmento)?.let { Coord.punto(it.groupValues[1], it.groupValues[2]) }
        )?.let { Marcador(it, null) }
}

private object LectorGoogle {
    private val DATOS_SITIO = Regex("""!3d(-?\d+(?:\.\d+)?)!4d(-?\d+(?:\.\d+)?)""")
    private val CENTRO = Regex("""/@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
    private val LUGAR = Regex("""/place/([^/]+)""")
    private val CLAVES_CONSULTA = listOf("q", "query", "ll", "destination", "daddr")

    fun leer(
        p: PartesUrl,
        url: String,
    ): Marcador? {
        val deConsulta = CLAVES_CONSULTA.firstNotNullOfOrNull { clave -> p.consulta[clave]?.let { Coord.consulta(it) } }
        val lugar =
            LUGAR
                .find(p.ruta)
                ?.groupValues
                ?.get(1)
                ?.let { Coord.decodificar(it) }
        val lugarComoPunto = lugar?.let { Coord.deTexto(it) }
        val punto =
            DATOS_SITIO.find(url)?.let { Coord.punto(it.groupValues[1], it.groupValues[2]) }
                ?: CENTRO.find(p.ruta)?.let { Coord.punto(it.groupValues[1], it.groupValues[2]) }
                ?: deConsulta?.punto
                ?: lugarComoPunto
        val nombre = lugar?.takeIf { lugarComoPunto == null }?.trim()?.ifEmpty { null } ?: deConsulta?.nombre
        return punto?.let { Marcador(it, nombre) }
    }
}
