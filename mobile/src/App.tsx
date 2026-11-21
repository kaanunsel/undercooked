import React from 'react';
import { StatusBar, StyleSheet } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useGameStore } from './store/useGameStore';
import { ConnectScreen } from './screens/ConnectScreen';
import { LobbyScreen } from './screens/LobbyScreen';
import { GameScreen } from './screens/GameScreen';
import { ResultsScreen } from './screens/ResultsScreen';

const Stack = createNativeStackNavigator();

export default function App() {
  const connected = useGameStore((s) => s.connected);
  const roomState = useGameStore((s) => s.roomState);

  return (
    <GestureHandlerRootView style={styles.root}>
      <SafeAreaProvider>
        <StatusBar hidden />
        <NavigationContainer>
          <Stack.Navigator screenOptions={{ headerShown: false, animation: 'fade' }}>
            {!connected ? (
              <Stack.Screen name="Connect" component={ConnectScreen} />
            ) : roomState === 'PLAYING' ? (
              <Stack.Screen name="Game" component={GameScreen} />
            ) : roomState === 'FINISHED' ? (
              <Stack.Screen name="Results" component={ResultsScreen} />
            ) : (
              <Stack.Screen name="Lobby" component={LobbyScreen} />
            )}
          </Stack.Navigator>
        </NavigationContainer>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#161920',
  },
});
