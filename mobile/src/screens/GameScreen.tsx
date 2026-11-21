import React, { useState } from 'react';
import { LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { ActionButtons } from '../game/ActionButtons';
import { Joystick } from '../game/Joystick';
import { KitchenCanvas } from '../game/KitchenCanvas';
import { OrderBar } from '../game/OrderBar';
import { useGameStore } from '../store/useGameStore';

export const GameScreen: React.FC = () => {
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

  const onCanvasLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setCanvasSize({ width, height });
  };

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

      <View style={styles.stage} onLayout={onCanvasLayout}>
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

        {/* Small Leave Button */}
        <Pressable style={styles.leaveBtn} onPress={leaveRoom}>
          <Text style={styles.leaveText}>✕</Text>
        </Pressable>

        {/* Left Virtual Joystick */}
        <View style={styles.joystickWrap}>
          <Joystick onMove={sendMove} />
        </View>

        {/* Right Action Buttons */}
        <View style={styles.actionsWrap}>
          <ActionButtons onInteract={sendInteract} onChopChange={sendChop} />
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#161920',
  },
  stage: {
    flex: 1,
    position: 'relative',
  },
  joystickWrap: {
    position: 'absolute',
    left: 20,
    bottom: 18,
  },
  actionsWrap: {
    position: 'absolute',
    right: 20,
    bottom: 16,
  },
  leaveBtn: {
    position: 'absolute',
    top: 8,
    left: 10,
    width: 30,
    height: 30,
    borderRadius: 15,
    backgroundColor: 'rgba(22, 25, 32, 0.72)',
    borderWidth: 1,
    borderColor: '#455A64',
    alignItems: 'center',
    justifyContent: 'center',
  },
  leaveText: {
    color: '#ECEFF1',
    fontWeight: '800',
    fontSize: 13,
  },
  toast: {
    position: 'absolute',
    top: 10,
    alignSelf: 'center',
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 16,
  },
  toastGood: {
    backgroundColor: 'rgba(46, 125, 50, 0.92)',
  },
  toastBad: {
    backgroundColor: 'rgba(198, 40, 40, 0.92)',
  },
  toastInfo: {
    backgroundColor: 'rgba(38, 50, 56, 0.92)',
  },
  toastText: {
    color: '#FFFFFF',
    fontWeight: '800',
    fontSize: 12,
  },
});
