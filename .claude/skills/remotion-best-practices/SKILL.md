---
name: remotion-best-practices
description: Crear o editar vídeos con Remotion (React): router oficial a las guías de composición, markup, captions, mapas y docs; no graba clips ni exporta (ver video-promo y remotion-render).
metadata:
  version: "4.0.537"
---

# remotion-best-practices

Router oficial de Remotion. Su contenido original está íntegro en [ROUTER.md](ROUTER.md) y enlaza a las guías por tema (`remotion-*/REFERENCE.md`). En este repo el proyecto Remotion vive en `video/` (Remotion 4.0.533 fijado) y el render se hace con `node scripts/video/promo.mjs`.

## Cuándo usarla
- Crear o editar composiciones del vídeo promocional en `video/src`.
- Buscar una API o guía de Remotion (markup, captions, mapas, transiciones, audio, docs).

## Cuándo NO usarla
- Grabar clips del emulador y montaje ffmpeg/Pillow → `video-promo`.
- Solo exportar un vídeo o still → `remotion-render`.
- Duración o dimensiones de un archivo de media → `remotion-multimedia`.

## Precondiciones
- `video/` con `npm install` hecho (usa `package-lock.json`; paquetes `remotion`/`@remotion/*` en la misma versión exacta 4.0.533).
- Las guías sugieren `npx remotion add`, `create-video@latest` o `remotion upgrade`: en este repo añadir o subir dependencias exige la skill `dependency-add`.

## Pasos
1. Leer [ROUTER.md](ROUTER.md) y cargar solo el `REFERENCE.md` del tema necesario.
2. Editar en `video/src` y previsualizar con `npm run studio` (desde `video/`).
3. Renderizar solo si se pide: `node scripts/video/promo.mjs` (o `npm run render` en `video/`).

## Validaciones / tests
- `cd video && npm run typecheck` sin errores.
- La previsualización del Studio carga la composición `Promo`.

## Definition of Done
- [ ] Cambios solo en `video/` (y `scripts/video/` si procede); nada de secretos ni clips versionados.
- [ ] Typecheck en verde y preview revisada.
- [ ] Sin dependencias nuevas fuera de `dependency-add`.

## Docs a actualizar
- `video/README.md` si cambian comandos · docs/ESTADO.md (skill session-handoff)

## Origen
- Repo https://github.com/remotion-dev/skills (espejo de `packages/skills` de remotion-dev/remotion), versión 4.0.537, copiado el 2026-10-20.
- Licencia: la del monorepo, https://github.com/remotion-dev/remotion/blob/main/LICENSE.md (el espejo no tiene LICENSE propia).
- Adaptado al repo: frontmatter (`version` pasa a `metadata`), `description` en español, secciones de la plantilla y cuerpo original movido a `ROUTER.md` sin cambios.
