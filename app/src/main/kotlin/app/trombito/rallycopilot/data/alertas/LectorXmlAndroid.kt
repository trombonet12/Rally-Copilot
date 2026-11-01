package app.trombito.rallycopilot.data.alertas

import android.util.Xml
import app.trombito.rallycopilot.motor.alertas.EventoXml
import app.trombito.rallycopilot.motor.alertas.LectorXml
import app.trombito.rallycopilot.motor.alertas.XmlMalFormadoException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.CharConversionException
import java.io.IOException
import java.io.InputStream

/**
 * Adaptador de [LectorXml] sobre el `XmlPullParser` de Android (`javax.xml.stream` no existe en el móvil). Con espacios
 * de nombres activos. El parser de Android (KXmlParser) sí expande las entidades internas que define un DOCTYPE
 * («billion laughs»), así que se lee con `nextToken()` y se rechaza como [XmlMalFormadoException] cualquier DOCTYPE y
 * toda referencia a entidad que no sea de las cinco predefinidas ni numérica; el feed DATEX no trae DOCTYPE. No
 * resuelve entidades externas. Se crea al primer [siguiente] para que cualquier fallo salga por el contrato del lector.
 */
class LectorXmlAndroid(
    private val entrada: InputStream,
) : LectorXml {
    private var parser: XmlPullParser? = null
    private var hayRaiz = false
    private var abiertos = 0

    override val nombre: String get() = checkNotNull(parser).name
    override val texto: String get() = checkNotNull(parser).text.orEmpty()

    override fun atributo(nombreLocal: String): String? {
        val p = checkNotNull(parser)
        for (i in 0 until p.attributeCount) {
            if (p.getAttributeName(i) == nombreLocal) return p.getAttributeValue(i)
        }
        return null
    }

    override fun siguiente(): EventoXml =
        try {
            evento(parser ?: abrir().also { parser = it })
        } catch (e: XmlPullParserException) {
            // Un fallo del flujo no es XML malo; una codificación rota sí.
            val fallo = e.detail as? IOException
            throw when {
                fallo != null && fallo !is CharConversionException -> fallo
                else -> XmlMalFormadoException(e.message, e)
            }
        }

    private fun evento(p: XmlPullParser): EventoXml =
        when (p.nextToken()) {
            XmlPullParser.START_TAG -> {
                hayRaiz = true
                abiertos++
                EventoXml.INICIO
            }

            XmlPullParser.END_TAG -> {
                abiertos--
                EventoXml.FIN
            }

            XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.IGNORABLE_WHITESPACE -> {
                EventoXml.TEXTO
            }

            XmlPullParser.DOCDECL -> {
                throw XmlMalFormadoException("El documento no puede llevar DOCTYPE")
            }

            XmlPullParser.ENTITY_REF -> {
                if (!esReferenciaSegura(p.name)) throw XmlMalFormadoException("Entidad no permitida: ${p.name}")
                EventoXml.TEXTO
            }

            XmlPullParser.END_DOCUMENT -> {
                finDeDocumento()
            }

            else -> {
                EventoXml.OTRO
            }
        }

    /** Android no se queja de un flujo vacío ni de uno cortado con etiquetas abiertas; StAX sí. */
    private fun finDeDocumento(): EventoXml {
        if (!hayRaiz) throw XmlMalFormadoException("El documento no tiene elemento raíz")
        if (abiertos != 0) throw XmlMalFormadoException("El documento termina con etiquetas sin cerrar")
        return EventoXml.FIN_DOCUMENTO
    }

    private fun esReferenciaSegura(nombre: String?): Boolean =
        nombre != null && (nombre.startsWith("#") || nombre in ENTIDADES_PREDEFINIDAS)

    private fun abrir(): XmlPullParser =
        Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(entrada, null)
        }

    override fun close() {
        // El flujo es de quien llama (lo cierra con `use`); el parser no retiene recursos propios.
    }

    private companion object {
        val ENTIDADES_PREDEFINIDAS = setOf("amp", "lt", "gt", "quot", "apos")
    }
}
