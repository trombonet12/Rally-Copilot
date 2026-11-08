package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.edicion.TipoLugar
import org.junit.Assert.assertEquals
import org.junit.Test

/** Frases con las que el copiloto responde a las preguntas y confirma las órdenes de voz. */
class RespuestasVozTest {
    @Test
    fun `numeros de carretera en palabras hasta cuatro cifras`() {
        assertEquals("cero", FormatoHablado.numero(0))
        assertEquals("once", FormatoHablado.numero(11))
        assertEquals("novecientos noventa y nueve", FormatoHablado.numero(999))
        assertEquals("mil", FormatoHablado.numero(1000))
        assertEquals("mil uno", FormatoHablado.numero(1001))
        assertEquals("dos mil ciento cuarenta y uno", FormatoHablado.numero(2141))
        assertEquals("10000", FormatoHablado.numero(10_000))
    }

    @Test
    fun `cuanto falta en kilometros con decimas y minutos`() {
        assertEquals("faltan doce kilómetros, quince minutos", RespuestasVoz.cuantoFalta(12_400.0, 900.0))
        assertEquals("faltan tres coma dos kilómetros, cinco minutos", RespuestasVoz.cuantoFalta(3_200.0, 300.0))
        assertEquals("faltan dos kilómetros y medio, cuatro minutos", RespuestasVoz.cuantoFalta(2_500.0, 240.0))
        assertEquals("falta un kilómetro, un minuto", RespuestasVoz.cuantoFalta(1_000.0, 60.0))
        assertEquals("falta un kilómetro y medio, tres minutos", RespuestasVoz.cuantoFalta(1_500.0, 180.0))
        assertEquals("faltan veintiún kilómetros, veintiún minutos", RespuestasVoz.cuantoFalta(21_100.0, 1_260.0))
    }

    @Test
    fun `cuanto falta en metros redondeados`() {
        assertEquals("faltan ochocientos metros, dos minutos", RespuestasVoz.cuantoFalta(800.0, 90.0))
        assertEquals("faltan trescientos metros, un minuto", RespuestasVoz.cuantoFalta(320.0, 55.0))
        assertEquals("faltan cincuenta metros, menos de un minuto", RespuestasVoz.cuantoFalta(47.0, 5.0))
        assertEquals("faltan diez metros, menos de un minuto", RespuestasVoz.cuantoFalta(3.0, 1.0))
        assertEquals("falta un kilómetro, un minuto", RespuestasVoz.cuantoFalta(975.0, 60.0))
    }

    @Test
    fun `cuanto falta con mas de una hora`() {
        assertEquals("faltan cuarenta kilómetros, una hora", RespuestasVoz.cuantoFalta(40_000.0, 3_600.0))
        assertEquals(
            "faltan cuarenta kilómetros, una hora y siete minutos",
            RespuestasVoz.cuantoFalta(40_000.0, 4_020.0),
        )
        assertEquals(
            "faltan cien kilómetros, dos horas y veintiún minutos",
            RespuestasVoz.cuantoFalta(100_000.0, 8_460.0),
        )
    }

    @Test
    fun `hora de llegada como se dice en espanol`() {
        assertEquals("llegas a las cuatro y diez", RespuestasVoz.horaLlegada(16, 10))
        assertEquals("llegas a la una en punto", RespuestasVoz.horaLlegada(13, 0))
        assertEquals("llegas a la una y cuarto", RespuestasVoz.horaLlegada(13, 15))
        assertEquals("llegas a las diez y media", RespuestasVoz.horaLlegada(22, 30))
        assertEquals("llegas a las diez menos cuarto", RespuestasVoz.horaLlegada(9, 45))
        assertEquals("llegas a las diez menos diez", RespuestasVoz.horaLlegada(9, 50))
        assertEquals("llegas a la una menos diez", RespuestasVoz.horaLlegada(12, 50))
        assertEquals("llegas a las doce y cinco", RespuestasVoz.horaLlegada(0, 5))
        assertEquals("llegas a las doce menos cinco", RespuestasVoz.horaLlegada(23, 55))
        assertEquals("llegas a las dos en punto", RespuestasVoz.horaLlegada(14, 0))
        assertEquals("llegas a las doce en punto", RespuestasVoz.horaLlegada(12, 0))
    }

    @Test
    fun `limite de la via o no lo se`() {
        assertEquals("el límite es noventa", RespuestasVoz.limite(90))
        assertEquals("el límite es treinta", RespuestasVoz.limite(30))
        assertEquals("no lo sé", RespuestasVoz.limite(null))
    }

    @Test
    fun `donde estoy con punto kilometrico lo dice en palabras tras la carretera`() {
        assertEquals("estás en la Ma diez, kilómetro veintitrés", RespuestasVoz.dondeEstoy(null, "Ma-10", 23.2))
        assertEquals(
            "estás en la Ma once, carretera de Sóller, kilómetro treinta y uno",
            RespuestasVoz.dondeEstoy("carretera de Sóller", "Ma-11", 30.6),
        )
        assertEquals("estás en la Ma diez, kilómetro cero", RespuestasVoz.dondeEstoy(null, "Ma-10", 0.2))
        assertEquals("estás en la Ma diez, kilómetro cien", RespuestasVoz.dondeEstoy(null, "Ma-10", 99.6))
        // sin carretera no hay de qué dar el kilómetro; un valor absurdo se calla
        assertEquals("estás en Carrer Major", RespuestasVoz.dondeEstoy("Carrer Major", null, 5.0))
        assertEquals("estás en la Ma diez", RespuestasVoz.dondeEstoy(null, "Ma-10", Double.NaN))
        assertEquals("estás en la Ma diez", RespuestasVoz.dondeEstoy(null, "Ma-10", -3.0))
    }

    @Test
    fun `donde estoy con carretera y nombre`() {
        assertEquals(
            "estás en la Ma once, carretera de Sóller",
            RespuestasVoz.dondeEstoy("carretera de Sóller", "Ma-11"),
        )
        assertEquals(
            "estás en la Ma dos mil ciento cuarenta y uno",
            RespuestasVoz.dondeEstoy(null, "Ma-2141"),
        )
        assertEquals("estás en la Ma trece a", RespuestasVoz.dondeEstoy(null, "Ma-13A"))
        assertEquals("estás en la Ma trece", RespuestasVoz.dondeEstoy(null, "Ma-13;Ma-13A"))
        assertEquals("estás en Carrer Major", RespuestasVoz.dondeEstoy("Carrer Major", null))
        assertEquals("estás en la Ma once", RespuestasVoz.dondeEstoy(" ", "Ma-11"))
        assertEquals("no lo sé", RespuestasVoz.dondeEstoy(null, null))
        assertEquals("no lo sé", RespuestasVoz.dondeEstoy("", " "))
    }

    @Test
    fun `como voy con el tiempo y la diferencia con el mejor`() {
        assertEquals("uno veinte, tres segundos por delante", RespuestasVoz.comoVoy(80_000, -3_000))
        assertEquals("uno veinte, tres segundos por detrás", RespuestasVoz.comoVoy(80_000, 3_000))
        assertEquals("uno veinte", RespuestasVoz.comoVoy(80_000, null))
        assertEquals("cuarenta y cinco segundos", RespuestasVoz.comoVoy(45_000, null))
        assertEquals("uno cero cinco, un segundo por detrás", RespuestasVoz.comoVoy(65_000, 1_000))
        assertEquals("dos minutos, igual que el mejor", RespuestasVoz.comoVoy(120_000, 200))
        assertEquals("dos minutos, igual que el mejor", RespuestasVoz.comoVoy(120_000, -400))
        assertEquals("un minuto", RespuestasVoz.comoVoy(60_000, null))
    }

    @Test
    fun `confirmaciones breves de modo volumen y zoom`() {
        assertEquals("modo notas", RespuestasVoz.modo(ModoCopiloto.NOTAS))
        assertEquals("solo avisos", RespuestasVoz.modo(ModoCopiloto.SOLO_ALERTAS))
        assertEquals("modo completo", RespuestasVoz.modo(ModoCopiloto.COMPLETO))
        assertEquals(null, RespuestasVoz.modo(ModoCopiloto.SILENCIO))
        assertEquals("volumen ciento diez", RespuestasVoz.volumen(110, subir = true, alLimite = false))
        assertEquals("volumen máximo", RespuestasVoz.volumen(150, subir = true, alLimite = true))
        assertEquals("volumen mínimo", RespuestasVoz.volumen(50, subir = false, alLimite = true))
        assertEquals("zoom más cerca", RespuestasVoz.zoom(acercar = true, alLimite = false))
        assertEquals("zoom más lejos", RespuestasVoz.zoom(acercar = false, alLimite = false))
        assertEquals("zoom máximo", RespuestasVoz.zoom(acercar = true, alLimite = true))
        assertEquals("zoom mínimo", RespuestasVoz.zoom(acercar = false, alLimite = true))
    }

    @Test
    fun `la ayuda lee cinco ordenes`() {
        assertEquals("puedes decir: cuánto falta, qué límite hay, cómo voy, repite o silencio", RespuestasVoz.AYUDA)
    }

    @Test
    fun `llevandote a un lugar con el tiempo en palabras`() {
        assertEquals("llevándote a Sóller, veinticinco minutos", RespuestasVoz.llevandoteA("Sóller", 1_500.0))
        assertEquals("llevándote a casa, una hora y siete minutos", RespuestasVoz.llevandoteA("casa", 4_020.0))
    }

    @Test
    fun `el mas cercano dice distancia y municipio y pregunta`() {
        assertEquals(
            "la gasolinera más cercana está a tres kilómetros, en Sóller. ¿Vamos?",
            RespuestasVoz.masCercano(TipoLugar.GASOLINERA, 3_000.0, "Sóller"),
        )
        assertEquals(
            "el hospital más cercano está a trescientos cincuenta metros. ¿Vamos?",
            RespuestasVoz.masCercano(TipoLugar.HOSPITAL, 340.0, null),
        )
        assertEquals("no encuentro ninguna farmacia cerca", RespuestasVoz.sinCercano(TipoLugar.FARMACIA))
        assertEquals("no encuentro ningún hospital cerca", RespuestasVoz.sinCercano(TipoLugar.HOSPITAL))
    }

    @Test
    fun `parada y lugar no encontrado`() {
        assertEquals("parada añadida: Inca", RespuestasVoz.paradaAnadida("Inca"))
        assertEquals("no encuentro Sóller", RespuestasVoz.lugarNoEncontrado("Sóller"))
    }
}
