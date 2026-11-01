package app.trombito.rallycopilot.motor.alertas

import java.io.CharConversionException
import java.io.IOException
import java.io.InputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

/**
 * Adaptador StAX de [LectorXml], solo para los tests del motor (StAX no existe en Android; la app usa
 * `XmlPullParser`). El lector se crea al primer [siguiente] para que los fallos salgan por el contrato.
 */
class LectorXmlStax(
    private val entrada: InputStream,
) : LectorXml {
    private var xml: XMLStreamReader? = null

    override val nombre: String get() = lector().localName
    override val texto: String get() = lector().text

    override fun atributo(nombreLocal: String): String? {
        val r = lector()
        for (i in 0 until r.attributeCount) {
            if (r.getAttributeLocalName(i) == nombreLocal) return r.getAttributeValue(i)
        }
        return null
    }

    override fun siguiente(): EventoXml =
        try {
            val r = xml ?: fabrica().createXMLStreamReader(entrada).also { xml = it }
            if (!r.hasNext()) {
                EventoXml.FIN_DOCUMENTO
            } else {
                when (r.next()) {
                    XMLStreamConstants.START_ELEMENT -> EventoXml.INICIO
                    XMLStreamConstants.END_ELEMENT -> EventoXml.FIN
                    XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> EventoXml.TEXTO
                    XMLStreamConstants.END_DOCUMENT -> EventoXml.FIN_DOCUMENTO
                    else -> EventoXml.OTRO
                }
            }
        } catch (e: XMLStreamException) {
            val causa = e.nestedException ?: e.cause
            // StAX envuelve los fallos del flujo; una codificación rota (CharConversionException) sí es XML malo.
            if (causa is IOException && causa !is CharConversionException) throw causa
            throw XmlMalFormadoException(e.message, e)
        }

    override fun close() {
        xml?.close()
    }

    private fun lector(): XMLStreamReader = checkNotNull(xml) { "Aún no se ha leído ningún evento" }

    private fun fabrica(): XMLInputFactory =
        XMLInputFactory.newFactory().apply {
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            setProperty(XMLInputFactory.IS_COALESCING, true)
        }
}
