package app.trombito.rallycopilot.domain.ritmo

import app.trombito.rallycopilot.motor.ruta.EstimadorLlegada
import app.trombito.rallycopilot.motor.ruta.PerfilRitmo
import app.trombito.rallycopilot.motor.ruta.Ruta

/** De dónde sale el [PerfilRitmo] con que se estiman los tiempos de las rutas (lo aprendido de los recorridos). */
interface ProveedorPerfilRitmo {
    /**
     * El perfil actual. Se cachea y solo se recalcula si han cambiado los recorridos terminados; la primera vez puede
     * tardar (analiza todos los recorridos), así que quien no pueda esperar debe acotarla con un plazo y usar el
     * [PerfilRitmo] de partida mientras tanto.
     */
    suspend fun perfil(): PerfilRitmo
}

/** Sin histórico: siempre el perfil de partida (Valhalla × factor personal por defecto). */
object SinPerfilRitmo : ProveedorPerfilRitmo {
    override suspend fun perfil(): PerfilRitmo = PerfilRitmo()
}

/**
 * Tiempo estimado (s) de recorrer la ruta entera según el [perfil], sin corrección en vivo: lo que enseña la vista
 * previa. Es lo mismo que suma [app.trombito.rallycopilot.motor.ruta.SeguidorRuta] al empezar el guiado.
 */
fun Ruta.tiempoEstimadoS(perfil: PerfilRitmo): Double {
    val ajuste = EstimadorLlegada(perfil).comoAjuste()
    if (maniobras.isEmpty()) return tiempoS * perfil.factorPersonal.coerceIn(MIN_FACTOR, MAX_FACTOR)
    return maniobras.sumOf { ajuste.tiempoS(it, 1.0) }
}

private const val MIN_FACTOR = PerfilRitmo.FACTOR_PERSONAL_MIN
private const val MAX_FACTOR = PerfilRitmo.FACTOR_PERSONAL_MAX
