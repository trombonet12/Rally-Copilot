package app.trombito.rallycopilot.ui.perfil

import app.trombito.rallycopilot.data.carreteras.viaFalsa
import app.trombito.rallycopilot.domain.perfil.CarneCarreteras
import app.trombito.rallycopilot.domain.perfil.TemporadaPiloto
import app.trombito.rallycopilot.motor.analisis.CurvasPorGrado
import app.trombito.rallycopilot.motor.analisis.PasadaRegistrada
import app.trombito.rallycopilot.motor.analisis.RecorridoTemporada
import app.trombito.rallycopilot.motor.analisis.Temporada
import app.trombito.rallycopilot.motor.analisis.TramoRegistrado
import app.trombito.rallycopilot.motor.analisis.UsoVia
import app.trombito.rallycopilot.ui.comun.pasadaFalsa
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Lunes 23 de noviembre de 2026, a mediodía UTC: «hoy» de las pruebas de la temporada. */
internal val AHORA_PRUEBA_MS: Long =
    ZonedDateTime.of(2026, 11, 23, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun diaMs(
    dia: Int,
    mes: Int = 11,
): Long = ZonedDateTime.of(2026, mes, dia, 10, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

/**
 * Temporada de prueba: tres viajes en tres semanas seguidas de noviembre de 2026 (120, 100 y 200 km) por la Ma-10, la
 * Ma-2141 y una calle sin referencia («Carrer Major»), y un tramo con dos pasadas que mejora el récord.
 */
internal fun temporadaDePrueba(): TemporadaPiloto {
    val ma10 = viaFalsa(1, "Ma-10", 0, 1_000)
    val ma2141 = viaFalsa(2, "Ma-2141", 0, 1_000)
    val calle = viaFalsa(3, null, 0, 1_000).copy(nombre = "Carrer Major")
    val curvas = CurvasPorGrado(horquillas = 5)
    val recorridos =
        listOf(
            RecorridoTemporada(
                diaMs(3),
                120_000.0,
                curvas,
                listOf(UsoVia(ma10, 5_000.0, 40), UsoVia(ma2141, 1_000.0, 5)),
            ),
            RecorridoTemporada(
                diaMs(10),
                100_000.0,
                curvas,
                listOf(UsoVia(ma10, 5_000.0, 40), UsoVia(calle, 500.0, 2)),
            ),
            RecorridoTemporada(diaMs(17), 200_000.0, curvas, listOf(UsoVia(ma10, 5_000.0, 40))),
        )
    val tramos =
        listOf(
            TramoRegistrado(
                tramoId = "t1",
                nombre = "Tramo de la sierra",
                distanciaM = 5_000.0,
                pasadas =
                    listOf(
                        PasadaRegistrada("1", pasadaFalsa("t1", 250_000L), diaMs(3)),
                        PasadaRegistrada("2", pasadaFalsa("t1", 241_500L), diaMs(17)),
                    ),
            ),
        )
    val temporada = Temporada.de(recorridos, tramos, ZoneOffset.UTC, AHORA_PRUEBA_MS)
    return TemporadaPiloto(temporada, CarneCarreteras.de(recorridos.map { it.usos }))
}
