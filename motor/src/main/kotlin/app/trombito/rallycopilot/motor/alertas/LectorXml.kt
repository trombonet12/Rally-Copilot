package app.trombito.rallycopilot.motor.alertas

import java.io.IOException

/** Tipo del evento que acaba de devolver [LectorXml.siguiente]. */
enum class EventoXml {
    /** Apertura de elemento: [LectorXml.nombre] y [LectorXml.atributo] son los suyos. */
    INICIO,

    /** Cierre de elemento: [LectorXml.nombre] es el suyo. */
    FIN,

    /** Texto o CDATA: [LectorXml.texto]. Puede llegar en varios trozos seguidos. */
    TEXTO,

    /** Fin del documento: no hay más eventos. */
    FIN_DOCUMENTO,

    /** Comentarios, instrucciones de proceso, etc.: se ignoran. */
    OTRO,
}

/** XML que no se puede leer por estar mal formado (etiquetas sin cerrar, truncado, codificación rota...). */
class XmlMalFormadoException(
    mensaje: String?,
    causa: Throwable? = null,
) : Exception(mensaje, causa)

/**
 * Lector XML en streaming de tipo pull, mínimo y sin dependencias de plataforma: el motor es JVM puro y
 * `javax.xml.stream` (StAX) no existe en Android, así que cada plataforma aporta su adaptador (StAX en los tests del
 * motor, XmlPullParser en la app).
 *
 * Contrato de los adaptadores: espacios de nombres activos (los nombres son los locales, sin prefijo), sin resolver DTD
 * ni entidades externas, y solo estas dos excepciones: [XmlMalFormadoException] si el XML es inválido e [IOException]
 * si falla la lectura del flujo (una codificación rota cuenta como XML malo y se lanza como
 * [java.io.CharConversionException]).
 */
interface LectorXml : AutoCloseable {
    /** Avanza al siguiente evento y lo devuelve. */
    @Throws(XmlMalFormadoException::class, IOException::class)
    fun siguiente(): EventoXml

    /** Nombre local del elemento del último [EventoXml.INICIO] o [EventoXml.FIN]. */
    val nombre: String

    /** Texto del último [EventoXml.TEXTO]. */
    val texto: String

    /** Valor del atributo con ese nombre local (sin importar su espacio de nombres) del último [EventoXml.INICIO]. */
    fun atributo(nombreLocal: String): String?
}
