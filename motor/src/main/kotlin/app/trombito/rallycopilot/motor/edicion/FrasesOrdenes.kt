package app.trombito.rallycopilot.motor.edicion

import app.trombito.rallycopilot.motor.voz.ModoCopiloto

/**
 * Las órdenes de voz que se dicen con una frase fija (preguntas, control del copiloto, mapa y cronómetro, diálogo), con
 * las variantes naturales de cada una. Es la fuente única: [InterpreteOrdenes] las reconoce (tolerando relleno y
 * ruido) y la gramática cerrada del reconocedor las incluye tal cual, de modo que lo que está aquí es lo que se
 * entiende. Las notas dictadas y las órdenes sueltas de edición (quita, repite, más pronto…) no están aquí.
 *
 * Las órdenes con un lugar («llévame a Sóller», «añade parada en Inca») no están aquí salvo sus prefijos
 * ([PREFIJOS_IR], [PREFIJO_PARADA]): el lugar lo reconoce `OrdenesLugar` y los nombres los pone la app. Todas las
 * palabras han de existir en el vocabulario del modelo del reconocedor (lo comprueba un test de la app).
 *
 * Una frase puede escribirse con tildes y signos; el intérprete las normaliza. Dos órdenes distintas no pueden tener
 * la misma frase una vez quitado el relleno (lo comprueba un test).
 */
object FrasesOrdenes {
    /** Órdenes cuya frase hay que decir sola (sin nada más): un «sí» o un «no» perdido en otra frase no cuenta. */
    internal val EXACTAS: Set<Orden> = setOf(Orden.Confirma(true), Orden.Confirma(false), Orden.Otro)

    /** Cómo se pide ir a un lugar («llévame a Sóller»); la gramática del reconocedor les añade los nombres. */
    val PREFIJOS_IR: List<String> = listOf("llévame a", "vamos a")

    /** Cómo se añade una parada a la ruta en curso («añade parada en Sóller»). */
    const val PREFIJO_PARADA = "añade parada en"

    /** Cada orden con sus frases. */
    val porOrden: List<Pair<Orden, List<String>>> =
        listOf(
            Orden.Pregunta(TipoPregunta.CUANTO_FALTA) to
                listOf(
                    "cuánto falta",
                    "cuánto queda",
                    "cuánto me queda",
                    "cuánto falta para llegar",
                    "cuántos kilómetros faltan",
                    "cuántos kilómetros quedan",
                ),
            Orden.Pregunta(TipoPregunta.HORA_LLEGADA) to
                listOf(
                    "a qué hora llego",
                    "a qué hora llegamos",
                    "hora de llegada",
                    "cuándo llego",
                    "cuándo llegamos",
                ),
            Orden.Pregunta(TipoPregunta.LIMITE) to
                listOf(
                    "qué límite hay",
                    "cuál es el límite",
                    "límite de velocidad",
                    "qué velocidad máxima hay",
                    "límite",
                ),
            Orden.Pregunta(TipoPregunta.DONDE_ESTOY) to
                listOf("dónde estoy", "dónde estamos", "en qué carretera estoy", "qué carretera es esta"),
            Orden.Pregunta(TipoPregunta.COMO_VOY) to
                listOf("cómo voy", "cómo vamos", "cómo va el tramo", "cómo llevo el tramo", "cómo voy de tiempo"),
            Orden.Modo(ModoCopiloto.NOTAS) to listOf("modo notas"),
            Orden.Modo(ModoCopiloto.SOLO_ALERTAS) to listOf("solo avisos", "modo solo avisos", "solo alertas"),
            Orden.Modo(ModoCopiloto.COMPLETO) to listOf("completo", "modo completo"),
            Orden.Silencio to listOf("modo silencio"),
            Orden.Volumen(true) to
                listOf("sube el volumen", "subir volumen", "más volumen", "sube la voz", "habla más alto"),
            Orden.Volumen(false) to
                listOf("baja el volumen", "bajar volumen", "menos volumen", "baja la voz", "habla más bajo"),
            Orden.NotasSimplificadas(true) to listOf("modo simplificado", "notas sencillas", "notas simples"),
            Orden.NotasSimplificadas(false) to listOf("notas completas", "notas normales", "notas detalladas"),
            Orden.Avisar(Indicacion.CRUCES, false) to listOf("sin cruces", "sin los cruces"),
            Orden.Avisar(Indicacion.CRUCES, true) to listOf("con cruces", "con los cruces"),
            Orden.Avisar(Indicacion.PUENTES, false) to listOf("sin puentes", "sin los puentes"),
            Orden.Avisar(Indicacion.PUENTES, true) to listOf("con puentes", "con los puentes"),
            Orden.Avisar(Indicacion.ROTONDAS, false) to listOf("sin rotondas", "sin glorieta"),
            Orden.Avisar(Indicacion.ROTONDAS, true) to listOf("con rotondas", "con glorieta"),
            Orden.Avisar(Indicacion.SALIDAS, false) to listOf("sin salidas", "sin salidas de autopista"),
            Orden.Avisar(Indicacion.SALIDAS, true) to listOf("con salidas", "con salidas de autopista"),
            Orden.Satelite(true) to listOf("satélite", "modo satélite", "mapa satélite", "vista satélite"),
            Orden.Satelite(false) to listOf("mapa", "mapa normal", "vista mapa", "modo mapa"),
            Orden.NorteArriba(true) to listOf("norte arriba", "con el norte arriba"),
            Orden.NorteArriba(false) to listOf("rumbo arriba", "con el rumbo arriba"),
            Orden.Zoom(true) to listOf("acerca", "acerca el mapa", "acercar", "más zoom"),
            Orden.Zoom(false) to listOf("aleja", "aleja el mapa", "alejar", "menos zoom"),
            Orden.Salida to listOf("salida", "da la salida", "dame la salida", "cuenta atrás"),
            Orden.ParaCrono to
                listOf(
                    "para el crono",
                    "para el cronómetro",
                    "parar el crono",
                    "termina el tramo",
                    "termina la conducción",
                ),
            Orden.RadarAqui to
                listOf("radar", "avisa radar aquí", "radar aquí", "marca radar", "marca un radar", "hay un radar"),
            Orden.TripACero to listOf("trip a cero", "poner trip a cero", "reinicia el trip"),
            Orden.NotaMal to
                listOf("nota mal", "esa nota está mal", "nota equivocada", "esa estaba mal"),
            Orden.VolverACasa to
                listOf(
                    "volver a casa",
                    "vuelve a casa",
                    "llévame a casa",
                    "vamos a casa",
                    "ir a casa",
                ),
            Orden.CancelarRuta to
                listOf("cancela la ruta", "cancelar la ruta", "quita la ruta", "cancela el guiado"),
            Orden.Otro to listOf("otro", "otra", "siguiente"),
            Orden.Confirma(true) to listOf("sí", "confirmo", "afirmativo"),
            Orden.Confirma(false) to listOf("no", "cancela", "olvídalo", "negativo"),
            Orden.Ayuda to listOf("ayuda", "ayúdame", "qué puedo decir", "qué órdenes hay"),
        ) + TipoLugar.entries.map { Orden.MasCercano(it) to it.frases() }

    /** Todas las frases, sin repetir, tal como se escriben aquí (para la gramática del reconocedor). */
    fun frases(): List<String> = porOrden.flatMap { it.second }.distinct()
}
