package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import java.io.CharConversionException
import java.io.IOException

/**
 * Lee en streaming el XML DATEX II v3.7 de incidencias de la DGT (NAP) y devuelve las de Mallorca. Recibe un
 * [LectorXml] sobre el flujo ya descomprimido; no toca disco ni red y no carga el documento entero en memoria
 * (≈ 6 MB sin comprimir).
 *
 * - Una [Incidencia] por `situation`; sus registros se fusionan: el tipo es el más grave, los puntos son la unión
 *   (desde, puntuales, hasta), la gravedad la mayor y el fin solo existe si todos los registros lo traen.
 * - Se quedan las situaciones con algún punto en [MALLORCA]; se descartan las de prueba (`informationStatus = test`),
 *   los registros `suspended` y los puntos con coordenadas ilegibles.
 * - Los elementos desconocidos se ignoran. Los datos malos nunca lanzan: devuelve [ResultadoDatex.Error] y quien llama
 *   conserva la caché anterior. Los adaptadores de [LectorXml] no resuelven DTD ni entidades externas.
 */
object LectorDatex {
    /** Recuadro de Mallorca (lon 2,30–3,50; lat 39,26–39,97): deja fuera Menorca, Ibiza y Cabrera. */
    val MALLORCA = CajaGeo(latMin = 39.26, lonMin = 2.30, latMax = 39.97, lonMax = 3.50)

    /** Lee el documento que entrega [xml] y lo cierra. Los fallos de lectura salen como [ResultadoDatex.Error]. */
    fun leer(xml: LectorXml): ResultadoDatex =
        try {
            xml.use { Lectura(it).ejecutar() }
        } catch (e: XmlMalFormadoException) {
            ResultadoDatex.Error(MotivoErrorDatex.XML_MAL_FORMADO, e.message)
        } catch (e: CharConversionException) {
            // Una codificación rota es XML malo, no un fallo del flujo.
            ResultadoDatex.Error(MotivoErrorDatex.XML_MAL_FORMADO, e.message)
        } catch (e: IOException) {
            ResultadoDatex.Error(MotivoErrorDatex.ENTRADA_ILEGIBLE, e.message)
        }

    /** Máquina de estados sobre los eventos del lector; cada hoja se procesa al cerrar su elemento. */
    private class Lectura(
        private val xml: LectorXml,
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
            while (true) {
                when (xml.siguiente()) {
                    EventoXml.INICIO -> abrir(xml.nombre)
                    EventoXml.TEXTO -> texto.append(xml.texto)
                    EventoXml.FIN -> cerrar(xml.nombre, texto.toString().trim())
                    EventoXml.FIN_DOCUMENTO -> break
                    EventoXml.OTRO -> Unit
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
                    situacion = SituacionDatex(xml.atributo("id"))
                }

                "situationRecord" -> {
                    val tipo = xml.atributo("type")?.substringAfter(':')
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
