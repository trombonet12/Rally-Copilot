package app.trombito.rallycopilot.motor.ruta

/** Cómo [SeguidorRuta] convierte el tiempo de Valhalla de una [fraccion] (0–1) de una maniobra en tiempo previsto. */
fun interface AjusteTiempo {
    fun tiempoS(
        maniobra: Maniobra,
        fraccion: Double,
    ): Double

    companion object {
        /** El tiempo de Valhalla repartido por progreso, sin corrección. */
        val VALHALLA = AjusteTiempo { maniobra, fraccion -> maniobra.tiempoS * fraccion }
    }
}
