# Vídeo promocional

Vídeo horizontal de la ficha de Google Play (1920×1080, 30 fps, ~32 s) hecho con [Remotion](https://www.remotion.dev).
Procedimiento completo: skill `video-promo` (`.claude/skills/video-promo/SKILL.md`).

```
node scripts/video/promo.mjs              # renderiza build/promo/rally-copilot-promo-1920x1080.mp4
node scripts/video/promo.mjs --grabar     # graba antes los clips en el emulador (scripts/video/grabar-clips.sh)
node scripts/video/promo.mjs --control    # además, 6 stills y hoja de contacto en build/promo/
cd video && npm run studio                # vista previa interactiva
cd video && npm run typecheck
```

- Textos, tiempos, bpm, clips y música: solo en `src/guion.ts`. Colores: `src/paleta.ts` (de `ui/theme/Color.kt`).
- Clips (`public/clips/`) y MP4 (`build/promo/`) no se versionan. Render local, nunca en el CI.
- Música: `public/musica/` con su `LICENCIA.md` (CC0).
- **Licencia de Remotion:** gratuita para particulares y empresas de hasta 3 personas
  (<https://www.remotion.dev/license>); por encima hace falta licencia de empresa.
