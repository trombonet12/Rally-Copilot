package app.trombito.rallycopilot.data.datosmapa

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.datos.Complemento

/** Los complementos bajo demanda que los ajustes de conducción piden: la voz Sharvard y el relieve activado. */
fun AjustesConduccion.complementosElegidos(): Set<Complemento> =
    setOfNotNull(
        Complemento.VOZ_SHARVARD.takeIf { voz == VozCopiloto.SHARVARD },
        Complemento.RELIEVE.takeIf { relieve },
    )
