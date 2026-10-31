"""Convierte la mascota SVG en los VectorDrawable de Android sin dependencias.

Uso:
  python -I scripts/svg-a-vector.py mascota   # docs/assets/mascota-rally.svg -> res/drawable/mascota_rally.xml
  python -I scripts/svg-a-vector.py launcher  # misma fuente -> res/drawable/ic_launcher_foreground.xml (zona segura,
                                              # centrada por la caja de lo pintado; ver CAJA_PINTADA)

Solo entiende lo que usa la mascota: <path> con d, fill, stroke, stroke-width, stroke-linecap/linejoin round y
stroke-opacity. ic_launcher_monochrome.xml e ic_stat_rally.xml se mantienen a mano (siluetas simplificadas);
el monocromo copia el translate y la escala del launcher (ESCALA_LAUNCHER, TRASLADO_X, TRASLADO_Y).
"""

import pathlib
import re
import sys

RAIZ = pathlib.Path(__file__).resolve().parent.parent
FUENTE = RAIZ / "docs" / "assets" / "mascota-rally.svg"
DRAWABLE = RAIZ / "app" / "src" / "main" / "res" / "drawable"

CABECERA_VECTOR = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
"""

# Caja de lo pintado (alfa > 0, trazos incluidos) de docs/assets/mascota-rally.svg en viewBox 108: se midió
# renderizando el SVG con Chrome headless a 1080 px sobre fondo transparente y tomando el getbbox() del canal alfa
# con PIL (10 px por unidad; precisión ~0,1). Si cambia la mascota, volver a medirla.
CAJA_PINTADA = (17.3, 18.8, 95.4, 82.0)  # (x mín, y mín, x máx, y máx)
# Zona segura del icono adaptativo: círculo de 66 dp en el lienzo de 108 dp. Con esta escala el píxel pintado más
# lejos del centro de la caja queda a 0,75 * 41,2 = 30,9 dp (< 33 dp); la escala máxima que cabe es 0,80.
ESCALA_LAUNCHER = 0.75
CENTRO_LIENZO = 54.0
# Con el grupo escalado respecto a (54, 54), el centro de la caja cae en 54 + escala * (centro - 54); el traslado
# lo lleva a (54, 54).
TRASLADO_X = round(ESCALA_LAUNCHER * (CENTRO_LIENZO - (CAJA_PINTADA[0] + CAJA_PINTADA[2]) / 2), 2)  # -1,76
TRASLADO_Y = round(ESCALA_LAUNCHER * (CENTRO_LIENZO - (CAJA_PINTADA[1] + CAJA_PINTADA[3]) / 2), 2)  # 2,70


def paths_android(svg: str) -> list[str]:
    salida = []
    for crudo in re.findall(r"<path ([^>]*)/>", svg):
        a = dict(re.findall(r'([\w-]+)="([^"]*)"', crudo))
        attrs = [f'android:pathData="{a["d"]}"']
        if a.get("fill", "") not in ("", "none"):
            attrs.append(f'android:fillColor="{a["fill"]}"')
        if "stroke" in a:
            attrs.append(f'android:strokeColor="{a["stroke"]}"')
            attrs.append(f'android:strokeWidth="{a["stroke-width"]}"')
            if a.get("stroke-linecap") == "round":
                attrs.append('android:strokeLineCap="round"')
            if a.get("stroke-linejoin") == "round":
                attrs.append('android:strokeLineJoin="round"')
            if "stroke-opacity" in a:
                attrs.append(f'android:strokeAlpha="{a["stroke-opacity"]}"')
        salida.append("<path " + " ".join(attrs) + " />")
    return salida


def num(v: float) -> str:
    return f"{v:g}"


def main() -> None:
    modo = sys.argv[1] if len(sys.argv) > 1 else ""
    paths = paths_android(FUENTE.read_text(encoding="utf-8"))
    if modo == "mascota":
        destino = DRAWABLE / "mascota_rally.xml"
        texto = (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<!-- Trombito con casco de rally, sin fondo, para la app. Generado por scripts/svg-a-vector.py desde\n"
            "     docs/assets/mascota-rally.svg (viewBox 108). No editar a mano. -->\n"
            + CABECERA_VECTOR
            + "".join(f"    {p}\n" for p in paths)
            + "</vector>\n"
        )
    elif modo == "launcher":
        destino = DRAWABLE / "ic_launcher_foreground.xml"
        texto = (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<!-- Trombito con casco de rally. Generado por scripts/svg-a-vector.py desde docs/assets/mascota-rally.svg\n"
            "     (viewBox 108). Escalada al 75 % y trasladada (" + num(TRASLADO_X) + ", " + num(TRASLADO_Y) + ") dp para centrar\n"
            "     la caja de lo pintado en la zona segura de 66 dp. No editar a mano. -->\n"
            + CABECERA_VECTOR
            + f'    <group android:translateX="{num(TRASLADO_X)}" android:translateY="{num(TRASLADO_Y)}">\n'
            + "        <group\n"
            + '            android:pivotX="54"\n'
            + '            android:pivotY="54"\n'
            + f'            android:scaleX="{ESCALA_LAUNCHER}"\n'
            + f'            android:scaleY="{ESCALA_LAUNCHER}">\n'
            + "".join(f"            {p}\n" for p in paths)
            + "        </group>\n    </group>\n</vector>\n"
        )
    else:
        sys.exit("Uso: python -I scripts/svg-a-vector.py <mascota|launcher>")
    destino.write_text(texto, encoding="utf-8", newline="\n")
    print(f"svg-a-vector: {len(paths)} paths -> {destino.relative_to(RAIZ).as_posix()}")


if __name__ == "__main__":
    main()
