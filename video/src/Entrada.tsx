import React from "react";
import { interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { FUENTE, LATON, ROJO, TEXTO, TEXTO_SUAVE } from "./paleta";
import { Trombito, parpadeoEnFrame } from "./Trombito";
import { frames, guion } from "./guion";

const LINEAS = ["RALLY", "COPILOT"];
const LEMA = "Tu copiloto en cada curva";
const CLAMP = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;

/** Entrada: Trombito cae con casco, «RALLY COPILOT» entra de golpe letra a letra y el lema debajo. */
export const Entrada: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const salto = spring({ frame, fps, delay: 2, config: { damping: 8, stiffness: 110, mass: 0.9 } });
  const aparece = interpolate(frame, [2, 8], [0, 1], CLAMP);
  const lema = spring({ frame, fps, delay: 38, config: { damping: 16, stiffness: 140 } });
  const barra = spring({ frame, fps, delay: 30, config: { damping: 15, stiffness: 150 } });
  // Barrido rojo en diagonal al entrar (impacto de la caída).
  const barrido = interpolate(frame, [4, 20], [-2600, 2600], CLAMP);

  const inicioSalida = frames(guion.entrada) - 38;
  const salida = interpolate(frame, [inicioSalida, inicioSalida + 20], [0, 1], CLAMP);

  let letra = 0;
  return (
    <div
      style={{
        position: "absolute",
        inset: 0,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        fontFamily: FUENTE,
        opacity: 1 - salida,
        transform: `translateX(${-salida * 260}px)`,
      }}
    >
      <div
        style={{
          position: "absolute",
          left: 960 - 160,
          top: -300,
          width: 320,
          height: 1700,
          backgroundColor: ROJO,
          opacity: 0.55,
          transform: `translateX(${barrido}px) rotate(24deg)`,
        }}
      />
      <div style={{ display: "flex", flexDirection: "row", alignItems: "center", gap: 70 }}>
        <div style={{ opacity: aparece, transform: `translateY(${(1 - salto) * -620}px)` }}>
          <Trombito
            idp="entrada"
            tamano={400}
            parpadeo={parpadeoEnFrame(frame, fps, 36)}
            rebote={Math.max(0, 1 - salto) * Math.min(1, frame / 10)}
          />
        </div>
        <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-start" }}>
          {LINEAS.map((texto, fila) => (
            <div
              key={texto}
              style={{
                display: "flex",
                fontSize: 188,
                lineHeight: 0.95,
                fontStyle: "italic",
                fontWeight: 900,
                letterSpacing: -4,
                color: fila === 0 ? TEXTO : LATON,
                textShadow: "0 8px 0 rgba(0,0,0,0.45)",
              }}
            >
              {texto.split("").map((c) => {
                const i = letra++;
                const s = spring({ frame, fps, delay: 12 + i * 2.5, config: { damping: 13, stiffness: 230 } });
                return (
                  <span
                    key={i}
                    style={{
                      display: "inline-block",
                      whiteSpace: "pre",
                      opacity: Math.min(1, s * 2),
                      transform: `translateX(${(1 - s) * -190}px) skewX(${(1 - s) * -24}deg)`,
                    }}
                  >
                    {c}
                  </span>
                );
              })}
            </div>
          ))}
          <div
            style={{
              marginTop: 22,
              height: 14,
              width: 640 * barra,
              backgroundColor: ROJO,
              transform: "skewX(-24deg)",
              transformOrigin: "left center",
            }}
          />
          <div
            style={{
              marginTop: 24,
              fontSize: 58,
              fontStyle: "italic",
              fontWeight: 600,
              color: TEXTO_SUAVE,
              opacity: lema,
              transform: `translateX(${(1 - lema) * 60}px)`,
            }}
          >
            {LEMA}
          </div>
        </div>
      </div>
    </div>
  );
};
