package app.trombito.rallycopilot.ui.tramos

import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ParcialCrono
import app.trombito.rallycopilot.domain.tramos.ResultadoCrono
import app.trombito.rallycopilot.domain.tramos.TipoCorte
import app.trombito.rallycopilot.domain.tramos.TramoCronometrado

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

/** Tramo guardado pero sin mejor pasada con la que comparar. */
internal fun cronoSinReferencia() = cronoConocido(deltaMs = null).copy(hayMejorPasada = false)

internal fun cronoTerminado(resultado: ResultadoCrono) =
    cronoConocido(transcurridoMs = resultado.totalMs, deltaMs = resultado.deltaMs).copy(
        progresoM = 3_990.0,
        resultado = resultado,
    )
