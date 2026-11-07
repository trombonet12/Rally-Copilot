package app.trombito.rallycopilot.motor.importar

import app.trombito.rallycopilot.motor.alertas.EventoXml
import app.trombito.rallycopilot.motor.alertas.LectorXml
import app.trombito.rallycopilot.motor.edicion.CodecEdiciones
import app.trombito.rallycopilot.motor.geo.PuntoGeo

/**
 * Lee un GPX (1.0 o 1.1) en streaming y devuelve su traza. Prioridad: los `trk` (los `trkseg` de un mismo `trk` se
 * concatenan siempre; entre `trk` se aplica la regla de contiguas o la más larga), si no hay, la primera ruta `rte`
 * con ≥ 2 puntos y, si tampoco, los `wpt` en orden. Los waypoints se ignoran si hay traza o ruta. Los puntos con
 * coordenadas ilegibles se saltan; la elevación (`ele`) es opcional. Los nombres son los locales: un GPX con prefijo
 * de espacio de nombres se lee igual. Si el GPX lo exportó Rally Copilot ([EscritorGpx]) también se lee su extensión
 * (`gpx/extensions/tramoRally`): sectores, microsectores y ediciones; las ilegibles se saltan y solo se admiten
 * [LectorTraza.TOPE_EXTENSION] elementos de cada clase.
 */
object LectorGpx {
    /** Lee el documento que entrega [xml] y lo cierra. */
    fun leer(xml: LectorXml): ResultadoImportacion = LectorTraza.resultado { xml.use { Lectura(it).ejecutar() } }

    private class Lectura(
        private val xml: LectorXml,
    ) {
        private val pila = ArrayList<String>()
        private val texto = StringBuilder()
        private val trks = ArrayList<LineaLeida>()
        private val rutas = ArrayList<LineaLeida>()
        private val waypoints = ArrayList<PuntoGeo>()
        private var nombreMeta: String? = null

        private var puntosTrk = ArrayList<PuntoGeo>()
        private var nombreTrk: String? = null
        private var puntosRte = ArrayList<PuntoGeo>()
        private var nombreRte: String? = null
        private var total = 0
        private var esGpx = false

        private var hayExtension = false
        private val sectores = ArrayList<Double>()
        private val microsectores = ArrayList<Double>()
        private val ediciones = ArrayList<EdicionArchivo>()

        private var lat: Double? = null
        private var lon: Double? = null
        private var alt: Double? = null

        fun ejecutar(): ResultadoImportacion {
            while (true) {
                when (xml.siguiente()) {
                    EventoXml.INICIO -> inicio()
                    EventoXml.FIN -> fin()
                    EventoXml.TEXTO -> acumular()
                    EventoXml.FIN_DOCUMENTO -> return resultado()
                    EventoXml.OTRO -> Unit
                }
            }
        }

        private fun inicio() {
            val nombre = xml.nombre
            if (pila.isEmpty()) esGpx = nombre == "gpx"
            if (pila.size >= LectorTraza.TOPE_PROFUNDIDAD) {
                throw FalloImportacion(MotivoErrorImportacion.XML_MAL_FORMADO, "Demasiados niveles de anidamiento")
            }
            pila.add(nombre)
            texto.setLength(0)
            when (nombre) {
                "trk" -> {
                    puntosTrk = ArrayList()
                    nombreTrk = null
                }

                "rte" -> {
                    puntosRte = ArrayList()
                    nombreRte = null
                }

                "trkpt", "rtept", "wpt" -> {
                    lat = xml.atributo("lat")?.trim()?.toDoubleOrNull()
                    lon = xml.atributo("lon")?.trim()?.toDoubleOrNull()
                    alt = null
                }

                "tramoRally" -> {
                    if (pila.size == PROFUNDIDAD_RALLY) hayExtension = true
                }

                "sectorRally", "microsectorRally", "edicionRally" -> {
                    if (enExtension()) elementoRally(nombre)
                }
            }
        }

        /** Cierto dentro de `gpx/extensions/tramoRally`, donde solo el hijo directo es de Rally Copilot. */
        private fun enExtension(): Boolean =
            hayExtension && pila.size == PROFUNDIDAD_RALLY + 1 && pila[1] == "extensions" && pila[2] == "tramoRally"

        private fun elementoRally(nombre: String) {
            val metros =
                xml
                    .atributo("m")
                    ?.trim()
                    ?.toDoubleOrNull()
                    ?.takeIf { it.isFinite() } ?: return
            when (nombre) {
                "sectorRally" -> if (sectores.size < LectorTraza.TOPE_EXTENSION) sectores.add(metros)
                "microsectorRally" -> if (microsectores.size < LectorTraza.TOPE_EXTENSION) microsectores.add(metros)
                else -> if (ediciones.size < LectorTraza.TOPE_EXTENSION) edicion(metros)?.let { ediciones.add(it) }
            }
        }

        private fun edicion(metros: Double): EdicionArchivo? {
            val accion = xml.atributo("tipo")?.let { CodecEdiciones.accion(it, xml.atributo("nota")) }
            return accion?.let { EdicionArchivo(metros, it, xml.atributo("t")?.trim()?.toLongOrNull() ?: 0L) }
        }

        private fun acumular() {
            // Solo interesan <name> y <ele>: el resto del texto (descripciones largas) no se guarda.
            val actual = pila.lastOrNull()
            if (actual != "name" && actual != "ele") return
            if (texto.length + xml.texto.length > LectorTraza.TOPE_TEXTO) {
                throw FalloImportacion(MotivoErrorImportacion.XML_MAL_FORMADO, "Texto demasiado largo")
            }
            texto.append(xml.texto)
        }

        private fun fin() {
            val nombre = pila.removeLastOrNull() ?: return
            val padre = pila.lastOrNull()
            when (nombre) {
                "name" -> nombreDe(padre)
                "ele" -> alt = texto.toString().trim().toDoubleOrNull()
                "trkpt" -> anadir(puntosTrk)
                "rtept" -> anadir(puntosRte)
                "wpt" -> anadir(waypoints)
                "trk" -> trks.add(LineaLeida(nombreTrk, puntosTrk))
                "rte" -> rutas.add(LineaLeida(nombreRte, puntosRte))
            }
            texto.setLength(0)
        }

        private fun nombreDe(padre: String?) {
            val valor = texto.toString().trim().ifEmpty { null }
            when (padre) {
                "trk" -> if (nombreTrk == null) nombreTrk = valor
                "rte" -> if (nombreRte == null) nombreRte = valor
                "metadata", "gpx" -> if (nombreMeta == null) nombreMeta = valor
            }
        }

        private fun anadir(destino: MutableList<PuntoGeo>) {
            val punto = LectorTraza.punto(lat, lon, alt)
            if (punto != null) {
                total++
                if (total > LectorTraza.TOPE_PUNTOS) {
                    throw FalloImportacion(MotivoErrorImportacion.DEMASIADOS_PUNTOS, "Demasiados puntos")
                }
                destino.add(punto)
            }
            lat = null
            lon = null
            alt = null
        }

        private fun resultado(): ResultadoImportacion {
            if (!esGpx) return ResultadoImportacion.Error(MotivoErrorImportacion.SIN_TRAZA, "No es un archivo GPX")
            val primeraRuta = rutas.firstOrNull { it.puntos.size >= 2 }
            val elegida =
                LectorTraza.elegir(trks)
                    ?: primeraRuta?.let { LectorTraza.elegir(listOf(it)) }
                    ?: LectorTraza.elegir(listOf(LineaLeida(null, waypoints)))
            return if (elegida == null) {
                ResultadoImportacion.Error(MotivoErrorImportacion.SIN_TRAZA, "El GPX no trae una traza de dos puntos")
            } else {
                val extension = if (hayExtension) ExtensionRally(sectores, microsectores, ediciones) else null
                ResultadoImportacion.Ok(elegida.first ?: primeraRuta?.nombre ?: nombreMeta, elegida.second, extension)
            }
        }
    }

    /** Profundidad de `gpx/extensions/tramoRally` contando el propio elemento. */
    private const val PROFUNDIDAD_RALLY = 3
}
