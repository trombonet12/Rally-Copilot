# kotlinx.serialization: conservar los serializers generados (rutas de navegación y modelos).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.trombito.rallycopilot.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.trombito.rallycopilot.**$$serializer { *; }

# Privacidad: R8 elimina las llamadas a Log.v/d/i en release (posiciones GPS, rutas). Log.w y Log.e se conservan.
# Solo funciona con la optimización de R8 activa (proguard-android-optimize).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
