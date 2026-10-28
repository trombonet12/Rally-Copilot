package app.trombito.rallycopilot.data.datosmapa

import android.util.Log
import app.trombito.rallycopilot.data.db.AjustesMapasDao
import app.trombito.rallycopilot.data.db.ArchivoInstaladoEntity
import app.trombito.rallycopilot.data.db.DatosMapaDao
import app.trombito.rallycopilot.data.db.archivos
import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.datos.MotivoError
import app.trombito.rallycopilot.motor.datos.ArchivoDatos
import app.trombito.rallycopilot.motor.datos.ArchivoLocal
import app.trombito.rallycopilot.motor.datos.LectorManifest
import app.trombito.rallycopilot.motor.datos.ManifestInvalido
import app.trombito.rallycopilot.motor.datos.PlanificadorDescarga
import app.trombito.rallycopilot.motor.datos.ProblemaEspacio
import java.io.IOException
import javax.inject.Inject

/** Resultado de una pasada de [ActualizadorDatos]. Los cortes de red no son un resultado: lanzan [IOException]. */
sealed interface ResultadoActualizacion {
    data object Instalado : ResultadoActualizacion

    data class Fallo(
        val motivo: MotivoError,
    ) : ResultadoActualizacion
}

/**
 * Una pasada completa: manifest, plan, descarga verificada de lo que falta e instalación atómica. No sabe nada de
 * WorkManager (lo envuelve [TrabajoDatosMapa]), así que se prueba directamente.
 */
class ActualizadorDatos
    @Inject
    constructor(
        private val dao: DatosMapaDao,
        private val ajustes: AjustesMapasDao,
        private val config: ConfigDatosMapa,
    ) {
        private val http = ClienteHttp(config)
        private val descargador = DescargadorArchivo(http)
        private val instalador = InstaladorDatos(config)

        /**
         * @param automatico true si lo lanzó la comprobación al abrir la app: si ya había datos, deja un aviso.
         * @param alProgreso bytes hechos y bytes totales a descargar (solo lo que falta, no el paquete entero).
         * @throws IOException si se corta la red (los `.part` se conservan para reanudar).
         */
        suspend fun actualizar(
            automatico: Boolean,
            alProgreso: suspend (hechos: Long, totales: Long) -> Unit = { _, _ -> },
        ): ResultadoActualizacion {
            val resultado =
                try {
                    ejecutar(automatico, alProgreso)
                } catch (e: ManifestInvalido) {
                    fallo(MotivoError.MANIFEST_INVALIDO, e)
                } catch (e: HashIncorrectoException) {
                    fallo(MotivoError.HASH_INCORRECTO, e)
                } catch (e: ZipInseguroException) {
                    fallo(MotivoError.DESCONOCIDO, e)
                }
            if (resultado is ResultadoActualizacion.Fallo) registrarError(resultado.motivo)
            return resultado
        }

        suspend fun registrarError(motivo: MotivoError) = dao.establecerError(motivo.name)

        private fun fallo(
            motivo: MotivoError,
            causa: Exception,
        ): ResultadoActualizacion.Fallo {
            Log.w(ETIQUETA_LOG, "Actualización de datos fallida ($motivo)", causa)
            return ResultadoActualizacion.Fallo(motivo)
        }

        private suspend fun ejecutar(
            automatico: Boolean,
            alProgreso: suspend (Long, Long) -> Unit,
        ): ResultadoActualizacion {
            val remoto = LectorManifest.leer(http.texto(config.urlManifest))
            val instalados = dao.archivos()
            val limite = ajustes.ajustes()?.limiteBytes ?: AjustesMapas().limiteBytes
            val locales = instalados.map { ArchivoLocal(it.id, it.sha256, it.bytes) }
            // Lo ya bajado en .nuevo/ (de un intento anterior) ya ocupa disco: no cuenta contra el espacio libre.
            val libres = config.bytesLibres() + instalador.bytesEnCurso(remoto.archivos)
            val plan = PlanificadorDescarga.planificar(remoto, locales, libres, limite)
            val problema =
                when (plan.problema) {
                    ProblemaEspacio.SUPERA_LIMITE -> MotivoError.SUPERA_LIMITE
                    ProblemaEspacio.SIN_ESPACIO_LIBRE -> MotivoError.SIN_ESPACIO
                    null -> null
                }
            if (problema != null) return ResultadoActualizacion.Fallo(problema)

            descargarTodo(plan.aDescargar, plan.bytesADescargar, alProgreso)

            val nombresABorrar = instalados.filter { it.id in plan.aBorrar }.map { it.nombre }
            instalador.instalar(plan.aDescargar, nombresABorrar, remoto.archivos.map { it.nombre })
            dao.registrarInstalacion(
                archivos = plan.aDescargar.map { it.aEntidad() },
                idsBorrados = plan.aBorrar,
                version = remoto.version,
                avisar = automatico && instalados.isNotEmpty() && plan.aDescargar.isNotEmpty(),
            )
            return ResultadoActualizacion.Instalado
        }

        private suspend fun descargarTodo(
            archivos: List<ArchivoDatos>,
            totales: Long,
            alProgreso: suspend (Long, Long) -> Unit,
        ) {
            var completados = 0L
            alProgreso(0, totales)
            for (archivo in archivos) {
                if (instalador.estaVerificado(archivo)) {
                    completados += archivo.bytes
                    alProgreso(completados, totales)
                    continue
                }
                val base = completados
                descargador.descargar(archivo, instalador.parcial(archivo)) { hechos ->
                    alProgreso(base + hechos, totales)
                }
                instalador.marcarVerificado(archivo)
                completados += archivo.bytes
            }
            alProgreso(totales, totales)
        }
    }

private const val ETIQUETA_LOG = "ActualizadorDatos"

internal fun ArchivoDatos.aEntidad() =
    ArchivoInstaladoEntity(id = id, nombre = nombre, sha256 = sha256, bytes = bytes, version = version)
