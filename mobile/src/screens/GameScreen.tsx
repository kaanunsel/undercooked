import React, { useEffect, useState } from 'react';
import { LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ActionButtons } from '../game/ActionButtons';
import { Joystick } from '../game/Joystick';
import { KitchenCanvas } from '../game/KitchenCanvas';
import { OrderBar } from '../game/OrderBar';
import {
  isAudioMuted,
  startBackgroundMusic,
  stopBackgroundMusic,
  toggleAudioMute,
} from '../game/soundManager';
import { useGameStore } from '../store/useGameStore';
import type { ItemDto } from '../types/protocol';

function describeHeldItem(item: ItemDto | null | undefined): { label: string; readyToServe: boolean } | null {
  if (!item) {
    return null;
  }
  if (item.type === 'PLATE') {
    if (item.recipe) {
      const nice = item.recipe.replace('_', ' ');
      return { label: `READY: ${nice} → Bring to SERVE ▶`, readyToServe: true };
    }
    const list = (item.contents ?? []).map((c) => c.replace('CHOPPED_', '').replace('SLICED_', '').replace('COOKED_', ''));
    return {
      label: list.length > 0 ? `Plate: ${list.join(' + ')}` : 'Empty Plate',
      readyToServe: false,
    };
  }
  return { label: `Holding: ${item.type.replace('_', ' ')}`, readyToServe: false };
}

export const GameScreen: React.FC = () => {
  const insets = useSafeAreaInsets();
  const {
    playerId,
    gridW,
    gridH,
    stations,
    stationItems,
    players,
    orders,
    score,
    combo,
    timeLeftMs,
    cleanPlates,
    washingPlates,
    toast,
    sendMove,
    sendInteract,
    sendChop,
    leaveRoom,
  } = useGameStore();

  const [canvasSize, setCanvasSize] = useState({ width: 0, height: 0 });
  const [muted, setMuted] = useState(isAudioMuted());

  useEffect(() => {
    startBackgroundMusic();
    return () => {
      stopBackgroundMusic();
    };
  }, []);

  const onCanvasLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setCanvasSize({ width, height });
  };

  const localPlayer = players.find((p) => p.id === playerId);
  const heldSummary = describeHeldItem(localPlayer?.heldItem);

  const leftPad = Math.max(16, insets.left);
  const rightPad = Math.max(16, insets.right);

  return (
    <View style={styles.root}>
      <OrderBar
        orders={orders}
        score={score}
        combo={combo}
        timeLeftMs={timeLeftMs}
        cleanPlates={cleanPlates}
        washingPlates={washingPlates}
      />

      <View style={[styles.stage, { paddingBottom: Math.max(6, insets.bottom * 0.4) }]} onLayout={onCanvasLayout}>
        <KitchenCanvas
          width={canvasSize.width}
          height={canvasSize.height}
          gridW={gridW}
          gridH={gridH}
          stations={stations}
          stationItems={stationItems}
          players={players}
          localPlayerId={playerId}
          cleanPlates={cleanPlates}
        />

        {/* Floating Event Toast */}
        {toast && (
          <View
            pointerEvents="none"
            style={[
              styles.toast,
              toast.tone === 'good'
                ? styles.toastGood
                : toast.tone === 'bad'
                  ? styles.toastBad
                  : styles.toastInfo,
            ]}
          >
            <Text style={styles.toastText}>{toast.text}</Text>
          </View>
        )}

        {/* Top-Left Utility Buttons: Leave + Audio Mute */}
        <View style={[styles.topLeftBtns, { left: leftPad }]}>
          <Pressable style={styles.utilBtn} onPress={leaveRoom}>
            <Text style={styles.utilText}>✕</Text>
          </Pressable>
          <Pressable
            style={[styles.utilBtn, muted && styles.utilBtnMuted]}
            onPress={() => setMuted(toggleAudioMute())}
          >
            <Text style={styles.utilText}>{muted ? '🔇' : '🔊'}</Text>
          </Pressable>
        </View>

        {/* Bottom-Center Held Item Status Pill */}
        {heldSummary && (
          <View
            pointerEvents="none"
            style={[styles.heldPill, heldSummary.readyToServe && styles.heldPillReady]}
          >
            <Text style={[styles.heldText, heldSummary.readyToServe && styles.heldTextReady]}>
              {heldSummary.label}
            </Text>
          </View>
        )}

        {/* Left Virtual Joystick */}
        <View style={[styles.joystickWrap, { left: leftPad + 4 }]}>
          <Joystick onMove={sendMove} />
        </View>

        {/* Right Action Buttons */}
        <View style={[styles.actionsWrap, { right: rightPad + 4 }]}>
          <ActionButtons onInteract={sendInteract} onChopChange={sendChop} />
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#14171D',
  },
  stage: {
    flex: 1,
    position: 'relative',
  },
  joystickWrap: {
    position: 'absolute',
    bottom: 18,
  },
  actionsWrap: {
    position: 'absolute',
    bottom: 16,
  },
  topLeftBtns: {
    position: 'absolute',
    top: 8,
    flexDirection: 'row',
    gap: 8,
  },
  utilBtn: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: 'rgba(20, 23, 29, 0.8)',
    borderWidth: 1,
    borderColor: '#455A64',
    alignItems: 'center',
    justifyContent: 'center',
  },
  utilBtnMuted: {
    borderColor: '#EF5350',
  },
  utilText: {
    color: '#ECEFF1',
    fontWeight: '800',
    fontSize: 13,
  },
  heldPill: {
    position: 'absolute',
    bottom: 8,
    alignSelf: 'center',
    backgroundColor: 'rgba(18, 22, 28, 0.88)',
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#455A64',
    paddingHorizontal: 12,
    paddingVertical: 4,
  },
  heldPillReady: {
    backgroundColor: 'rgba(27, 94, 32, 0.94)',
    borderColor: '#69F0AE',
  },
  heldText: {
    color: '#FFFDE7',
    fontSize: 11,
    fontWeight: '800',
  },
  heldTextReady: {
    color: '#B9F6CA',
  },
  toast: {
    position: 'absolute',
    top: 8,
    alignSelf: 'center',
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 16,
  },
  toastGood: {
    backgroundColor: 'rgba(46, 125, 50, 0.94)',
  },
  toastBad: {
    backgroundColor: 'rgba(198, 40, 40, 0.94)',
  },
  toastInfo: {
    backgroundColor: 'rgba(38, 50, 56, 0.94)',
  },
  toastText: {
    color: '#FFFFFF',
    fontWeight: '800',
    fontSize: 12,
  },
});
