"""Extrae el vocabulario del modelo Vosk (vosk-model-small-es-0.42) a app/src/main/resources/asr/.

El modelo pequeño no trae graph/words.txt: la tabla de palabras va incrustada en graph/Gr.fst (cabecera de OpenFst con
tabla de símbolos de salida). Uso (con -I, el modelo está descomprimido en <modelo>):

    python -I extraer-vocabulario.py <modelo>/graph/Gr.fst app/src/main/resources/asr/vocabulario-vosk-es-0.42.txt.gz

Salida: una palabra por línea, ordenada, sin las especiales (<eps>, !SIL, [unk], #...), comprimida con gzip sin fecha.
"""

import gzip
import struct
import sys


def leer_cadena(f):
    n = struct.unpack("<i", f.read(4))[0]
    return f.read(n).decode("utf-8")


def leer_tabla(f):
    struct.unpack("<i", f.read(4))  # número mágico
    leer_cadena(f)  # nombre
    f.read(8)  # available_key
    n = struct.unpack("<q", f.read(8))[0]
    simbolos = []
    for _ in range(n):
        simbolo = leer_cadena(f)
        f.read(8)  # clave
        simbolos.append(simbolo)
    return simbolos


def main(origen, destino):
    with open(origen, "rb") as f:
        f.read(4)  # número mágico del FST
        leer_cadena(f)  # tipo de FST
        leer_cadena(f)  # tipo de arco
        f.read(4)  # versión
        banderas = struct.unpack("<i", f.read(4))[0]
        f.read(8 + 24)  # propiedades, inicio, estados, arcos
        if banderas & 1:
            leer_tabla(f)  # símbolos de entrada
        if not banderas & 2:
            sys.exit("El FST no lleva tabla de símbolos de salida")
        palabras = sorted({s for s in leer_tabla(f) if s and s[0] not in "<![#"})
    contenido = ("\n".join(palabras) + "\n").encode("utf-8")
    with open(destino, "wb") as salida:
        with gzip.GzipFile(filename="", mode="wb", fileobj=salida, mtime=0, compresslevel=9) as gz:
            gz.write(contenido)
    print(f"{len(palabras)} palabras")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
