---
name: session-start
description: Al empezar una sesión o retomar trabajo: leer ESTADO.md, MEMORIA.md, el hito activo de ROADMAP.md y QUESTIONS.md, ver git y verificar el entorno; resumen ≤ 10 líneas. No para cerrar (session-handoff).
allowed-tools: Read, Grep, Glob, Bash(git status*), Bash(git log*), Bash(node --version)
---

# session-start

Carga el contexto mínimo para trabajar sin repetir investigación y entrega un resumen corto antes de actuar.

## Cuándo usarla
- Al empezar una sesión, tras `/clear` o compactación, o al retomar un trabajo a medias.
- Antes de lanzar subagentes: el estado real manda sobre la memoria de la conversación.

## Cuándo NO usarla
- Cerrar o pausar la sesión → usa `session-handoff`.
- Preparar el encargo de un subagente → usa `delegate-task`.
- Subagentes: no la ejecutan; reciben el contexto en su brief.

## Precondiciones
- Directorio de trabajo en la raíz del repo. Solo lectura: no se modifica ni instala nada.

## Pasos
1. Leer `docs/ESTADO.md` entero; extraer Hito, Estado, Pendiente (incluido `§ Pendiente del usuario`), Problemas conocidos y **Próxima acción exacta**.
2. Leer `docs/MEMORIA.md` (preferencias del usuario entre sesiones): mandan sobre cualquier costumbre heredada.
3. Localizar el hito activo en `ROADMAP.md` (`## Hn`) y leer su Alcance y Criterios de aceptación.
4. Leer `QUESTIONS.md`: dudas abiertas que bloquean el hito y las ya respondidas por el usuario.
5. Leer solo las secciones de `docs/DECISIONES.md` que el hito cita.
6. Git: `git status --short` y `git log -5 --oneline`. Contrastar con "Último commit" de ESTADO.md; discrepancias o cambios sin commitear no explicados se marcan. Ver también commits locales sin publicar (`git status -sb`): el push solo con permiso del usuario.
7. Entorno mínimo, según lo que toque la próxima acción:
   - Siempre: `node --version` (los `scripts/*.mjs` lo usan).
   - Android o `:motor`: JDK 21 visible para Gradle (`~/.gradle/gradle.properties`) y SDK (`ANDROID_HOME` o `sdk.dir` en `local.properties`); comprobar solo su existencia, nunca imprimir valores.
   - Emulador: AVD `trombito-36` (`emulator -list-avds`) si la acción lo necesita.
8. Si falta algo, no instalar ni arreglar: listar el bloqueo y quién lo resuelve.
9. Responder con un resumen **≤ 10 líneas**: hito y estado · último commit y git · próxima acción exacta · dudas abiertas que bloquean · entorno (OK o qué falta). Después, empezar por la próxima acción o pedir confirmación si hay discrepancias.

## Validaciones / tests
- El resumen cabe en 10 líneas y la próxima acción citada es literalmente la de `docs/ESTADO.md`.
- No ejecutar el gate completo aquí (es de cierre de tarea); solo si ESTADO.md lo marca como primer paso.

## Definition of Done
- [ ] ESTADO.md, MEMORIA.md, hito activo, QUESTIONS.md y decisiones citadas leídos (o su ausencia anotada).
- [ ] `git status` y `git log -5` contrastados con ESTADO.md.
- [ ] Entorno mínimo verificado según el ámbito; sin instalar nada.
- [ ] Resumen ≤ 10 líneas entregado antes de tocar código.

## Docs a actualizar
- Ninguno. Si ESTADO.md está desfasado, corrígelo con `session-handoff`.
