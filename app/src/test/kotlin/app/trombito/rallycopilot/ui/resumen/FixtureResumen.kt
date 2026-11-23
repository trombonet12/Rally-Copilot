package app.trombito.rallycopilot.ui.resumen

import app.trombito.rallycopilot.domain.resumen.ViajeResumido
import app.trombito.rallycopilot.motor.analisis.EscalaVelocidad
import app.trombito.rallycopilot.motor.analisis.Llegada
import app.trombito.rallycopilot.motor.analisis.ResumenViaje
import app.trombito.rallycopilot.motor.analisis.SegmentoMapa
import app.trombito.rallycopilot.motor.analisis.TramoHecho
import app.trombito.rallycopilot.motor.analisis.ViaRecorrida
import app.trombito.rallycopilot.motor.geo.PuntoGeo

/** Un viaje de ejemplo (31,9 km en 40 min, 5 min antes de lo previsto) para las pruebas del resumen. */
internal object FixtureResumen {
    val segmentos =
        listOf(
            SegmentoMapa(listOf(PuntoGeo(39.60, 2.90), PuntoGeo(39.61, 2.91)), 0.0, 100.0, 40.0),
            SegmentoMapa(listOf(PuntoGeo(39.61, 2.91), PuntoGeo(39.62, 2.93)), 100.0, 200.0, 80.0),
        )

    val vias =
        listOf(
            ViaRecorrida("Ma-10", "Carretera de Valldemossa", 12_400.0, 62.0, 58.0, 6.9),
            ViaRecorrida(null, "Camí de Sa Calobra", 800.0, 30.0, null, null),
            ViaRecorrida("Ma-2141", null, 5_000.0, 40.0, 50.0, -20.0),
        )

    val tramos =
        listOf(
            TramoHecho("Ma-10 Valldemossa", 28_000L, -2_000L, esRecord = true),
            TramoHecho("Ma-2141 Escorca", 31_000L, 1_000L, esRecord = false),
            TramoHecho("Sa Calobra", 40_000L, null, esRecord = false),
        )

    fun resumen(
        previstoS: Long? = 2_700L,
        vias: List<ViaRecorrida> = this.vias,
        tramos: List<TramoHecho> = this.tramos,
        segmentos: List<SegmentoMapa> = this.segmentos,
        conclusiones: List<String> = listOf("Récord nuevo en Ma-10 Valldemossa", "Llegaste 5 min antes de lo previsto"),
    ) = ResumenViaje(
        distanciaM = 31_900.0,
        tiempoTotalS = 2_400L,
        tiempoEnMarchaS = 2_100L,
        mediaEnMarchaKmh = 54.7,
        velocidadMaxKmh = 98.4,
        llegada = Llegada(previstoS, previstoS?.let { 2_400L - it }),
        segmentos = segmentos,
        escala = EscalaVelocidad.de(segmentos.map { it.velocidadKmh }),
        vias = vias,
        tramos = tramos,
        records = tramos.count { it.esRecord },
        conclusiones = conclusiones,
    )

    fun viaje(
        resumen: ResumenViaje = resumen(),
        destino: String? = "Sóller",
    ) = ViajeResumido(recorridoId = 7L, inicioMs = 1_763_900_000_000L, destinoNombre = destino, resumen = resumen)
}
