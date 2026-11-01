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
import app.trombito.rallycopilot.motor.datos.ManifestDatos
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
        /** La siembra de los datos del APK termina antes de comparar con el manifest remoto. */
        private val siembra: EsperaDatosSembrados = EsperaDatosSembrados.Ninguna,
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
            siembra.esperar()
            val publicado = http.leerManifestRemoto(config)
            val completo = publicado.manifest
            val instalados = dao.archivos()
            // El satélite (z16 + z17, ≈1,5 GB) solo lo baja el usuario cuando ya tiene lo sembrado: ni una
            // actualización automática ni «Descargar mapas» tras una siembra fallida (faltan los datos base) estrenan
            // un archivo de satélite; sí actualizan los que ya hay instalados.
            val faltaBase = IDS_SEMBRADOS.any { id -> instalados.none { it.id == id } }
            val soloBase = faltaBase || (automatico && instalados.isNotEmpty())
            val idsInstalados = instalados.map { it.id }.toSet()
            val remoto =
                if (soloBase) {
                    completo.copy(
                        archivos =
                            completo.archivos.filterNot {
                                it.id.startsWith(
                                    PREFIJO_SATELITE,
                                ) && it.id !in idsInstalados
                            },
                    )
                } else {
                    completo
                }
            val limite = (ajustes.ajustes()?.aDominio() ?: AjustesMapas()).limiteBytes
            // Sin el manifest extra no se sabe nada de sus archivos: lo instalado de ahí se deja como está.
            val locales =
                instalados
                    .filter { publicado.extraDisponible || !it.id.esSateliteExtra(completo) }
                    .map { ArchivoLocal(it.id, it.sha256, it.bytes) }
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

/** Los ids de los archivos de satélite empiezan así (`satelite`, y los que añada el z17). */
internal const val PREFIJO_SATELITE = "satelite"

/** Las teselas del satélite instaladas son `pnoa-mallorca*.pmtiles` (z16 o z17: el nombre cambia con el paquete). */
internal const val PREFIJO_TESELAS_SATELITE = "pnoa-mallorca"

/** Un id de satélite que el manifest principal no lista: viene del extra (`satelite-z17`). */
private fun String.esSateliteExtra(principal: ManifestDatos) =
    startsWith(PREFIJO_SATELITE) && principal.archivo(this) == null

internal fun ArchivoDatos.aEntidad() =
    ArchivoInstaladoEntity(id = id, nombre = nombre, sha256 = sha256, bytes = bytes, version = version)
