"""Crea una traza GPX de prueba siguiendo una carretera de `vias-mallorca.tsv.gz` entre dos puntos.

Uso: python -I scripts/traza-desde-vias.py <vias-mallorca.tsv.gz> <ref> <lat,lon inicio> <lat,lon fin> <salida.gpx> [nombre]
Ejemplo: python -I scripts/traza-desde-vias.py vias-mallorca.tsv.gz Ma-10 39.7660,2.7200 39.8225,2.8848 docs/rutas/ma-10-soller-lluc.gpx

Camino más corto (Dijkstra) por las vías con ese `ref`; las demás vías principales cuestan ×4 (salvan huecos del `ref` en
OSM) y las residenciales no se usan. Inicio y fin = nodos de la vía `ref` más cercanos. Sin tiempos ni elevación: se
reproduce con `gps-replay --kmh`. Datos © OpenStreetMap contributors (ODbL).
"""

import gzip
import heapq
import math
import sys

MENORES = ("residential", "living_street", "unclassified", "road")


def distancia(p, q):
    return math.hypot((p[0] - q[0]) / 1e6, (p[1] - q[1]) / 1e6 * math.cos(math.radians(p[0] / 1e6))) * 111195


def main() -> int:
    vias_gz, ref, ini, fin, salida = sys.argv[1:6]
    nombre = sys.argv[6] if len(sys.argv) > 6 else ref
    ady, de_ref = {}, set()
    for linea in gzip.open(vias_gz, "rt", encoding="utf-8"):
        if linea.startswith("#"):
            continue
        c = linea.rstrip("\n").split("\t")
        if c[1] in MENORES:
            continue
        pts = [tuple(int(v) for v in p.split(",")) for p in c[8].split(";")]
        k = 1.0 if c[6] == ref else 4.0
        if c[6] == ref:
            de_ref.update(pts)
        # Sentido único (columna oneway): en una autovía cada calzada es una vía; sin esto la traza podía ir por
        # la calzada contraria.
        sentido = c[5]
        for p, q in zip(pts, pts[1:]):
            w = k * distancia(p, q)
            if sentido != "-1":
                ady.setdefault(p, []).append((q, w))
            if sentido not in ("yes", "1", "true", "-1"):
                ady.setdefault(q, []).append((p, w))
            elif sentido == "-1":
                ady.setdefault(q, []).append((p, w))
    if not de_ref:
        print(f"No hay vías con ref {ref}", file=sys.stderr)
        return 1

    def cercanos(texto, n=12):
        lat, lon = (float(v) for v in texto.split(","))
        return sorted(de_ref, key=lambda p: distancia(p, (lat * 1e6, lon * 1e6)))[:n]

    def dijkstra(a):
        dist, prev, cola = {a: 0.0}, {}, [(0.0, a)]
        while cola:
            d, p = heapq.heappop(cola)
            if d > dist[p]:
                continue
            for q, w in ady.get(p, []):
                if d + w < dist.get(q, math.inf):
                    dist[q], prev[q] = d + w, p
                    heapq.heappush(cola, (d + w, q))
        return dist, prev

    # Con sentidos únicos el nodo más cercano puede estar en la calzada contraria: se prueban varios candidatos
    # de inicio y fin y se queda el camino más corto que exista.
    mejor = None
    finales = cercanos(fin)
    for a in cercanos(ini):
        dist, prev = dijkstra(a)
        for b in finales:
            if b in dist and (mejor is None or dist[b] < mejor[0]):
                mejor = (dist[b], a, b, prev)
    if mejor is None:
        print("No hay camino entre los dos puntos", file=sys.stderr)
        return 1
    _, a, b, prev = mejor
    ruta = [b]
    while ruta[-1] != a:
        ruta.append(prev[ruta[-1]])
    ruta.reverse()
    with open(salida, "w", encoding="utf-8", newline="\n") as f:
        f.write('<?xml version="1.0" encoding="UTF-8"?>\n<gpx version="1.1" creator="Rally Copilot (vias-mallorca)" '
                'xmlns="http://www.topografix.com/GPX/1/1">\n')
        f.write(f"  <metadata>\n    <name>{nombre}</name>\n    <copyright author=\"OpenStreetMap contributors\">"
                "<license>https://opendatacommons.org/licenses/odbl/1-0/</license></copyright>\n  </metadata>\n")
        f.write(f"  <trk><name>{ref}</name><trkseg>\n")
        for lat, lon in ruta:
            f.write(f'    <trkpt lat="{lat / 1e6:.6f}" lon="{lon / 1e6:.6f}"/>\n')
        f.write("  </trkseg></trk>\n</gpx>\n")
    largo = sum(distancia(p, q) for p, q in zip(ruta, ruta[1:]))
    print(f"{salida}: {len(ruta)} puntos, {largo / 1000:.1f} km")
    return 0


if __name__ == "__main__":
    sys.exit(main())
