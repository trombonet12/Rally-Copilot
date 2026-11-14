package app.trombito.rallycopilot.ui.tramos

import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ParcialCrono
import app.trombito.rallycopilot.domain.tramos.ResultadoCrono
import app.trombito.rallycopilot.domain.tramos.SubtramoVivo
import app.trombito.rallycopilot.domain.tramos.TipoCorte
import app.trombito.rallycopilot.domain.tramos.TramoCronometrado
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.PosicionFantasma

/** Instante (ms epoch) en que se publicó el estado de prueba. */
internal const val AHORA_PRUEBA_MS = 1_000_000L

/** Tramo de 4 km con 3 cortes de sector (4 sectores) y 2 microsectores; la meta cae 10 m antes del final. */
internal fun tramoDePrueba() =
    TramoCronometrado(
        id = "t1",
        nombre = null,
        longitudM = 4_000.0,
        metaM = 3_990.0,
        sectoresM = listOf(1_000.0, 2_000.0, 3_000.0),
        microsectoresM = listOf(1_500.0, 3_500.0),
    )

internal fun sector(
    numero: Int,
    diferenciaMs: Long?,
    duracionMs: Long = 40_000L,
) = ParcialCrono(
    TipoCorte.SECTOR,
    numero,
    numero * 1_000.0,
    numero * duracionMs,
    duracionMs,
    diferenciaMs,
    diferenciaMs,
)

internal fun micro(
    numero: Int,
    diferenciaMs: Long?,
) = ParcialCrono(TipoCorte.MICROSECTOR, numero, numero * 1_500.0, numero * 60_000L, 60_000L, diferenciaMs, diferenciaMs)

/** Tramo conocido (con mejor pasada) en marcha: 2 min 30,4 s y +1,2 s sobre la mejor. */
internal fun cronoConocido(
    transcurridoMs: Long = 150_400L,
    deltaMs: Long? = 1_200L,
    parciales: List<ParcialCrono> = emptyList(),
    progresoM: Double = 1_800.0,
) = CronoEnCurso(
    tramo = tramoDePrueba(),
    transcurridoMs = transcurridoMs,
    actualizadoEnMs = AHORA_PRUEBA_MS,
    progresoM = progresoM,
    deltaMs = deltaMs,
    parciales = parciales,
    ultimaMarca = parciales.lastOrNull(),
    hayMejorPasada = true,
)

/** Conducción sin tramo: solo el tiempo desde Conducir. */
internal fun cronoSinTramo(transcurridoMs: Long = 95_000L) =
    CronoEnCurso(tramo = null, transcurridoMs = transcurridoMs, actualizadoEnMs = AHORA_PRUEBA_MS)

/** Conducir pulsado y el coche aún sin salir: sin tiempo que contar. */
internal fun cronoListo() =
    CronoEnCurso(tramo = null, transcurridoMs = 0L, actualizadoEnMs = AHORA_PRUEBA_MS, enMarcha = false)

/** Tramo guardado pero sin mejor pasada con la que comparar. */
internal fun cronoSinReferencia() = cronoConocido(deltaMs = null).copy(hayMejorPasada = false)

internal fun cronoTerminado(resultado: ResultadoCrono) =
    cronoConocido(transcurridoMs = resultado.totalMs, deltaMs = resultado.deltaMs).copy(
        progresoM = 3_990.0,
        resultado = resultado,
    )

/**
 * Conducción sin tramo completo pero con un subtramo de 2,4 km en marcha (52,3 s): con referencia lleva cortes de
 * sector y microsector del tramo de la referencia, los parciales ya cerrados y el fantasma; con [deltaMs] null no hay
 * referencia (solo la barra simple).
 */
internal fun cronoConSubtramo(
    deltaMs: Long? = -3_100L,
    parciales: List<ParcialCrono> = listOf(sector(1, -300L)),
) = cronoSinTramo().copy(
    subtramo =
        SubtramoVivo(
            recorridoM = 2_400.0,
            deltaMs = deltaMs,
            transcurridoMs = 52_300L,
            longitudM = if (deltaMs == null) 0.0 else 4_000.0,
            sectoresM = if (deltaMs == null) emptyList() else listOf(1_000.0, 2_000.0, 3_000.0),
            microsectoresM = if (deltaMs == null) emptyList() else listOf(1_500.0, 3_500.0),
            parciales = if (deltaMs == null) emptyList() else parciales,
            ultimaMarca = if (deltaMs == null) null else parciales.lastOrNull(),
            fantasma = if (deltaMs == null) null else PosicionFantasma(PuntoGeo(39.7, 2.9), 90.0, 2_500.0),
        ),
)
