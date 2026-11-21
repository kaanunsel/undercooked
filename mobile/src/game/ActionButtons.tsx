import React, { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

interface ActionButtonsProps {
  onInteract: () => void;
  onChopChange: (active: boolean) => void;
}

export const ActionButtons: React.FC<ActionButtonsProps> = ({ onInteract, onChopChange }) => {
  const [chopping, setChopping] = useState(false);

  return (
    <View style={styles.container}>
      <Pressable
        onPressIn={() => {
          setChopping(true);
          onChopChange(true);
        }}
        onPressOut={() => {
          setChopping(false);
          onChopChange(false);
        }}
        style={[styles.btn, styles.chopBtn, chopping && styles.chopBtnActive]}
      >
        <Text style={styles.btnTitle}>CHOP</Text>
        <Text style={styles.btnSub}>HOLD</Text>
      </Pressable>

      <Pressable
        onPressIn={onInteract}
        style={({ pressed }) => [
          styles.btn,
          styles.grabBtn,
          pressed && styles.grabBtnPressed,
        ]}
      >
        <Text style={styles.btnTitle}>GRAB</Text>
        <Text style={styles.btnSub}>DROP / SERVE</Text>
      </Pressable>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    gap: 12,
  },
  btn: {
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2.5,
  },
  grabBtn: {
    width: 86,
    height: 86,
    borderRadius: 43,
    backgroundColor: 'rgba(255, 112, 67, 0.92)',
    borderColor: '#FFCCBC',
  },
  grabBtnPressed: {
    transform: [{ scale: 0.93 }],
    backgroundColor: '#F4511E',
  },
  chopBtn: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: 'rgba(38, 166, 154, 0.9)',
    borderColor: '#B2DFDB',
  },
  chopBtnActive: {
    transform: [{ scale: 0.93 }],
    backgroundColor: '#00897B',
  },
  btnTitle: {
    color: '#FFFFFF',
    fontWeight: '800',
    fontSize: 15,
    letterSpacing: 0.5,
  },
  btnSub: {
    color: '#FFFDE7',
    fontWeight: '600',
    fontSize: 9,
    marginTop: 2,
  },
});
