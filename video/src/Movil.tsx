import React from "react";
import { AbsoluteFill, interpolate, Sequence, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { Video } from "@remotion/media";
import { TransitionSeries, linearTiming } from "@remotion/transitions";
import { slide } from "@remotion/transitions/slide";
import { duracionTramo, frames, guion, inicioTramo, inicioTramos, ranuraTramo } from "./guion";
import { CARBON_PROFUNDO, LATON, ROJO } from "./paleta";

const PANTALLA_W = 529;
const PANTALLA_H = 940; // 1080x1920 escalado (marco total ~976 px, deja aire para el empuje y el giro)
const BORDE = 18;
const RADIO_MARCO = 70;
const CENTRO_X = 1360;
const CENTRO_Y = 540;
const CLAMP = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;

/** Marco de móvil con los tramos encadenados dentro; el audio de los clips con `voz` es la voz del copiloto. */
export const Movil: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const ini0 = inicioTramos() - 5;
  const entra = spring({ frame: frame - ini0, fps, config: { damping: 15, stiffness: 100, mass: 0.9 } });
  const opacidad = interpolate(frame - ini0, [0, 10], [0, 1], CLAMP);
  const flota = Math.sin((2 * Math.PI * frame) / (4 * fps)) * 5;
  const giro = Math.sin((2 * Math.PI * frame) / (6 * fps)) * 0.6 - 1.5;
  const desplazamiento = (1 - entra) * 560;
  // Empuje por tramo: 1.00 -> 1.04 -> 1.00 (campana), vuelve a 1 justo en los cortes (que caen en beat).
  let empuje = 0;
  for (let i = 0; i < guion.tramos.length; i++) {
    const ini = inicioTramo(i);
    const largo = ranuraTramo(i);
    if (frame >= ini && frame < ini + largo) {
      const p = (frame - ini) / largo;
      empuje = 0.04 * Math.pow(Math.sin(Math.PI * p), 2);
    }
  }
  const escala = 1 + empuje;

  return (
    <div
      style={{
        position: "absolute",
        left: CENTRO_X - (PANTALLA_W + 2 * BORDE) / 2,
        top: CENTRO_Y - (PANTALLA_H + 2 * BORDE) / 2,
        width: PANTALLA_W + 2 * BORDE,
        height: PANTALLA_H + 2 * BORDE,
        boxSizing: "border-box",
        borderRadius: RADIO_MARCO,
        border: `${BORDE}px solid #050607`,
        backgroundColor: CARBON_PROFUNDO,
        boxShadow: `0 0 0 3px ${LATON}, 0 0 90px rgba(224,42,50,0.45), 0 40px 80px rgba(0,0,0,0.6)`,
        opacity: opacidad,
        transform: `translate(${desplazamiento}px, ${flota}px) rotate(${giro}deg) scale(${escala})`,
        overflow: "hidden",
      }}
    >
      <AbsoluteFill style={{ backgroundColor: CARBON_PROFUNDO }}>
        <Sequence from={inicioTramos()} layout="none">
          <TransitionSeries>
            {guion.tramos.flatMap((t, i) => {
              const elementos: React.ReactElement[] = [];
              const dur = duracionTramo(i);
              if (i > 0) {
                elementos.push(
                  <TransitionSeries.Transition
                    key={`t${i}`}
                    presentation={slide({ direction: "from-right" })}
                    timing={linearTiming({ durationInFrames: frames(guion.transicion) })}
                  />,
                );
              }
              elementos.push(
                <TransitionSeries.Sequence key={`s${i}`} durationInFrames={dur}>
                  <Video
                    src={staticFile(t.clip)}
                    trimBefore={frames(t.inicio)}
                    muted={!t.voz}
                    volume={(f) =>
                      guion.volumenVoz * interpolate(f, [0, 6, dur - 10, dur], [0, 1, 1, 0], CLAMP)
                    }
                    style={{ width: PANTALLA_W, height: PANTALLA_H, objectFit: "cover" }}
                  />
                </TransitionSeries.Sequence>,
              );
              return elementos;
            })}
          </TransitionSeries>
        </Sequence>
      </AbsoluteFill>
      {/* cámara frontal */}
      <div
        style={{
          position: "absolute",
          top: 12,
          left: "50%",
          width: 24,
          height: 24,
          marginLeft: -12,
          borderRadius: 12,
          backgroundColor: "#050607",
          boxShadow: `inset 0 0 0 4px #1A1C20, 0 0 0 2px ${ROJO}55`,
        }}
      />
    </div>
  );
};
