package app.trombito.rallycopilot.motor.importar

import app.trombito.rallycopilot.motor.alertas.EventoXml
import app.trombito.rallycopilot.motor.alertas.LectorXml
import app.trombito.rallycopilot.motor.geo.PuntoGeo

/**
 * Lee un KML (2.x) en streaming y devuelve su traza: las geometrías `LineString` (`lon,lat[,alt]` separados por
 * espacios) y `gx:Track` (un `gx:coord` `lon lat alt` por punto) de cada `Placemark`. Con varios, se concatenan los
 * contiguos y gana el grupo más largo ([LectorTraza.elegir]). Puntos, polígonos y anillos se ignoran. Las alturas
 * todas a 0 («a ras de suelo») se descartan. El nombre es el del `Placemark` de la traza elegida o, si no lo tiene,
 * el del `Document`.
 */
object LectorKml {
    /** Lee el documento que entrega [xml] y lo cierra. */
    fun leer(xml: LectorXml): ResultadoImportacion = LectorTraza.resultado { xml.use { Lectura(it).ejecutar() } }

    private class Lectura(
        private val xml: LectorXml,
    ) {
        private val pila = ArrayList<String>()
        private val texto = StringBuilder()
        private val lineas = ArrayList<LineaLeida>()
        private val delPlacemark = ArrayList<List<PuntoGeo>>()
        private var nombrePlacemark: String? = null
        private var nombreDocumento: String? = null
        private var track = ArrayList<PuntoGeo>()
        private var total = 0
        private var esKml = false

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
            if (pila.isEmpty()) esKml = nombre == "kml"
            if (pila.size >= LectorTraza.TOPE_PROFUNDIDAD) {
                throw FalloImportacion(MotivoErrorImportacion.XML_MAL_FORMADO, "Demasiados niveles de anidamiento")
            }
            pila.add(nombre)
            texto.setLength(0)
            when (nombre) {
                "Placemark" -> {
                    delPlacemark.clear()
                    nombrePlacemark = null
                }

                "Track" -> {
                    track = ArrayList()
                }
            }
        }

        private fun acumular() {
            val actual = pila.lastOrNull()
            if (actual != "name" && actual != "coordinates" && actual != "coord") return
            if (texto.length + xml.texto.length > LectorTraza.TOPE_TEXTO) {
                throw FalloImportacion(MotivoErrorImportacion.XML_MAL_FORMADO, "Texto demasiado largo")
            }
            texto.append(xml.texto)
        }

        private fun fin() {
            val nombre = pila.removeLastOrNull() ?: return
            val padre = pila.lastOrNull()
            when {
                nombre == "name" -> nombreDe(padre)
                nombre == "coordinates" && padre == "LineString" -> delPlacemark.add(coordenadas(texto.toString()))
                nombre == "coord" && padre == "Track" -> contarYAnadir(coordGx(texto.toString()))
                nombre == "Track" -> delPlacemark.add(track)
                nombre == "Placemark" -> lineas.addAll(delPlacemark.map { LineaLeida(nombrePlacemark, it) })
            }
            texto.setLength(0)
        }

        private fun nombreDe(padre: String?) {
            val valor = texto.toString().trim().ifEmpty { null }
            when (padre) {
                "Placemark" -> if (nombrePlacemark == null) nombrePlacemark = valor
                "Document" -> if (nombreDocumento == null) nombreDocumento = valor
            }
        }

        private fun contarYAnadir(punto: PuntoGeo?) {
            if (punto != null) {
                comprobarTope(1)
                track.add(punto)
            }
        }

        private fun comprobarTope(nuevos: Int) {
            total += nuevos
            if (total > LectorTraza.TOPE_PUNTOS) {
                throw FalloImportacion(MotivoErrorImportacion.DEMASIADOS_PUNTOS, "Demasiados puntos")
            }
        }

        private fun coordenadas(cadena: String): List<PuntoGeo> {
            val salida = ArrayList<PuntoGeo>()
            for (ficha in cadena.split(' ', '\n', '\r', '\t')) {
                if (ficha.isEmpty()) continue
                val c = ficha.split(',')
                val punto =
                    LectorTraza.punto(
                        c.getOrNull(1)?.toDoubleOrNull(),
                        c.getOrNull(0)?.toDoubleOrNull(),
                        c.getOrNull(2)?.toDoubleOrNull(),
                    )
                if (punto != null) {
                    comprobarTope(1)
                    salida.add(punto)
                }
            }
            return salida
        }

        private fun coordGx(cadena: String): PuntoGeo? {
            val c = cadena.trim().split(' ', '\n', '\t').filter { it.isNotEmpty() }
            return LectorTraza.punto(
                c.getOrNull(1)?.toDoubleOrNull(),
                c.getOrNull(0)?.toDoubleOrNull(),
                c.getOrNull(2)?.toDoubleOrNull(),
            )
        }

        private fun resultado(): ResultadoImportacion {
            if (!esKml) return ResultadoImportacion.Error(MotivoErrorImportacion.SIN_TRAZA, "No es un archivo KML")
            val elegida = LectorTraza.elegir(lineas)
            return if (elegida == null) {
                ResultadoImportacion.Error(MotivoErrorImportacion.SIN_TRAZA, "El KML no trae una línea de dos puntos")
            } else {
                ResultadoImportacion.Ok(elegida.first ?: nombreDocumento, elegida.second)
            }
        }
    }
}
