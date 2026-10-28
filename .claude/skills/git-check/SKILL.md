---
name: git-check
description: Auditar el árbol git (solo lectura) [working-tree|generated|large-files]: cambios sin commitear, generados rastreados, archivos grandes. Para commitear usa git-commit.
argument-hint: "[working-tree|generated|large-files]"
allowed-tools: Read, Grep, Glob, Bash(git status*), Bash(git ls-files*), Bash(git diff*), Bash(git check-ignore*), Bash(git cat-file*)
---

# git-check

Revisión de solo lectura del estado del repo. No modifica archivos ni el índice. Argumento: `$ARGUMENTS` (por defecto `working-tree`; `all` ejecuta los tres).

## Cuándo usarla
- Antes de un commit o PR, o al retomar una sesión, para saber qué hay sin commitear o mal rastreado.

## Cuándo NO usarla
- Crear el commit → usa `git-commit`.
- Buscar secretos en profundidad → usa `security-secret-scan`.
- Limpiar o borrar archivos: esta skill solo propone la acción.

## Precondiciones
- Estar en la raíz del repo. Si `$ARGUMENTS` no es válido, usar `working-tree` y avisarlo.

## Pasos
1. `working-tree`: `git status --short` y `git diff --stat`.
   - Cambios sin commitear y archivos no rastreados.
   - Marcar los no rastreados que deberían ignorarse (`git check-ignore -v`) y los que parecen secretos (`.env*`, keystores, `local.properties`) sin leer su contenido.
2. `generated`: `git ls-files` filtrando `build/`, `.gradle/`, `.idea/`, `.kotlin/`, `*.log`, `*.mbtiles`, `*.pmtiles`, `*.onnx`, `*.apk`, `*.aab`, `*.jks`, `*.keystore`, `local.properties`.
   - Toda coincidencia rastreada es hallazgo; comprobar si `.gitignore` la cubre.
3. `large-files`: tamaño de lo rastreado (`git cat-file -s :<ruta>`) y de lo no rastreado (tamaño en disco).
   - Hallazgo si > 1 MB o binario inesperado (`.jar`, `.so`, `.zip`, `.onnx`, teselas, audios, imágenes pesadas).
4. Informar findings por severidad (CRITICO > ALTO > MEDIO > BAJO):
   `SEVERIDAD | archivo:línea | problema | corrección propuesta`
   - Secreto rastreado: CRITICO (`git rm --cached`, rotar la clave y pasar `security-secret-scan`).
   - Generado rastreado: ALTO (`git rm -r --cached <ruta>` y añadir a `.gitignore`).
   - Grande o binario: MEDIO (justificar o ignorar).
   - Sin commitear: BAJO (commitear con `git-commit`; descartar solo con permiso del usuario).
5. Sin hallazgos: decir "sin hallazgos" y qué se comprobó.

## Validaciones / tests
- `git status --short` queda idéntico antes y después.
- Cada hallazgo incluye ruta y acción propuesta; ningún valor de secreto mostrado.

## Definition of Done
- [ ] Revisado el ámbito pedido (o los tres con `all`).
- [ ] Findings en formato `SEVERIDAD | archivo:línea | problema | corrección propuesta`.
- [ ] Ningún archivo ni el índice modificados.

## Docs a actualizar
- Ninguno; si falta una regla en `.gitignore`, proponerla al agente principal.
