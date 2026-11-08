package app.trombito.rallycopilot.motor.edicion

import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Gramática de las órdenes de voz: lo que devuelve un reconocedor (ASR) y la orden que se entiende. */
class InterpreteOrdenesTest {
    private fun nota(
        direccion: Direccion,
        grado: Int?,
        modificadores: Set<Modificador> = emptySet(),
        distanciaM: Int? = null,
        cuidado: Boolean = false,
    ) = Orden.Nota(direccion, grado, modificadores, distanciaM, cuidado)

    private val izq = Direccion.IZQUIERDA
    private val der = Direccion.DERECHA

    private fun comprobar(
        texto: String,
        esperada: Orden?,
    ) = assertEquals("«$texto»", esperada, InterpreteOrdenes.interpretar(texto))

    // --- Notas ----------------------------------------------------------------------------------------------------

    @Test
    fun `notas con grado en palabra y modificadores`() {
        val casos =
            mapOf(
                "derecha tres larga" to nota(der, 3, setOf(Modificador.LARGA)),
                "izquierda uno cierra" to nota(izq, 1, setOf(Modificador.CIERRA)),
                "izquierda cuatro abre" to nota(izq, 4, setOf(Modificador.ABRE)),
                "izquierda cinco muy larga" to nota(izq, 5, setOf(Modificador.MUY_LARGA)),
                "derecha dos muy larga cierra" to nota(der, 2, setOf(Modificador.MUY_LARGA, Modificador.CIERRA)),
                "izquierda seis" to nota(izq, 6),
                "derecha dos" to nota(der, 2),
            )
        casos.forEach { (texto, orden) -> comprobar(texto, orden) }
    }

    @Test
    fun `notas con cifras y mayusculas`() {
        comprobar("Derecha 3 larga", nota(der, 3, setOf(Modificador.LARGA)))
        comprobar("IZQUIERDA 4 abre", nota(izq, 4, setOf(Modificador.ABRE)))
        comprobar("izquierda 1", nota(izq, 1))
    }

    @Test
    fun `tildes y signos de puntuacion no importan`() {
        comprobar("Izquierda, cinco, muy larga.", nota(izq, 5, setOf(Modificador.MUY_LARGA)))
        comprobar("derecha cuatro, cincuenta", nota(der, 4, distanciaM = 50))
        comprobar("izquierdá tres", nota(izq, 3))
    }

    @Test
    fun `horquillas`() {
        comprobar("horquilla derecha", nota(der, null))
        comprobar("izquierda horquilla", nota(izq, null))
        comprobar("horquilla izquierda muy larga", nota(izq, null, setOf(Modificador.MUY_LARGA)))
        comprobar("cuidado horquilla derecha", nota(der, null, cuidado = true))
        comprobar("horquilla derecha cincuenta", nota(der, null, distanciaM = 50))
    }

    @Test
    fun `escuadra es grado tres con el modificador escuadra`() {
        val esc = setOf(Modificador.ESCUADRA)
        comprobar("derecha escuadra", nota(der, 3, esc))
        comprobar("Izquierda, escuadra.", nota(izq, 3, esc))
        comprobar("escuadra izquierda", nota(izq, 3, esc))
        comprobar("cuidado derecha escuadra", nota(der, 3, esc, cuidado = true))
        comprobar("derecha escuadra cincuenta", nota(der, 3, esc, distanciaM = 50))
    }

    @Test
    fun `la escuadra no admite modificadores ni se entiende sin direccion`() {
        comprobar("derecha escuadra larga", null)
        comprobar("escuadra", null)
    }

    @Test
    fun `la horquilla nunca lleva cierra ni abre`() {
        comprobar("horquilla derecha cierra", nota(der, null))
        comprobar("horquilla izquierda larga abre", nota(izq, null, setOf(Modificador.LARGA)))
    }

    @Test
    fun `cuidado en la misma frase de la nota`() {
        comprobar("cuidado izquierda dos", nota(izq, 2, cuidado = true))
        comprobar("derecha tres, cuidado", nota(der, 3, cuidado = true))
        comprobar("cuidado derecha uno cierra", nota(der, 1, setOf(Modificador.CIERRA), cuidado = true))
    }

    @Test
    fun `distancia a la siguiente en palabras y cifras se ajusta a la lista`() {
        comprobar("derecha cuatro cincuenta", nota(der, 4, distanciaM = 50))
        comprobar("derecha tres larga ciento veinte", nota(der, 3, setOf(Modificador.LARGA), 120))
        comprobar("izquierda 2 150", nota(izq, 2, distanciaM = 150))
        comprobar("derecha cuatro doscientos cincuenta", nota(der, 4, distanciaM = 250))
        comprobar("derecha cuatro cien", nota(der, 4, distanciaM = 100))
        comprobar("derecha cuatro cincuenta metros", nota(der, 4, distanciaM = 50))
        comprobar("derecha cuatro treinta y cinco", nota(der, 4, distanciaM = 30))
        comprobar("derecha cuatro seiscientos", nota(der, 4, distanciaM = 600))
        comprobar("derecha tres 9", nota(der, 3))
    }

    @Test
    fun `notas simplificadas dan un grado representativo`() {
        comprobar("derecha media", nota(der, 3))
        comprobar("izquierda muy cerrada", nota(izq, 1))
        comprobar("izquierda cerrada", nota(izq, 2))
        comprobar("derecha suave", nota(der, 5))
        comprobar("derecha media abre cincuenta", nota(der, 3, setOf(Modificador.ABRE), 50))
    }

    @Test
    fun `relleno y ruido tipico del reconocedor`() {
        val casos =
            mapOf(
                "la derecha tres" to nota(der, 3),
                "a la izquierda dos" to nota(izq, 2),
                "eh derecha tres larga" to nota(der, 3, setOf(Modificador.LARGA)),
                "copiloto derecha tres" to nota(der, 3),
                "oye copiloto pon izquierda dos" to nota(izq, 2),
                "izquierda tres larga por favor" to nota(izq, 3, setOf(Modificador.LARGA)),
                "vale la izquierda cuatro" to nota(izq, 4),
                "es una derecha tres" to nota(der, 3),
                "derecha tres la nota" to nota(der, 3),
            )
        casos.forEach { (texto, orden) -> comprobar(texto, orden) }
    }

    // --- Órdenes sueltas ------------------------------------------------------------------------------------------

    @Test
    fun `quitar la nota`() {
        listOf("quita", "borra", "quítala", "borra esa nota", "elimina la nota", "Quita esa").forEach {
            comprobar(it, Orden.Quitar)
        }
    }

    @Test
    fun `cuidado suelto marca la nota cercana`() {
        listOf("cuidado", "marca cuidado", "con cuidado", "pon cuidado", "ojo").forEach {
            comprobar(it, Orden.Cuidado)
        }
    }

    @Test
    fun `repite`() {
        listOf("repite", "repítelo", "otra vez", "repite la última nota").forEach { comprobar(it, Orden.Repite) }
    }

    @Test
    fun `mas pronto y mas tarde`() {
        listOf("más pronto", "mas pronto", "avisa antes", "adelanta").forEach { comprobar(it, Orden.MasPronto) }
        listOf("más tarde", "avisa más tarde", "retrasa").forEach { comprobar(it, Orden.MasTarde) }
    }

    @Test
    fun `silencio y habla`() {
        listOf("silencio", "cállate", "no hables", "calla").forEach { comprobar(it, Orden.Silencio) }
        listOf("habla", "vuelve a hablar", "ya puedes hablar").forEach { comprobar(it, Orden.Habla) }
    }

    @Test
    fun `marca aqui`() {
        listOf("marca aquí", "marca", "marca este punto", "marcar aqui").forEach { comprobar(it, Orden.MarcaAqui) }
    }

    // --- Preguntas ------------------------------------------------------------------------------------------------

    private fun comprobarTodas(
        esperada: Orden,
        vararg textos: String,
    ) = textos.forEach { comprobar(it, esperada) }

    @Test
    fun `preguntas de ruta con variantes naturales`() {
        comprobarTodas(
            Orden.Pregunta(TipoPregunta.CUANTO_FALTA),
            "¿cuánto falta?",
            "cuanto falta",
            "oye copiloto cuánto falta por favor",
            "cuánto queda",
            "cuánto me queda",
            "cuántos kilómetros faltan",
            "cuánto falta para llegar",
        )
        comprobarTodas(
            Orden.Pregunta(TipoPregunta.HORA_LLEGADA),
            "¿a qué hora llego?",
            "a que hora llegamos",
            "copiloto a qué hora llego",
            "hora de llegada",
            "cuándo llego",
        )
        comprobarTodas(
            Orden.Pregunta(TipoPregunta.LIMITE),
            "¿qué límite hay?",
            "que limite hay",
            "cuál es el límite",
            "límite de velocidad",
            "qué velocidad máxima hay",
        )
        comprobarTodas(
            Orden.Pregunta(TipoPregunta.DONDE_ESTOY),
            "¿dónde estoy?",
            "donde estamos",
            "en qué carretera estoy",
            "qué carretera es esta",
        )
        comprobarTodas(
            Orden.Pregunta(TipoPregunta.COMO_VOY),
            "¿cómo voy?",
            "como vamos",
            "cómo va el tramo",
            "cómo llevo el tramo",
            "cómo voy de tiempo",
        )
    }

    // --- Control del copiloto -------------------------------------------------------------------------------------

    @Test
    fun `modos del copiloto`() {
        comprobarTodas(Orden.Modo(ModoCopiloto.NOTAS), "modo notas", "Modo notas", "copiloto modo notas")
        comprobarTodas(Orden.Modo(ModoCopiloto.SOLO_ALERTAS), "solo avisos", "modo solo avisos", "solo alertas")
        comprobarTodas(Orden.Modo(ModoCopiloto.COMPLETO), "completo", "modo completo")
        comprobarTodas(Orden.Silencio, "modo silencio", "silencio")
    }

    @Test
    fun `volumen sube y baja`() {
        comprobarTodas(Orden.Volumen(true), "sube el volumen", "subir volumen", "más volumen", "sube la voz")
        comprobarTodas(Orden.Volumen(false), "baja el volumen", "bajar volumen", "menos volumen", "baja la voz")
        comprobarTodas(Orden.Volumen(true), "habla más alto")
        comprobarTodas(Orden.Volumen(false), "habla más bajo")
    }

    @Test
    fun `notas simplificadas o completas`() {
        comprobarTodas(Orden.NotasSimplificadas(true), "modo simplificado", "notas sencillas", "notas simples")
        comprobarTodas(Orden.NotasSimplificadas(false), "notas completas", "notas normales", "notas detalladas")
    }

    @Test
    fun `filtros de cruces rotondas y salidas`() {
        comprobarTodas(Orden.Avisar(Indicacion.CRUCES, false), "sin cruces", "sin los cruces")
        comprobarTodas(Orden.Avisar(Indicacion.CRUCES, true), "con cruces", "con los cruces")
        comprobarTodas(Orden.Avisar(Indicacion.PUENTES, false), "sin puentes", "sin los puentes")
        comprobarTodas(Orden.Avisar(Indicacion.PUENTES, true), "con puentes", "con los puentes")
        comprobarTodas(Orden.Avisar(Indicacion.ROTONDAS, false), "sin rotondas", "sin glorieta")
        comprobarTodas(Orden.Avisar(Indicacion.ROTONDAS, true), "con rotondas", "con glorieta")
        comprobarTodas(Orden.Avisar(Indicacion.SALIDAS, false), "sin salidas", "sin salidas de autopista")
        comprobarTodas(Orden.Avisar(Indicacion.SALIDAS, true), "con salidas", "con salidas de autopista")
    }

    // --- Mapa y cronómetro ----------------------------------------------------------------------------------------

    @Test
    fun `mapa satelite norte y zoom`() {
        comprobarTodas(Orden.Satelite(true), "satélite", "modo satélite", "mapa satélite", "vista satélite")
        comprobarTodas(Orden.Satelite(false), "mapa", "mapa normal", "vista mapa")
        comprobarTodas(Orden.NorteArriba(true), "norte arriba", "con el norte arriba")
        comprobarTodas(Orden.NorteArriba(false), "rumbo arriba", "con el rumbo arriba")
        comprobarTodas(Orden.Zoom(true), "acerca", "acerca el mapa", "más zoom", "acercar")
        comprobarTodas(Orden.Zoom(false), "aleja", "aleja el mapa", "menos zoom", "alejar")
    }

    @Test
    fun `salida parar el crono y radar`() {
        comprobarTodas(Orden.Salida, "salida", "da la salida", "dame la salida", "cuenta atrás")
        comprobarTodas(Orden.ParaCrono, "para el crono", "para el cronómetro", "parar el crono", "termina el tramo")
        comprobarTodas(Orden.RadarAqui, "radar", "avisa radar aquí", "radar aquí", "marca radar", "hay un radar")
    }

    @Test
    fun `esta nota estaba mal`() {
        comprobarTodas(
            Orden.NotaMal,
            "nota mal",
            "esa nota está mal",
            "nota equivocada",
            "esa estaba mal",
            "copiloto, esa nota estaba mal",
            "oye, nota equivocada por favor",
        )
        // Una nota dictada o «quita» con la palabra «mal» suelta no es esta orden.
        comprobar("derecha tres mal", null)
    }

    @Test
    fun `si y no solo como respuesta suelta`() {
        comprobarTodas(Orden.Confirma(true), "sí", "si", "sí por favor", "confirmo", "copiloto sí")
        comprobarTodas(Orden.Confirma(false), "no", "cancela", "no gracias", "olvídalo")
        // Un «sí» perdido en otra frase es ruido, no una confirmación.
        comprobar("sí derecha tres", nota(der, 3))
        comprobar("sí cuánto falta", Orden.Pregunta(TipoPregunta.CUANTO_FALTA))
    }

    @Test
    fun `ayuda`() {
        comprobarTodas(Orden.Ayuda, "ayuda", "ayúdame", "qué puedo decir", "copiloto ayuda")
    }

    @Test
    fun `las ordenes nuevas no se mezclan con notas ni entre si`() {
        comprobar("cuánto falta derecha tres", null)
        comprobar("cuánto falta cómo voy", null)
        comprobar("sube el volumen baja el volumen", null)
        comprobar("sin cruces con cruces", null)
        comprobar("satélite mapa", null)
        comprobar("modo notas silencio", null)
    }

    @Test
    fun `las ordenes de siempre siguen igual con las nuevas presentes`() {
        comprobar("no hables", Orden.Silencio)
        comprobar("avisa antes", Orden.MasPronto)
        comprobar("marca aquí", Orden.MarcaAqui)
        comprobar("habla", Orden.Habla)
        comprobar("derecha tres larga cincuenta", nota(der, 3, setOf(Modificador.LARGA), 50))
    }

    @Test
    fun `una palabra suelta de ruido junto a una orden nueva se tolera pero no demasiada`() {
        comprobar("cuánto falta ya mismo", Orden.Pregunta(TipoPregunta.CUANTO_FALTA))
        comprobar("cuánto falta tomate pepino cebolla", null)
    }

    // --- Lo que no se entiende ------------------------------------------------------------------------------------

    @Test
    fun `vacio o sin sentido devuelve null`() {
        listOf("", "   ", "hola qué tal", "quiero un café", "tal cual").forEach { comprobar(it, null) }
    }

    @Test
    fun `nota incompleta devuelve null`() {
        comprobar("derecha", null)
        comprobar("tres larga", null)
        comprobar("larga", null)
        comprobar("derecha siete", null)
        comprobar("derecha cincuenta", null)
    }

    @Test
    fun `ordenes contradictorias devuelven null`() {
        comprobar("izquierda derecha tres", null)
        comprobar("izquierda cierra abre tres", null)
        comprobar("quita derecha tres", null)
        comprobar("repite silencio", null)
        comprobar("más pronto más tarde", null)
        comprobar("silencio habla", null)
    }

    @Test
    fun `demasiado ruido desconocido devuelve null`() {
        comprobar("el coche de la derecha tiene cuatro ruedas grandes", null)
    }

    // --- Conversión a nota ----------------------------------------------------------------------------------------

    @Test
    fun `una orden de nota se convierte en nota en la posicion dada`() {
        val orden = InterpreteOrdenes.interpretar("cuidado derecha tres larga ciento veinte") as Orden.Nota
        val nota = orden.aNota(520.0)
        assertEquals(520.0, nota.posicionM, 0.0)
        assertEquals(520.0, nota.finM, 0.0)
        assertEquals(der, nota.direccion)
        assertEquals(3, nota.grado)
        assertEquals(setOf(Modificador.LARGA), nota.modificadores)
        assertEquals(120, nota.distanciaSiguienteM)
        assertEquals(true, nota.cuidado)
        assertNull(InterpreteOrdenes.interpretar("derecha"))
    }
}
