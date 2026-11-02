package app.trombito.rallycopilot.motor.importar

import app.trombito.rallycopilot.motor.alertas.EventoXml
import app.trombito.rallycopilot.motor.alertas.LectorXml
import app.trombito.rallycopilot.motor.geo.PuntoGeo

/**
 * Lee un GPX (1.0 o 1.1) en streaming y devuelve su traza. Prioridad: los `trk` (los `trkseg` de un mismo `trk` se
 * concatenan siempre; entre `trk` se aplica la regla de contiguas o la más larga), si no hay, la primera ruta `rte`
 * con ≥ 2 puntos y, si tampoco, los `wpt` en orden. Los waypoints se ignoran si hay traza o ruta. Los puntos con
 * coordenadas ilegibles se saltan; la elevación (`ele`) es opcional. Los nombres son los locales: un GPX con prefijo
 * de espacio de nombres se lee igual.
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
            }
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
                ResultadoImportacion.Ok(elegida.first ?: primeraRuta?.nombre ?: nombreMeta, elegida.second)
            }
        }
    }
}
