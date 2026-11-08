package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.vias.ProveedorPuntoKilometrico
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.edicion.TipoPregunta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.HitoKm
import app.trombito.rallycopilot.motor.vias.IndicePuntosKm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RespondedorPreguntasPkTest {
    /** La fijación falsa está en 39.7, 2.9; los hitos se colocan hacia el este a la distancia pedida. */
    private val aqui = PuntoGeo(39.7, 2.9)
    private val ubicacion = UbicacionFalsa()
    private val vias = ViasFalsas(ViaActual("carretera de Sóller", "Ma-10", 90))

    private fun hito(
        ref: String,
        km: Double,
        metrosAlEste: Double,
    ) = HitoKm(ref, km, PuntoGeo(aqui.lat, aqui.lon + metrosAlEste / METROS_POR_GRADO_LON))

    private fun respondedor(puntosKm: ProveedorPuntoKilometrico = ProveedorPuntoKilometrico.Ninguno) =
        RespondedorPreguntas(
            estado = { EstadoNavegacion.Inactiva },
            crono = CronoFalso(),
            vias = vias,
            ubicacion = ubicacion,
            ahoraMs = { 0L },
            puntosKm = puntosKm,
        )

    private fun indice(vararg hitos: HitoKm): ProveedorPuntoKilometrico {
        val indice = IndicePuntosKm(hitos.toList())
        return ProveedorPuntoKilometrico { ref, posicion -> indice.buscar(ref, posicion) }
    }

    private fun dondeEstoy(respondedor: RespondedorPreguntas) =
        runBlocking { respondedor.responder(TipoPregunta.DONDE_ESTOY) }

    @Test
    fun `con punto kilometrico dice la carretera, su nombre y el kilometro en palabras`() {
        val r = respondedor(indice(hito("Ma-10", 23.0, 30.0), hito("Ma-11", 5.0, 5.0)))

        assertEquals("estás en la Ma diez, carretera de Sóller, kilómetro veintitrés", dondeEstoy(r))
    }

    @Test
    fun `sin archivo de hitos responde como antes, sin kilometro`() {
        assertEquals("estás en la Ma diez, carretera de Sóller", dondeEstoy(respondedor()))
    }

    @Test
    fun `con hitos solo de otra carretera no dice kilometro`() {
        val r = respondedor(indice(hito("Ma-13", 40.0, 10.0), hito("Ma-1", 3.0, 20.0)))

        assertEquals("estás en la Ma diez, carretera de Sóller", dondeEstoy(r))
    }

    @Test
    fun `interpola entre dos hitos`() {
        // A 400 m del hito 23 y a 600 m del 24: kilómetro 23,4, «veintitrés».
        val tres = respondedor(indice(hito("Ma-10", 23.0, -400.0), hito("Ma-10", 24.0, 600.0)))
        assertEquals("estás en la Ma diez, carretera de Sóller, kilómetro veintitrés", dondeEstoy(tres))

        // A 700 m del 23 y a 300 m del 24: kilómetro 23,7, «veinticuatro».
        val cuatro = respondedor(indice(hito("Ma-10", 23.0, -700.0), hito("Ma-10", 24.0, 300.0)))
        assertEquals("estás en la Ma diez, carretera de Sóller, kilómetro veinticuatro", dondeEstoy(cuatro))
    }

    @Test
    fun `sin hito cercano no dice kilometro`() {
        val r = respondedor(indice(hito("Ma-10", 23.0, 5_000.0)))

        assertEquals("estás en la Ma diez, carretera de Sóller", dondeEstoy(r))
    }

    @Test
    fun `sin via no lo sabe aunque haya hitos`() {
        vias.via = null
        val r = respondedor(indice(hito("Ma-10", 23.0, 10.0)))

        assertEquals("no lo sé", dondeEstoy(r))
    }

    @Test
    fun `sin fijacion a tiempo no lo sabe`() {
        ubicacion.fijacion = null
        val r = respondedor(indice(hito("Ma-10", 23.0, 10.0)))

        assertEquals("no lo sé", dondeEstoy(r))
    }

    private companion object {
        /** Metros por grado de longitud a 39,7 grados de latitud. */
        val METROS_POR_GRADO_LON = Math.toRadians(1.0) * PuntoGeo.RADIO_TIERRA_M * Math.cos(Math.toRadians(39.7))
    }
}
