# Pasos manuales

> Propósito: lo que solo puede hacer el usuario, y cuándo hace falta · Audiencia: Joan · Actualizado: 2026-10-28 · Relacionado: [play.md](play.md)

Marca `[x]` al terminar cada paso. Claude nunca lee ni imprime los valores de `~/.rallycopilot/`.

## Antes de la primera release (cuando quieras publicar la 0.1)
- [ ] **Keystore de subida**: `python -I scripts/crear-keystore.py`. Crea `~/.rallycopilot/rallycopilot-release.jks`
      y `release-signing.properties` sin mostrar valores. **Haz copia de seguridad** de esa carpeta: sin la clave no
      se pueden publicar actualizaciones.
- [ ] **Secretos de firma en GitHub**: `node scripts/subir-secretos-release.mjs` (sube `ANDROID_KEYSTORE_BASE64`,
      `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` y `ANDROID_KEY_PASSWORD` a `trombonet12/Rally-Copilot` por stdin).
- [ ] **Play Console**: crear la app «Rally Copilot» y hacer la **primera subida del AAB a mano** (ver
      [play.md](play.md#una-sola-vez-en-play-console)).
- [ ] **Cuenta de servicio de Play**: invitarla a la app, guardar el JSON en `~/.rallycopilot/` y subirlo con
      `node scripts/subir-secreto-play.mjs`. Variables `PLAY_ESTADO=draft` y, si quieres, `PLAY_PISTA_CERRADA`.
- [ ] **Push a `main`**: lo decides tú; dispara el gate y, si cambia `version.properties`, la release.

## Material para la voz (cuando lo tengas; se usa en H11)
- [ ] Audios y vídeos de Luis Moya en `voice-lab/data/raw/moya/` y de tramos reales en `voice-lab/data/raw/tramos/`
      (ver [voice-lab/README.md](../voice-lab/README.md)). No se versionan.
- [ ] Autorización de Luis Moya para clonar su voz, por escrito (no hace falta subirla al repo).

## Móvil (cuando la app esté avanzada; Claude te avisará)
- [ ] Depuración USB activada y el móvil listado en `adb devices`.
- [ ] Permitir ubicación precisa y notificaciones al arrancar la primera navegación.
- [ ] Si el fabricante mata apps en segundo plano, quitar Rally Copilot de la optimización de batería.
