package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import java.io.CharConversionException
import java.io.IOException
import java.io.InputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

/**
 * Lee en streaming el XML DATEX II v3.7 de incidencias de la DGT (NAP) y devuelve las de Mallorca. Recibe el flujo ya
 * descomprimido; no toca disco ni red y no carga el documento entero en memoria (≈ 6 MB sin comprimir).
 *
 * - Una [Incidencia] por `situation`; sus registros se fusionan: el tipo es el más grave, los puntos son la unión
 *   (desde, puntuales, hasta), la gravedad la mayor y el fin solo existe si todos los registros lo traen.
 * - Se quedan las situaciones con algún punto en [MALLORCA]; se descartan las de prueba (`informationStatus = test`),
 *   los registros `suspended` y los puntos con coordenadas ilegibles.
 * - Los elementos desconocidos se ignoran. Los datos malos nunca lanzan: devuelve [ResultadoDatex.Error] y quien llama
 *   conserva la caché anterior. El lector no resuelve DTD ni entidades externas.
 */
object LectorDatex {
    /** Recuadro de Mallorca (lon 2,30–3,50; lat 39,26–39,97): deja fuera Menorca, Ibiza y Cabrera. */
    val MALLORCA = CajaGeo(latMin = 39.26, lonMin = 2.30, latMax = 39.97, lonMax = 3.50)

    fun leer(entrada: InputStream): ResultadoDatex =
        try {
            val xml = fabrica().createXMLStreamReader(entrada)
            try {
                Lectura(xml).ejecutar()
            } finally {
                xml.close()
            }
        } catch (e: XMLStreamException) {
            val causa = e.nestedException ?: e.cause
            // StAX envuelve los fallos del flujo; una codificación rota (CharConversionException) sí es XML malo.
            val motivo =
                if (causa is IOException && causa !is CharConversionException) {
                    MotivoErrorDatex.ENTRADA_ILEGIBLE
                } else {
                    MotivoErrorDatex.XML_MAL_FORMADO
                }
            ResultadoDatex.Error(motivo, e.message)
        } catch (e: IOException) {
            ResultadoDatex.Error(MotivoErrorDatex.ENTRADA_ILEGIBLE, e.message)
        }

    private fun fabrica(): XMLInputFactory =
        XMLInputFactory.newFactory().apply {
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            setProperty(XMLInputFactory.IS_COALESCING, true)
        }

    private const val XSI = "http://www.w3.org/2001/XMLSchema-instance"

    /** Máquina de estados sobre los eventos StAX; cada hoja se procesa al cerrar su elemento. */
    private class Lectura(
        private val xml: XMLStreamReader,
    ) {
        private val incidencias = ArrayList<Incidencia>()
        private var leidas = 0
        private var esDatex = false
        private var situacion: SituacionDatex? = null
        private var registro: RegistroDatex? = null
        private var enLocalizacion = false
        private var rol: String? = null
        private var lat: Double? = null
        private var lon: Double? = null
        private val texto = StringBuilder()

        fun ejecutar(): ResultadoDatex {
            while (xml.hasNext()) {
                when (xml.next()) {
                    XMLStreamConstants.START_ELEMENT -> abrir(xml.localName)
                    XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> texto.append(xml.text)
                    XMLStreamConstants.END_ELEMENT -> cerrar(xml.localName, texto.toString().trim())
                }
            }
            return if (esDatex) {
                ResultadoDatex.Ok(incidencias, leidas)
            } else {
                ResultadoDatex.Error(MotivoErrorDatex.NO_ES_DATEX, "No hay publicationTime ni situation")
            }
        }

        private fun abrir(nombre: String) {
            texto.setLength(0)
            when (nombre) {
                "situation" -> {
                    esDatex = true
                    leidas++
                    situacion = SituacionDatex(xml.getAttributeValue(null, "id"))
                }

                "situationRecord" -> {
                    val tipo = xml.getAttributeValue(XSI, "type")?.substringAfter(':')
                    if (situacion != null) registro = RegistroDatex(tipo)
                }

                "locationReference" -> {
                    enLocalizacion = true
                }

                "from", "to", "point" -> {
                    if (enLocalizacion) rol = nombre
                }

                "pointCoordinates" -> {
                    lat = null
                    lon = null
                }
            }
        }

        private fun cerrar(
            nombre: String,
            valor: String,
        ) {
            when (nombre) {
                "publicationTime" -> esDatex = true
                "latitude" -> lat = valor.toDoubleOrNull()
                "longitude" -> lon = valor.toDoubleOrNull()
                "pointCoordinates" -> registro?.punto(rol, lat, lon)
                "from", "to", "point" -> rol = null
                "locationReference" -> enLocalizacion = false
                "informationStatus" -> situacion?.prueba = valor == "test"
                "overallSeverity" -> if (registro == null) situacion?.gravedad = valor
                "situationRecord" -> cerrarRegistro()
                "situation" -> cerrarSituacion()
                else -> registro?.hoja(nombre, valor)
            }
        }

        private fun cerrarRegistro() {
            registro?.let { situacion?.registros?.add(it) }
            registro = null
        }

        private fun cerrarSituacion() {
            situacion?.aIncidencia()?.let(incidencias::add)
            situacion = null
        }
    }

    /** Una `situation` en construcción. */
    private class SituacionDatex(
        val id: String?,
    ) {
        var prueba = false
        var gravedad: String? = null
        val registros = ArrayList<RegistroDatex>()

        private fun sirve(
            vigentes: List<RegistroDatex>,
            puntos: List<PuntoGeo>,
        ): Boolean = !prueba && vigentes.isNotEmpty() && puntos.any(MALLORCA::contiene)

        /** `null` si no sirve: de prueba, sin registros vigentes, sin id o sin ningún punto en Mallorca. */
        fun aIncidencia(): Incidencia? {
            val vigentes = registros.filter { it.vigente }
            val puntos = vigentes.flatMap { it.puntos }.distinct()
            if (id == null || !sirve(vigentes, puntos)) return null
            val tipos = vigentes.map(ClasificadorDatex::tipo)
            val tipo = ClasificadorDatex.masGrave(tipos)
            val principal = vigentes[tipos.indexOf(tipo)]
            val inicios = vigentes.mapNotNull { ClasificadorDatex.instante(it.hojas["overallStartTime"]) }
            val fines = vigentes.map { ClasificadorDatex.instante(it.hojas["overallEndTime"]) }
            val gravedades =
                (vigentes.map { it.hojas["severity"] } + gravedad).mapNotNull(ClasificadorDatex::gravedad)
            return Incidencia(
                id = id,
                tipo = tipo,
                puntos = puntos,
                carretera = vigentes.firstNotNullOfOrNull { it.hojas["roadName"] },
                descripcion = ClasificadorDatex.descripcion(principal, tipo),
                inicio = inicios.minOrNull(),
                fin = if (fines.all { it != null }) fines.filterNotNull().maxOrNull() else null,
                gravedad = gravedades.maxOrNull(),
            )
        }
    }
}
