# Pasos manuales

> Propósito: lo que solo puede hacer el usuario, y cuándo hace falta · Audiencia: Joan · Actualizado: 2026-10-28 · Relacionado: [obtainium.md](obtainium.md), [play.md](play.md)

Marca `[x]` al terminar cada paso. Claude nunca lee ni imprime los valores de `~/.rallycopilot/`.

## Antes de la primera release (GitHub + Obtainium)
- [x] **Keystore nueva, propia de Rally Copilot** (no la de Sous Chef): `python -I scripts/crear-keystore.py`. Crea
      `~/.rallycopilot/rallycopilot-release.jks` y `release-signing.properties` sin mostrar valores. Si ya existe, no
      la toca. **Haz copia de seguridad** de esa carpeta: sin la clave no se pueden publicar actualizaciones.
- [x] **Secretos de firma en GitHub**: `node scripts/subir-secretos-release.mjs`. Sube `ANDROID_KEYSTORE_BASE64`,
      `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` y `ANDROID_KEY_PASSWORD` a `trombonet12/Rally-Copilot` por stdin.
- [x] **Repo público**: lo hace Claude con tu permiso (`gh repo edit --visibility public`), después de comprobar que
      no hay secretos en el historial.
- [x] **Push a `main`**: lo decides tú; dispara el gate y, si cambia `version.properties`, la release.
- [ ] **Obtainium** en el móvil: ver [obtainium.md](obtainium.md#configurar-obtainium-una-vez).

## Material para la voz (cuando lo tengas; se usa en H11)
- [ ] Audios y vídeos de Luis Moya en `voice-lab/data/raw/moya/` y de tramos reales en `voice-lab/data/raw/tramos/`
      (ver [voice-lab/README.md](../voice-lab/README.md)). No se versionan. Se usará solo material existente.
- [ ] Autorización de Luis Moya para clonar su voz, por escrito (no hace falta subirla al repo).

## Móvil (cuando la app esté avanzada; Claude te avisará)
- [ ] Depuración USB activada y el móvil listado en `adb devices`.
- [ ] Permitir ubicación precisa y notificaciones al arrancar la primera navegación.
- [ ] Si LineageOS limita apps en segundo plano, quitar Rally Copilot de la optimización de batería.

## Más adelante: Google Play (pospuesto)
- [ ] Crear la app en Play Console, primera subida manual del AAB, cuenta de servicio y variable
      `PLAY_HABILITADO=true`. Ver [play.md](play.md).
