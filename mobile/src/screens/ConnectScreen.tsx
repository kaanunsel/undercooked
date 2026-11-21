import React, { useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useGameStore } from '../store/useGameStore';

export const ConnectScreen: React.FC = () => {
  const {
    serverHost,
    serverPort,
    playerName,
    connecting,
    connectionError,
    setServerConfig,
    connectToServer,
    clearError,
  } = useGameStore();

  const [host, setHost] = useState(serverHost);
  const [port, setPort] = useState(String(serverPort));
  const [name, setName] = useState(playerName);

  const handleConnect = () => {
    clearError();
    const parsedPort = parseInt(port, 10) || 6789;
    setServerConfig(host.trim() || '127.0.0.1', parsedPort, name.trim() || 'Chef');
    connectToServer();
  };

  return (
    <KeyboardAvoidingView
      style={styles.root}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <View style={styles.leftCol}>
        <Text style={styles.badge}>CMPE 436 • RAW TCP MULTIPLAYER</Text>
        <Text style={styles.title}>UNDERCOOKED</Text>
        <Text style={styles.subtitle}>
          2D Cooperative Kitchen Simulator backed by a Plain-Java Multithreaded TCP ServerSocket.
        </Text>
        <View style={styles.hintBox}>
          <Text style={styles.hintTitle}>Quick Host Hints</Text>
          <Text style={styles.hintText}>• Android Emulator → 10.0.2.2</Text>
          <Text style={styles.hintText}>• iOS Simulator → 127.0.0.1</Text>
          <Text style={styles.hintText}>• Classroom Demo → undercooked.socialeventmapper.com (:6789)</Text>
        </View>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Connect to Kitchen Server</Text>

        <Text style={styles.label}>Chef Nickname</Text>
        <TextInput
          style={styles.input}
          value={name}
          onChangeText={setName}
          placeholder="e.g. Kaan"
          placeholderTextColor="#78909C"
          maxLength={16}
        />

        <View style={styles.row}>
          <View style={styles.hostWrap}>
            <Text style={styles.label}>Server IP / Host</Text>
            <TextInput
              style={styles.input}
              value={host}
              onChangeText={setHost}
              placeholder="undercooked.socialeventmapper.com"
              placeholderTextColor="#78909C"
              autoCapitalize="none"
              autoCorrect={false}
            />
          </View>
          <View style={styles.portWrap}>
            <Text style={styles.label}>TCP Port</Text>
            <TextInput
              style={styles.input}
              value={port}
              onChangeText={setPort}
              keyboardType="number-pad"
              maxLength={5}
            />
          </View>
        </View>

        {connectionError && <Text style={styles.error}>{connectionError}</Text>}

        <Pressable
          style={[styles.connectBtn, connecting && styles.connectBtnDisabled]}
          onPress={handleConnect}
          disabled={connecting}
        >
          {connecting ? (
            <ActivityIndicator color="#161920" />
          ) : (
            <Text style={styles.connectText}>CONNECT VIA TCP SOCKET</Text>
          )}
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#161920',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 28,
    gap: 28,
  },
  leftCol: {
    flex: 1,
    maxWidth: 340,
  },
  badge: {
    color: '#FFCA28',
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 1.2,
    marginBottom: 6,
  },
  title: {
    color: '#FFFFFF',
    fontSize: 36,
    fontWeight: '900',
    letterSpacing: 1.5,
  },
  subtitle: {
    color: '#B0BEC5',
    fontSize: 13,
    marginTop: 8,
    lineHeight: 18,
  },
  hintBox: {
    marginTop: 16,
    backgroundColor: '#212630',
    borderRadius: 10,
    padding: 12,
    borderWidth: 1,
    borderColor: '#2E3542',
  },
  hintTitle: {
    color: '#FFD54F',
    fontSize: 11,
    fontWeight: '800',
    marginBottom: 4,
  },
  hintText: {
    color: '#CFD8DC',
    fontSize: 11,
    lineHeight: 16,
  },
  card: {
    flex: 1,
    maxWidth: 370,
    backgroundColor: '#212630',
    borderRadius: 14,
    padding: 18,
    borderWidth: 1,
    borderColor: '#313949',
  },
  cardTitle: {
    color: '#FFFFFF',
    fontSize: 17,
    fontWeight: '800',
    marginBottom: 12,
  },
  label: {
    color: '#90A4AE',
    fontSize: 11,
    fontWeight: '700',
    marginBottom: 4,
    marginTop: 6,
  },
  input: {
    backgroundColor: '#161920',
    color: '#FFFFFF',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#374151',
    paddingHorizontal: 10,
    paddingVertical: 8,
    fontSize: 14,
  },
  row: {
    flexDirection: 'row',
    gap: 10,
  },
  hostWrap: {
    flex: 2,
  },
  portWrap: {
    flex: 1,
  },
  error: {
    color: '#FF5252',
    fontSize: 12,
    fontWeight: '700',
    marginTop: 10,
  },
  connectBtn: {
    marginTop: 16,
    backgroundColor: '#FF7043',
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: 'center',
  },
  connectBtnDisabled: {
    opacity: 0.65,
  },
  connectText: {
    color: '#161920',
    fontWeight: '900',
    fontSize: 14,
    letterSpacing: 0.8,
  },
});
