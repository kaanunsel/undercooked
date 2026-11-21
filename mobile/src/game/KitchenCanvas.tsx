import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Canvas, Circle, Group, Line, Rect, RoundedRect, vec } from '@shopify/react-native-skia';
import type {
  Facing,
  ItemDto,
  ItemType,
  PlayerSnapshotDto,
  StationItemDto,
  StationStaticDto,
  StationType,
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

const STATION_SHORT_LABEL: Partial<Record<StationType, string>> = {
  CRATE_BUN: 'BUN',
  CRATE_MEAT: 'MEAT',
  CRATE_LETTUCE: 'LET',
  CRATE_TOMATO: 'TOM',
  CRATE_CHEESE: 'CHS',
  CHOPPING_BOARD: 'CHOP',
  STOVE: 'STOVE',
  PLATE_DISPENSER: 'PLATES',
  TRASH_BIN: 'TRASH',
};

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

function getContextualTooltip(
  station: StationStaticDto,
  stationItem: StationItemDto | undefined,
  held: ItemDto | null,
  cleanPlates: number,
): { title: string; hint: string; accent: string } {
  switch (station.type) {
    case 'CRATE_BUN':
      return { title: 'Bun Crate', hint: 'Tap GRAB for Burger Bun', accent: '#FFB74D' };
    case 'CRATE_MEAT':
      return { title: 'Meat Crate', hint: 'Tap GRAB for Raw Patty', accent: '#EF9A9A' };
    case 'CRATE_LETTUCE':
      return { title: 'Lettuce Crate', hint: 'Tap GRAB for Lettuce', accent: '#81C784' };
    case 'CRATE_TOMATO':
      return { title: 'Tomato Crate', hint: 'Tap GRAB for Tomato', accent: '#E57373' };
    case 'CRATE_CHEESE':
      return { title: 'Cheese Crate', hint: 'Tap GRAB for Cheese', accent: '#FFF176' };
    case 'CHOPPING_BOARD':
      if (stationItem) {
        const isRaw =
          stationItem.item.type === 'RAW_LETTUCE' ||
          stationItem.item.type === 'RAW_TOMATO' ||
          stationItem.item.type === 'RAW_CHEESE';
        return {
          title: 'Chopping Board',
          hint: isRaw ? 'Hold CHOP (2s) to slice!' : 'Ready! Tap GRAB to pick up',
          accent: '#4DD0E1',
        };
      }
      return { title: 'Chopping Board', hint: 'Drop veggie/cheese, then hold CHOP', accent: '#4DD0E1' };
    case 'STOVE':
      if (stationItem?.item.type === 'COOKED_PATTY') {
        return { title: 'Stove • COOKED!', hint: 'Tap GRAB before it burns!', accent: '#69F0AE' };
      }
      if (stationItem?.item.type === 'BURNT_PATTY') {
        return { title: 'Stove • BURNT', hint: 'Grab & dump in Trash Bin', accent: '#FF5252' };
      }
      if (stationItem?.item.type === 'RAW_PATTY') {
        return { title: 'Stove • Cooking...', hint: 'Wait until bar turns green', accent: '#FFAB40' };
      }
      return { title: 'Stove', hint: 'Drop Raw Patty here to cook (4s)', accent: '#FF8A65' };
    case 'PLATE_DISPENSER':
      return {
        title: `Clean Plates (${cleanPlates}/4)`,
        hint: cleanPlates > 0 ? 'Tap GRAB to take / plate food' : 'Wait for dishwasher (4s)',
        accent: '#E0F7FA',
      };
    case 'SERVING_WINDOW':
      if (held?.type === 'PLATE' && held.recipe) {
        return { title: 'SERVING WINDOW ▶', hint: 'Tap GRAB to SERVE dish now!', accent: '#69F0AE' };
      }
      return { title: 'SERVING WINDOW ▶', hint: 'Bring plated recipe here to score!', accent: '#FFD54F' };
    case 'TRASH_BIN':
      return { title: 'Trash Bin', hint: 'Tap GRAB to discard mistakes', accent: '#B0BEC5' };
    default:
      if (stationItem && held) {
        return { title: 'Prep Counter', hint: 'Tap GRAB to plate / combine', accent: '#FFCC80' };
      }
      return { title: 'Prep Counter', hint: 'Tap GRAB to drop or pick up', accent: '#FFCC80' };
  }
}

function renderIngredientGlyph(type: ItemType, cx: number, cy: number, r: number, keyPrefix: string) {
  switch (type) {
    case 'BUN':
      return (
        <Group key={keyPrefix}>
          <Circle cx={cx} cy={cy} r={r} color="#E69A3A" />
          <Circle cx={cx} cy={cy - r * 0.08} r={r * 0.82} color="#F2B45B" />
          <Circle cx={cx - r * 0.28} cy={cy - r * 0.22} r={r * 0.13} color="#FFF8E1" />
          <Circle cx={cx + r * 0.24} cy={cy - r * 0.16} r={r * 0.13} color="#FFF8E1" />
          <Circle cx={cx} cy={cy + r * 0.18} r={r * 0.12} color="#FFF8E1" />
        </Group>
      );
    case 'RAW_PATTY':
      return (
        <Group key={keyPrefix}>
          <Circle cx={cx} cy={cy} r={r * 0.94} color="#C95A60" />
          <Circle cx={cx} cy={cy} r={r * 0.78} color="#E0757B" />
          <Circle cx={cx - r * 0.25} cy={cy - r * 0.15} r={r * 0.13} color="#F8BBD0" />
          <Circle cx={cx + r * 0.22} cy={cy + r * 0.18} r={r * 0.11} color="#F8BBD0" />
        </Group>
      );
    case 'COOKED_PATTY':
      return (
        <Group key={keyPrefix}>
          <Circle cx={cx} cy={cy} r={r * 0.94} color="#5D3219" />
          <Circle cx={cx} cy={cy} r={r * 0.78} color="#7A4424" />
          <Line
            p1={vec(cx - r * 0.48, cy - r * 0.22)}
            p2={vec(cx + r * 0.48, cy - r * 0.22)}
            color="#381A0A"
            strokeWidth={2}
          />
          <Line
            p1={vec(cx - r * 0.48, cy + r * 0.22)}
            p2={vec(cx + r * 0.48, cy + r * 0.22)}
            color="#381A0A"
            strokeWidth={2}
          />
        </Group>
      );
    case 'BURNT_PATTY':
      return (
        <Group key={keyPrefix}>
          <Circle cx={cx} cy={cy} r={r * 0.94} color="#1C1C1C" />
          <Circle cx={cx - r * 0.22} cy={cy - r * 0.18} r={r * 0.26} color="#3D3D3D" />
          <Circle cx={cx + r * 0.25} cy={cy + r * 0.15} r={r * 0.2} color="#555555" />
        </Group>
      );
    case 'RAW_LETTUCE':
      return (
        <Group key={keyPrefix}>
          <Circle cx={cx} cy={cy} r={r * 0.95} color="#388E3C" />
          <Circle cx={cx - r * 0.26} cy={cy - r * 0.18} r={r * 0.48} color="#66BB6A" />
          <Circle cx={cx + r * 0.22} cy={cy + r * 0.2} r={r * 0.42} color="#81C784" />
        </Group>
      );
    case 'CHOPPED_LETTUCE':
      return (
        <Group key={keyPrefix}>
          <RoundedRect x={cx - r * 0.72} y={cy - r * 0.42} width={r * 0.62} height={r * 0.46} r={2} color="#66BB6A" />
          <RoundedRect x={cx + r * 0.05} y={cy - r * 0.56} width={r * 0.66} height={r * 0.46} r={2} color="#43A047" />
          <RoundedRect x={cx - r * 0.36} y={cy + r * 0.08} width={r * 0.76} height={r * 0.46} r={2} color="#81C784" />
        </Group>
      );
    case 'RAW_TOMATO':
      return (
        <Group key={keyPrefix}>
          <Circle cx={cx} cy={cy + r * 0.06} r={r * 0.88} color="#D32F2F" />
          <Circle cx={cx - r * 0.22} cy={cy - r * 0.12} r={r * 0.26} color="#EF5350" />
          <Circle cx={cx} cy={cy - r * 0.55} r={r * 0.26} color="#2E7D32" />
        </Group>
      );
    case 'CHOPPED_TOMATO':
      return (
        <Group key={keyPrefix}>
          <Circle cx={cx - r * 0.32} cy={cy - r * 0.14} r={r * 0.46} color="#EF5350" />
          <Circle cx={cx + r * 0.3} cy={cy + r * 0.16} r={r * 0.46} color="#C62828" />
        </Group>
      );
    case 'RAW_CHEESE':
      return (
        <Group key={keyPrefix}>
          <RoundedRect x={cx - r * 0.82} y={cy - r * 0.65} width={r * 1.64} height={r * 1.3} r={3} color="#FFCA28" />
          <Circle cx={cx - r * 0.25} cy={cy - r * 0.15} r={r * 0.18} color="#F57F17" />
          <Circle cx={cx + r * 0.3} cy={cy + r * 0.18} r={r * 0.14} color="#F57F17" />
        </Group>
      );
    case 'SLICED_CHEESE':
      return (
        <Group key={keyPrefix}>
          <RoundedRect x={cx - r * 0.76} y={cy - r * 0.46} width={r * 1.32} height={r * 0.86} r={2} color="#FFB300" />
          <RoundedRect x={cx - r * 0.45} y={cy - r * 0.14} width={r * 1.22} height={r * 0.76} r={2} color="#FFE082" />
        </Group>
      );
    default:
      return null;
  }
}

function renderItem(item: ItemDto, cx: number, cy: number, tile: number, keyPrefix: string) {
  const r = tile * 0.26;
  if (item.type !== 'PLATE') {
    return renderIngredientGlyph(item.type, cx, cy, r, keyPrefix);
  }
  const contents = item.contents ?? [];
  return (
    <Group key={keyPrefix}>
      <Circle cx={cx} cy={cy} r={tile * 0.35} color="#FFFFFF" />
      <Circle cx={cx} cy={cy} r={tile * 0.27} color="#E0E0E0" />
      {item.recipe && (
        <Circle cx={cx} cy={cy} r={tile * 0.37} color="#00E676" style="stroke" strokeWidth={2} />
      )}
      {contents.map((ing, idx) =>
        renderIngredientGlyph(
          ing,
          cx + (idx % 2 === 0 ? -1.5 : 1.5),
          cy - idx * 2.2,
          r * 0.76,
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
  const holdingReadyDish = Boolean(localPlayer?.heldItem?.type === 'PLATE' && localPlayer.heldItem.recipe);

  const itemByCoord = new Map<string, StationItemDto>();
  for (const si of stationItems) {
    itemByCoord.set(`${si.x},${si.y}`, si);
  }

  const targetedStation = highlight
    ? stations.find((s) => s.x === highlight.tx && s.y === highlight.ty && s.type !== 'WALL')
    : undefined;
  const targetedItem = targetedStation
    ? itemByCoord.get(`${targetedStation.x},${targetedStation.y}`)
    : undefined;

  const tooltip = targetedStation
    ? getContextualTooltip(targetedStation, targetedItem, localPlayer?.heldItem ?? null, cleanPlates)
    : null;

  const servingTiles = stations.filter((s) => s.type === 'SERVING_WINDOW');

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
                color={(x + y) % 2 === 0 ? '#F6EEDB' : '#E9DEC5'}
              />
            )),
          )}

          {/* Stations */}
          {stations.map((st, idx) => {
            const px = st.x * tile;
            const py = st.y * tile;
            const cx = px + tile / 2;
            const cy = py + tile / 2;
            const hasItem = itemByCoord.has(`${st.x},${st.y}`);
            const isHighlighted =
              highlight && highlight.tx === st.x && highlight.ty === st.y && st.type !== 'WALL';

            let baseColor = '#8D5B34';
            let topColor = '#C48E5A';
            if (st.type === 'WALL') {
              baseColor = '#232830';
              topColor = '#343B47';
            } else if (st.type === 'STOVE') {
              baseColor = '#263238';
              topColor = '#455A64';
            } else if (st.type === 'SERVING_WINDOW') {
              baseColor = holdingReadyDish ? '#00C853' : '#BF360C';
              topColor = holdingReadyDish ? '#2E7D32' : '#E65100';
            } else if (st.type === 'TRASH_BIN') {
              baseColor = '#37474F';
              topColor = '#546E7A';
            } else if (st.type === 'PLATE_DISPENSER') {
              baseColor = '#607D8B';
              topColor = '#90A4AE';
            } else if (st.type.startsWith('CRATE_')) {
              baseColor = '#5D4037';
              topColor = '#795548';
            }

            return (
              <Group key={`st-${st.id}-${st.x}-${st.y}-${idx}`}>
                <Rect x={px} y={py} width={tile} height={tile} color={baseColor} />
                <RoundedRect
                  x={px + 2}
                  y={py + 2}
                  width={tile - 4}
                  height={tile - 4}
                  r={5}
                  color={topColor}
                />

                {/* Counter top highlight */}
                {st.type === 'COUNTER' && (
                  <RoundedRect
                    x={px + 5}
                    y={py + 4}
                    width={tile - 10}
                    height={4}
                    r={2}
                    color="rgba(255,255,255,0.22)"
                  />
                )}

                {/* Chopping Board + Knife */}
                {st.type === 'CHOPPING_BOARD' && (
                  <Group>
                    <RoundedRect
                      x={px + tile * 0.14}
                      y={py + tile * 0.14}
                      width={tile * 0.72}
                      height={tile * 0.56}
                      r={4}
                      color="#F5E6B8"
                    />
                    {!hasItem && (
                      <Group>
                        {/* Knife blade */}
                        <Line
                          p1={vec(px + tile * 0.32, py + tile * 0.34)}
                          p2={vec(px + tile * 0.68, py + tile * 0.52)}
                          color="#CFD8DC"
                          strokeWidth={4}
                        />
                        {/* Knife handle */}
                        <Line
                          p1={vec(px + tile * 0.64, py + tile * 0.5)}
                          p2={vec(px + tile * 0.78, py + tile * 0.57)}
                          color="#37474F"
                          strokeWidth={5}
                        />
                      </Group>
                    )}
                  </Group>
                )}

                {/* Stove Burner + Grate + Knobs */}
                {st.type === 'STOVE' && (
                  <Group>
                    <Circle cx={cx} cy={cy - tile * 0.05} r={tile * 0.28} color="#1C262B" />
                    <Circle cx={cx} cy={cy - tile * 0.05} r={tile * 0.19} color="#FF5722" />
                    <Circle cx={cx} cy={cy - tile * 0.05} r={tile * 0.11} color="#263238" />
                    <Line
                      p1={vec(cx - tile * 0.28, cy - tile * 0.05)}
                      p2={vec(cx + tile * 0.28, cy - tile * 0.05)}
                      color="#78909C"
                      strokeWidth={1.8}
                    />
                    <Line
                      p1={vec(cx, cy - tile * 0.33)}
                      p2={vec(cx, cy + tile * 0.23)}
                      color="#78909C"
                      strokeWidth={1.8}
                    />
                  </Group>
                )}

                {/* Trash Bin with slatted lid */}
                {st.type === 'TRASH_BIN' && (
                  <Group>
                    <RoundedRect
                      x={px + tile * 0.2}
                      y={py + tile * 0.14}
                      width={tile * 0.6}
                      height={tile * 0.56}
                      r={6}
                      color="#263238"
                    />
                    <Line
                      p1={vec(px + tile * 0.32, py + tile * 0.3)}
                      p2={vec(px + tile * 0.68, py + tile * 0.3)}
                      color="#78909C"
                      strokeWidth={2}
                    />
                    <Line
                      p1={vec(px + tile * 0.32, py + tile * 0.44)}
                      p2={vec(px + tile * 0.68, py + tile * 0.44)}
                      color="#78909C"
                      strokeWidth={2}
                    />
                    <Line
                      p1={vec(px + tile * 0.32, py + tile * 0.58)}
                      p2={vec(px + tile * 0.68, py + tile * 0.58)}
                      color="#78909C"
                      strokeWidth={2}
                    />
                  </Group>
                )}

                {/* Serving Window Conveyor */}
                {st.type === 'SERVING_WINDOW' && (
                  <Group>
                    <RoundedRect
                      x={px + tile * 0.1}
                      y={py + tile * 0.1}
                      width={tile * 0.8}
                      height={tile * 0.8}
                      r={5}
                      color={holdingReadyDish ? '#69F0AE' : '#FFD54F'}
                    />
                    <RoundedRect
                      x={px + tile * 0.18}
                      y={py + tile * 0.18}
                      width={tile * 0.64}
                      height={tile * 0.64}
                      r={4}
                      color={holdingReadyDish ? '#1B5E20' : '#4E342E'}
                    />
                  </Group>
                )}

                {/* Plate Dispenser */}
                {st.type === 'PLATE_DISPENSER' &&
                  Array.from({ length: Math.min(4, cleanPlates) }).map((_, pIdx) => (
                    <Circle
                      key={`disp-plate-${pIdx}`}
                      cx={cx}
                      cy={cy - tile * 0.04 + 5 - pIdx * 3.2}
                      r={tile * 0.25}
                      color={pIdx % 2 === 0 ? '#FFFFFF' : '#E0E0E0'}
                    />
                  ))}

                {/* Ingredient Crate Glyphs (shifted up slightly to leave room for label) */}
                {st.type === 'CRATE_BUN' &&
                  renderIngredientGlyph('BUN', cx, cy - tile * 0.08, tile * 0.23, `crate-${idx}`)}
                {st.type === 'CRATE_MEAT' &&
                  renderIngredientGlyph('RAW_PATTY', cx, cy - tile * 0.08, tile * 0.23, `crate-${idx}`)}
                {st.type === 'CRATE_LETTUCE' &&
                  renderIngredientGlyph('RAW_LETTUCE', cx, cy - tile * 0.08, tile * 0.23, `crate-${idx}`)}
                {st.type === 'CRATE_TOMATO' &&
                  renderIngredientGlyph('RAW_TOMATO', cx, cy - tile * 0.08, tile * 0.23, `crate-${idx}`)}
                {st.type === 'CRATE_CHEESE' &&
                  renderIngredientGlyph('RAW_CHEESE', cx, cy - tile * 0.08, tile * 0.23, `crate-${idx}`)}

                {/* Highlight facing target tile */}
                {isHighlighted && (
                  <RoundedRect
                    x={px + 2}
                    y={py + 2}
                    width={tile - 4}
                    height={tile - 4}
                    r={5}
                    color="#FFF176"
                    style="stroke"
                    strokeWidth={3.5}
                  />
                )}
              </Group>
            );
          })}

          {/* Dynamic Items & Progress Bars on Stations */}
          {stationItems.map((si) => {
            const cx = si.x * tile + tile / 2;
            const cy = si.y * tile + tile / 2 - tile * 0.04;
            const barW = tile * 0.78;
            const barH = 6;
            const barX = si.x * tile + tile * 0.11;
            const barY = si.y * tile + 3;

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

            const holdX = cx + fdx * tile * 0.4;
            const holdY = cy + fdy * tile * 0.4;

            return (
              <Group key={`chef-${p.id}`}>
                <Circle cx={cx} cy={cy + tile * 0.12} r={tile * 0.34} color="rgba(0,0,0,0.22)" />
                {isLocal && (
                  <Circle
                    cx={cx}
                    cy={cy}
                    r={tile * 0.43}
                    color="#FFF59D"
                    style="stroke"
                    strokeWidth={2.5}
                  />
                )}
                <Circle cx={cx} cy={cy} r={tile * 0.34} color={chefColor} />
                <Circle cx={cx} cy={cy - tile * 0.04} r={tile * 0.21} color="#FFFFFF" />
                <Circle
                  cx={cx + fdx * tile * 0.22}
                  cy={cy + fdy * tile * 0.22}
                  r={tile * 0.08}
                  color="#212121"
                />
                {p.heldItem && renderItem(p.heldItem, holdX, holdY, tile * 0.85, `held-${p.id}`)}
              </Group>
            );
          })}
        </Group>
      </Canvas>

      {/* Crisp Station Micro-Labels Overlay */}
      <View pointerEvents="none" style={StyleSheet.absoluteFill}>
        {stations.map((st, idx) => {
          const shortLabel = STATION_SHORT_LABEL[st.type];
          if (!shortLabel) {
            return null;
          }
          const left = offsetX + st.x * tile;
          const top = offsetY + st.y * tile + tile - 13;
          return (
            <View
              key={`lbl-${st.id}-${st.x}-${st.y}-${idx}`}
              style={[styles.stationTagWrap, { left, top, width: tile }]}
            >
              <Text style={styles.stationTagText} numberOfLines={1}>
                {shortLabel}
              </Text>
            </View>
          );
        })}

        {/* Bold Unmistakable SERVE Window Banner spanning the 2 serving tiles */}
        {servingTiles.length > 0 && (
          <View
            style={[
              styles.serveBanner,
              {
                left: offsetX + servingTiles[0].x * tile + 2,
                top: offsetY + Math.min(...servingTiles.map((s) => s.y)) * tile + 4,
                width: tile - 4,
                height: tile * servingTiles.length - 8,
              },
              holdingReadyDish && styles.serveBannerReady,
            ]}
          >
            <Text style={styles.serveArrows}>▶</Text>
            <Text style={styles.serveWord}>SERVE</Text>
            <Text style={styles.serveArrows}>▶</Text>
          </View>
        )}

        {/* Chef Name Badges */}
        {players.map((p) => (
          <View
            key={`name-${p.id}`}
            style={[
              styles.chefNamePill,
              {
                left: offsetX + p.x * tile - 28,
                top: offsetY + p.y * tile + tile * 0.38,
              },
            ]}
          >
            <Text style={styles.chefNameText} numberOfLines={1}>
              {p.name}
            </Text>
          </View>
        ))}

        {/* Contextual Floating Tooltip when facing a station */}
        {tooltip && targetedStation && (
          <View
            style={[
              styles.tooltipCard,
              {
                borderLeftColor: tooltip.accent,
                left: Math.max(
                  12,
                  Math.min(width - 210, offsetX + targetedStation.x * tile - 75),
                ),
                top: Math.max(
                  6,
                  targetedStation.y === 0
                    ? offsetY + tile + 6
                    : offsetY + targetedStation.y * tile - 42,
                ),
              },
            ]}
          >
            <Text style={[styles.tooltipTitle, { color: tooltip.accent }]}>{tooltip.title}</Text>
            <Text style={styles.tooltipHint}>{tooltip.hint}</Text>
          </View>
        )}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#1B1F27',
  },
  stationTagWrap: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
  },
  stationTagText: {
    backgroundColor: 'rgba(18, 22, 28, 0.82)',
    color: '#FFFDE7',
    fontSize: 7.5,
    fontWeight: '900',
    paddingHorizontal: 3,
    paddingVertical: 0.5,
    borderRadius: 3,
    overflow: 'hidden',
    letterSpacing: 0.3,
  },
  serveBanner: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 5,
    gap: 2,
  },
  serveBannerReady: {
    backgroundColor: 'rgba(0, 230, 118, 0.22)',
  },
  serveArrows: {
    color: '#FFD54F',
    fontSize: 11,
    fontWeight: '900',
  },
  serveWord: {
    color: '#FFFFFF',
    fontSize: 8.5,
    fontWeight: '900',
    letterSpacing: 0.6,
    backgroundColor: 'rgba(22, 25, 32, 0.85)',
    paddingHorizontal: 3,
    paddingVertical: 1,
    borderRadius: 3,
    overflow: 'hidden',
  },
  chefNamePill: {
    position: 'absolute',
    width: 56,
    alignItems: 'center',
  },
  chefNameText: {
    backgroundColor: 'rgba(22, 25, 32, 0.78)',
    color: '#FFFFFF',
    fontSize: 8.5,
    fontWeight: '800',
    paddingHorizontal: 5,
    paddingVertical: 1,
    borderRadius: 4,
    overflow: 'hidden',
  },
  tooltipCard: {
    position: 'absolute',
    width: 196,
    backgroundColor: 'rgba(18, 22, 28, 0.94)',
    borderRadius: 8,
    borderLeftWidth: 3.5,
    paddingHorizontal: 8,
    paddingVertical: 5,
    shadowColor: '#000',
    shadowOpacity: 0.35,
    shadowRadius: 4,
  },
  tooltipTitle: {
    fontSize: 10,
    fontWeight: '900',
  },
  tooltipHint: {
    color: '#ECEFF1',
    fontSize: 9.5,
    fontWeight: '600',
    marginTop: 1,
  },
});
