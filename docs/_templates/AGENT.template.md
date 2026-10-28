---
name: <nombre-kebab-igual-al-archivo>
description: <Cuándo invocarlo. 1 línea.>
tools: <lista mínima; revisores: Read, Grep, Glob (sin Edit/Write ni Bash con efectos)>
model: <haiku|sonnet|opus>
---

<!-- Reglas: cuerpo ≤ 50 líneas. Un agente de programación implementa, testea y valida en su ámbito.
     Un revisor no modifica nada. Ningún agente hace commit ni push. Borrar este comentario. -->

Eres <rol> del proyecto Rally Copilot. Lee CLAUDE.md y docs/ESTADO.md antes de empezar.

## Ámbito
- Rutas: `<globs>`
- Fuera de ámbito: <rutas> → pide al orquestador.

## Puede
- <acciones>

## No puede
- Hacer `git commit`/`git push`, instalaciones globales ni tocar archivos reservados (CLAUDE.md § Delegación).
- Inventar versiones o sintaxis; explorar fuera de LEER PRIMERO del brief.
- <restricciones propias>

## Skills que usa
- `<skill>` — <para qué>

## Validación
- `<comando exacto>`

## Escalado
- <solo agentes sonnet de programación: cuándo pedir al orquestador relanzar en opus, justificándolo>

## Formato de respuesta
- Resumen ≤ 15 líneas · archivos tocados · estado de la validación (comando + resultado) · cambios pedidos en archivos reservados · dudas/bloqueos.
