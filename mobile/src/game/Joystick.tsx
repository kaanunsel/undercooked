import React, { useRef } from 'react';
import { StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { runOnJS, useAnimatedStyle, useSharedValue } from 'react-native-reanimated';
import type { Facing } from '../types/protocol';

interface JoystickProps {
  onMove: (dx: number, dy: number, facing?: Facing) => void;
}

const OUTER_RADIUS = 56;
const KNOB_RADIUS = 24;
const MAX_DIST = OUTER_RADIUS - KNOB_RADIUS + 4;
const SEND_INTERVAL_MS = 50; // 20 Hz

export const Joystick: React.FC<JoystickProps> = ({ onMove }) => {
  const knobX = useSharedValue(0);
  const knobY = useSharedValue(0);
  const lastSentRef = useRef(0);

  const emitMove = (nx: number, ny: number, force = false) => {
    const now = Date.now();
    if (!force && now - lastSentRef.current < SEND_INTERVAL_MS) {
      return;
    }
    lastSentRef.current = now;
    let facing: Facing | undefined;
    if (Math.hypot(nx, ny) > 0.15) {
      if (Math.abs(nx) > Math.abs(ny)) {
        facing = nx > 0 ? 'E' : 'W';
      } else {
        facing = ny > 0 ? 'S' : 'N';
      }
    }
    onMove(nx, ny, facing);
  };

  const pan = Gesture.Pan()
    .onUpdate((e) => {
      const dist = Math.hypot(e.translationX, e.translationY);
      const clamped = Math.min(dist, MAX_DIST);
      const angle = Math.atan2(e.translationY, e.translationX);
      const cx = dist > 0.01 ? Math.cos(angle) * clamped : 0;
      const cy = dist > 0.01 ? Math.sin(angle) * clamped : 0;
      knobX.value = cx;
      knobY.value = cy;
      runOnJS(emitMove)(cx / MAX_DIST, cy / MAX_DIST, false);
    })
    .onEnd(() => {
      knobX.value = 0;
      knobY.value = 0;
      runOnJS(emitMove)(0, 0, true);
    })
    .onFinalize(() => {
      knobX.value = 0;
      knobY.value = 0;
      runOnJS(emitMove)(0, 0, true);
    });

  const knobStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: knobX.value }, { translateY: knobY.value }],
  }));

  return (
    <GestureDetector gesture={pan}>
      <View style={styles.outer}>
        <Animated.View style={[styles.knob, knobStyle]} />
      </View>
    </GestureDetector>
  );
};

const styles = StyleSheet.create({
  outer: {
    width: OUTER_RADIUS * 2,
    height: OUTER_RADIUS * 2,
    borderRadius: OUTER_RADIUS,
    backgroundColor: 'rgba(255, 255, 255, 0.14)',
    borderWidth: 2,
    borderColor: 'rgba(255, 255, 255, 0.35)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  knob: {
    width: KNOB_RADIUS * 2,
    height: KNOB_RADIUS * 2,
    borderRadius: KNOB_RADIUS,
    backgroundColor: 'rgba(255, 193, 7, 0.9)',
    borderWidth: 2,
    borderColor: '#FFF8E1',
  },
});
