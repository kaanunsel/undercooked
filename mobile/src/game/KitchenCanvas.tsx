import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Canvas, Circle, Group, Line, Rect, RoundedRect, vec } from '@shopify/react-native-skia';
import type {
  Facing,
  ItemDto,
  ItemType,
  PlayerSnapshotDto,
  StationItemDto,
  StationStaticDto,
} from '../types/protocol';

interface KitchenCanvasProps {
  width: number;
  height: number;
  gridW: number;
  gridH: number;
  stations: StationStaticDto[];
  stationItems: StationItemDto[];
  players: PlayerSnapshotDto[];
  localPlayerId: string | null;
  cleanPlates: number;
}

const CHEF_COLORS = ['#E53935', '#1E88E5', '#43A047', '#F9A825'];

function getFacingTile(x: number, y: number, facing: Facing): { tx: number; ty: number } {
  const reach = 0.85;
  let targetX = x;
  let targetY = y;
  if (facing === 'N') targetY -= reach;
  else if (facing === 'S') targetY += reach;
  else if (facing === 'E') targetX += reach;
  else if (facing === 'W') targetX -= reach;
  return { tx: Math.floor(targetX), ty: Math.floor(targetY) };
}

function renderIngredientGlyph(type: ItemType, cx: number, cy: number, r: number, keyPrefix: string) {
  switch (type) {
    case 'BUN':
      return (
        <Group key={keyPrefix}>
          <Circle cx={cx} cy={cy} r={r} color="#E6A14A" />
          <Circle cx={cx - r * 0.25} cy={cy - r * 0.2} r={r * 0.14} color="#FFF3D1" />
          <Circle cx={cx + r * 0.22} cy={cy - r * 0.1} r={r * 0.14} color="#FFF3D1" />
        </Group>
      );
    case 'RAW_PATTY':
      return <Circle key={keyPrefix} cx={cx} cy={cy} r={r * 0.92} color="#D86C70" />;
    case 'COOKED_PATTY':
      return (
        <Group key={keyPrefix}>
          <Circle cx={cx} cy={cy} r={r * 0.92} color="#6D3B1E" />
          <Line
            p1={vec(cx - r * 0.45, cy - r * 0.2)}
            p2={vec(cx + r * 0.45, cy - r * 0.2)}
            color="#43200D"
            strokeWidth={2}
          />
          <Line
            p1={vec(cx - r * 0.45, cy + r * 0.2)}
            p2={vec(cx + r * 0.45, cy + r * 0.2)}
            color="#43200D"
            strokeWidth={2}
          />
        </Group>
      );
    case 'BURNT_PATTY':
      return (
        <Group key={keyPrefix}>
          <Circle cx={cx} cy={cy} r={r * 0.92} color="#212121" />
          <Circle cx={cx - r * 0.2} cy={cy - r * 0.15} r={r * 0.25} color="#424242" />
        </Group>
      );
    case 'RAW_LETTUCE':
      return (
        <Group key={keyPrefix}>
          <Circle cx={cx} cy={cy} r={r * 0.95} color="#4CAF50" />
          <Circle cx={cx - r * 0.25} cy={cy - r * 0.15} r={r * 0.45} color="#81C784" />
        </Group>
      );
    case 'CHOPPED_LETTUCE':
      return (
        <Group key={keyPrefix}>
          <RoundedRect x={cx - r * 0.7} y={cy - r * 0.4} width={r * 0.6} height={r * 0.45} r={2} color="#66BB6A" />
          <RoundedRect x={cx + r * 0.05} y={cy - r * 0.55} width={r * 0.65} height={r * 0.45} r={2} color="#43A047" />
          <RoundedRect x={cx - r * 0.35} y={cy + r * 0.08} width={r * 0.75} height={r * 0.45} r={2} color="#81C784" />
        </Group>
      );
    case 'RAW_TOMATO':
      return (
        <Group key={keyPrefix}>
          <Circle cx={cx} cy={cy} r={r * 0.88} color="#E53935" />
          <Circle cx={cx} cy={cy - r * 0.55} r={r * 0.25} color="#2E7D32" />
        </Group>
      );
    case 'CHOPPED_TOMATO':
      return (
        <Group key={keyPrefix}>
          <Circle cx={cx - r * 0.32} cy={cy - r * 0.15} r={r * 0.45} color="#EF5350" />
          <Circle cx={cx + r * 0.3} cy={cy + r * 0.15} r={r * 0.45} color="#C62828" />
        </Group>
      );
    case 'RAW_CHEESE':
      return (
        <RoundedRect
          key={keyPrefix}
          x={cx - r * 0.8}
          y={cy - r * 0.65}
          width={r * 1.6}
          height={r * 1.3}
          r={3}
          color="#FFD54F"
        />
      );
    case 'SLICED_CHEESE':
      return (
        <Group key={keyPrefix}>
          <RoundedRect x={cx - r * 0.75} y={cy - r * 0.45} width={r * 1.3} height={r * 0.85} r={2} color="#FFCA28" />
          <RoundedRect x={cx - r * 0.45} y={cy - r * 0.15} width={r * 1.2} height={r * 0.75} r={2} color="#FFE082" />
        </Group>
      );
    default:
      return null;
  }
}

function renderItem(item: ItemDto, cx: number, cy: number, tile: number, keyPrefix: string) {
  const r = tile * 0.27;
  if (item.type !== 'PLATE') {
    return renderIngredientGlyph(item.type, cx, cy, r, keyPrefix);
  }
  const contents = item.contents ?? [];
  return (
    <Group key={keyPrefix}>
      <Circle cx={cx} cy={cy} r={tile * 0.34} color="#FAFAFA" />
      <Circle cx={cx} cy={cy} r={tile * 0.26} color="#E0E0E0" />
      {contents.map((ing, idx) =>
        renderIngredientGlyph(
          ing,
          cx + (idx % 2 === 0 ? -1.5 : 1.5),
          cy - idx * 2.2,
          r * 0.78,
          `${keyPrefix}-ing-${idx}`,
        ),
      )}
    </Group>
  );
}

export const KitchenCanvas: React.FC<KitchenCanvasProps> = ({
  width,
  height,
  gridW,
  gridH,
  stations,
  stationItems,
  players,
  localPlayerId,
  cleanPlates,
}) => {
  if (width <= 0 || height <= 0) {
    return <View style={styles.container} />;
  }

  const tile = Math.floor(Math.min(width / gridW, height / gridH));
  const mapW = tile * gridW;
  const mapH = tile * gridH;
  const offsetX = Math.floor((width - mapW) / 2);
  const offsetY = Math.floor((height - mapH) / 2);

  const localPlayer = players.find((p) => p.id === localPlayerId);
  const highlight = localPlayer
    ? getFacingTile(localPlayer.x, localPlayer.y, localPlayer.facing)
    : null;

  return (
    <View style={styles.container}>
      <Canvas style={{ width, height }}>
        <Group transform={[{ translateX: offsetX }, { translateY: offsetY }]}>
          {/* Checkerboard Kitchen Floor */}
          {Array.from({ length: gridH }).map((_, y) =>
            Array.from({ length: gridW }).map((__, x) => (
              <Rect
                key={`floor-${x}-${y}`}
                x={x * tile}
                y={y * tile}
                width={tile}
                height={tile}
                color={(x + y) % 2 === 0 ? '#F5ECD7' : '#EAE0C8'}
              />
            )),
          )}

          {/* Stations */}
          {stations.map((st, idx) => {
            const px = st.x * tile;
            const py = st.y * tile;
            const cx = px + tile / 2;
            const cy = py + tile / 2;
            const isHighlighted = highlight && highlight.tx === st.x && highlight.ty === st.y && st.type !== 'WALL';

            let baseColor = '#A16E45';
            let topColor = '#C28D5E';
            if (st.type === 'WALL') {
              baseColor = '#2C313A';
              topColor = '#3E4552';
            } else if (st.type === 'STOVE') {
              baseColor = '#37474F';
              topColor = '#546E7A';
            } else if (st.type === 'SERVING_WINDOW') {
              baseColor = '#D84315';
              topColor = '#FF7043';
            } else if (st.type === 'TRASH_BIN') {
              baseColor = '#455A64';
              topColor = '#607D8B';
            } else if (st.type === 'PLATE_DISPENSER') {
              baseColor = '#78909C';
              topColor = '#B0BEC5';
            } else if (st.type.startsWith('CRATE_')) {
              baseColor = '#6D4C41';
              topColor = '#8D6E63';
            }

            return (
              <Group key={`st-${st.id}-${st.x}-${st.y}-${idx}`}>
                <Rect x={px} y={py} width={tile} height={tile} color={baseColor} />
                <RoundedRect
                  x={px + 2}
                  y={py + 2}
                  width={tile - 4}
                  height={tile - 4}
                  r={4}
                  color={topColor}
                />

                {/* Station-specific details */}
                {st.type === 'CHOPPING_BOARD' && (
                  <RoundedRect
                    x={px + tile * 0.16}
                    y={py + tile * 0.2}
                    width={tile * 0.68}
                    height={tile * 0.6}
                    r={4}
                    color="#F3E5AB"
                  />
                )}

                {st.type === 'STOVE' && (
                  <Group>
                    <Circle cx={cx} cy={cy} r={tile * 0.3} color="#263238" />
                    <Circle cx={cx} cy={cy} r={tile * 0.2} color="#FF5722" />
                    <Circle cx={cx} cy={cy} r={tile * 0.12} color="#263238" />
                  </Group>
                )}

                {st.type === 'TRASH_BIN' && (
                  <RoundedRect
                    x={px + tile * 0.22}
                    y={py + tile * 0.22}
                    width={tile * 0.56}
                    height={tile * 0.56}
                    r={6}
                    color="#263238"
                  />
                )}

                {st.type === 'SERVING_WINDOW' && (
                  <RoundedRect
                    x={px + tile * 0.15}
                    y={py + tile * 0.15}
                    width={tile * 0.7}
                    height={tile * 0.7}
                    r={4}
                    color="#FFD54F"
                  />
                )}

                {st.type === 'PLATE_DISPENSER' &&
                  Array.from({ length: Math.min(4, cleanPlates) }).map((_, pIdx) => (
                    <Circle
                      key={`disp-plate-${pIdx}`}
                      cx={cx}
                      cy={cy + 6 - pIdx * 3.5}
                      r={tile * 0.28}
                      color={pIdx % 2 === 0 ? '#FFFFFF' : '#ECEFF1'}
                    />
                  ))}

                {st.type === 'CRATE_BUN' && renderIngredientGlyph('BUN', cx, cy, tile * 0.26, `crate-${idx}`)}
                {st.type === 'CRATE_MEAT' && renderIngredientGlyph('RAW_PATTY', cx, cy, tile * 0.26, `crate-${idx}`)}
                {st.type === 'CRATE_LETTUCE' && renderIngredientGlyph('RAW_LETTUCE', cx, cy, tile * 0.26, `crate-${idx}`)}
                {st.type === 'CRATE_TOMATO' && renderIngredientGlyph('RAW_TOMATO', cx, cy, tile * 0.26, `crate-${idx}`)}
                {st.type === 'CRATE_CHEESE' && renderIngredientGlyph('RAW_CHEESE', cx, cy, tile * 0.26, `crate-${idx}`)}

                {/* Highlight facing target tile */}
                {isHighlighted && (
                  <RoundedRect
                    x={px + 2}
                    y={py + 2}
                    width={tile - 4}
                    height={tile - 4}
                    r={4}
                    color="#FFF176"
                    style="stroke"
                    strokeWidth={3}
                  />
                )}
              </Group>
            );
          })}

          {/* Dynamic Items & Progress Bars on Stations */}
          {stationItems.map((si) => {
            const cx = si.x * tile + tile / 2;
            const cy = si.y * tile + tile / 2;
            const barW = tile * 0.76;
            const barH = 6;
            const barX = si.x * tile + tile * 0.12;
            const barY = si.y * tile + 4;

            return (
              <Group key={`sitem-${si.id}`}>
                {renderItem(si.item, cx, cy, tile, `st-item-${si.id}`)}

                {typeof si.chopRatio === 'number' && si.chopRatio > 0 && (
                  <Group>
                    <RoundedRect x={barX} y={barY} width={barW} height={barH} r={3} color="#263238" />
                    <RoundedRect
                      x={barX}
                      y={barY}
                      width={barW * Math.min(1, si.chopRatio)}
                      height={barH}
                      r={3}
                      color="#26C6DA"
                    />
                  </Group>
                )}

                {typeof si.cookRatio === 'number' && (
                  <Group>
                    <RoundedRect x={barX} y={barY} width={barW} height={barH} r={3} color="#263238" />
                    {si.burnRatio && si.burnRatio > 0 ? (
                      <RoundedRect
                        x={barX}
                        y={barY}
                        width={barW * Math.min(1, si.burnRatio)}
                        height={barH}
                        r={3}
                        color={si.burnRatio > 0.65 ? '#FF1744' : '#FFAB00'}
                      />
                    ) : (
                      <RoundedRect
                        x={barX}
                        y={barY}
                        width={barW * Math.min(1, si.cookRatio)}
                        height={barH}
                        r={3}
                        color={si.cookRatio >= 1 ? '#00E676' : '#FF9100'}
                      />
                    )}
                  </Group>
                )}
              </Group>
            );
          })}

          {/* Chefs */}
          {players.map((p) => {
            const cx = p.x * tile;
            const cy = p.y * tile;
            const chefColor = CHEF_COLORS[p.slot % CHEF_COLORS.length];
            const isLocal = p.id === localPlayerId;

            let fdx = 0;
            let fdy = 0;
            if (p.facing === 'N') fdy = -1;
            else if (p.facing === 'S') fdy = 1;
            else if (p.facing === 'E') fdx = 1;
            else if (p.facing === 'W') fdx = -1;

            const holdX = cx + fdx * tile * 0.38;
            const holdY = cy + fdy * tile * 0.38;

            return (
              <Group key={`chef-${p.id}`}>
                {/* Subtle shadow */}
                <Circle cx={cx} cy={cy + tile * 0.12} r={tile * 0.34} color="rgba(0,0,0,0.2)" />

                {/* Local player highlight ring */}
                {isLocal && (
                  <Circle
                    cx={cx}
                    cy={cy}
                    r={tile * 0.42}
                    color="#FFF59D"
                    style="stroke"
                    strokeWidth={2.5}
                  />
                )}

                {/* Chef apron body */}
                <Circle cx={cx} cy={cy} r={tile * 0.34} color={chefColor} />
                {/* White chef hat */}
                <Circle cx={cx} cy={cy - tile * 0.04} r={tile * 0.21} color="#FFFFFF" />
                {/* Direction indicator dot */}
                <Circle
                  cx={cx + fdx * tile * 0.22}
                  cy={cy + fdy * tile * 0.22}
                  r={tile * 0.08}
                  color="#212121"
                />

                {/* Held Item */}
                {p.heldItem && renderItem(p.heldItem, holdX, holdY, tile * 0.85, `held-${p.id}`)}
              </Group>
            );
          })}
        </Group>
      </Canvas>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#1E222A',
  },
});
