---
name: bugfix-flow
description: Corregir un bug: reproducir, test que falla, arreglo, regresión y causa raíz documentada; no para tests intermitentes (test-debug-flaky) ni para features nuevas (design-feature).
argument-hint: "[descripción-del-bug]"
allowed-tools: Read, Grep, Glob, Write, Edit, Bash(./gradlew *), Bash(node scripts/*)
---

# bugfix-flow

Flujo para el bug `$ARGUMENTS`. Lo ejecuta un agente de ingeniería [sonnet]; si no hay uno cargado: `general-purpose` + modelo sonnet.

## Cuándo usarla
- Hay un comportamiento incorrecto reproducible que debe corregirse (app, motor `motor/`, datos o esquema Room).

## Cuándo NO usarla
- Intermitente (pasa a veces) → usa `test-debug-flaky`.
- Comportamiento nuevo o cambio de diseño → usa `design-feature`.
- Una nota mal generada por calibración (grado discutible, no un fallo de código) → revisión con el agente `copiloto-reviewer` y registro en `docs/DECISIONES.md`.

## Precondiciones
- `$ARGUMENTS` con descripción, pasos y resultado esperado vs. obtenido. Si falta, pedirlos y parar.
- Leer `docs/ESTADO.md` y el hito activo de `ROADMAP.md`; el bug debe caer dentro de él o registrarse si no.

## Pasos
1. Reproducir: localizar el fallo en local con el menor caso posible (test existente, traza con `gps-replay`, dispositivo). Anotar el comando exacto.
2. Escribir primero un test que falle por la razón correcta (ver `test-write`; dobles con `test-create-double`). Ejecutarlo y confirmar el rojo:
   - Motor: `./gradlew :motor:test --tests "<clase>"`.
   - Android: `./gradlew testDebugUnitTest --tests "<clase>"`.
3. Buscar la causa raíz (no el síntoma): leer el flujo y contrastar hipótesis con el test; no parchear con condiciones ad hoc.
4. Corregir con el cambio mínimo. El test debe pasar (verde).
5. Regresión: gate qa fast. Si otro test cae, entender por qué antes de tocarlo; nunca desactivarlo.
6. Documentar la causa raíz: mensaje de commit `fix(<ámbito>): <resumen>` con causa, arreglo y test añadido (lo hace el agente principal) y entrada en `docs/ESTADO.md` vía `session-handoff`.
7. Si el bug revela un hueco más amplio (validación, permisos, migración Room), anotarlo como pendiente en ESTADO.md; no ampliar el alcance.

## Validaciones / tests
- El test nuevo falla antes del arreglo y pasa después (evidenciar ambos).
- Gate qa fast (skill `qa`).

## Definition of Done
- [ ] Bug reproducido con comando documentado.
- [ ] Test de regresión añadido, visto en rojo y luego en verde.
- [ ] Cambio mínimo, sin tests desactivados ni errores ignorados.
- [ ] Causa raíz en el commit y en `docs/ESTADO.md`.
- [ ] Gate qa fast en verde.

## Docs a actualizar
- docs/ESTADO.md (skill `session-handoff`) · docs de la feature afectada si el comportamiento esperado se aclara.
