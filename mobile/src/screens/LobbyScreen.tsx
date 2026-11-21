import React, { useState } from 'react';
import {
  FlatList,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useGameStore } from '../store/useGameStore';

const SEAT_COLORS = ['#E53935', '#1E88E5', '#43A047', '#F9A825'];

export const LobbyScreen: React.FC = () => {
  const {
    playerId,
    playerName,
    openRooms,
    roomCode,
    hostId,
    durationSec,
    lobbyPlayers,
    connectionError,
    refreshRooms,
    createRoom,
    joinRoom,
    toggleReady,
    startGame,
    leaveRoom,
    disconnectFromServer,
  } = useGameStore();

  const [joinCode, setJoinCode] = useState('');
  const [selectedDuration, setSelectedDuration] = useState<90 | 180>(180);

  if (roomCode) {
    const isHost = playerId === hostId;
    const me = lobbyPlayers.find((p) => p.id === playerId);
    const allReady = lobbyPlayers.length <= 1 || lobbyPlayers.every((p) => p.ready);

    return (
      <View style={styles.root}>
        <View style={styles.header}>
          <View>
            <Text style={styles.subLabel}>KITCHEN LOBBY</Text>
            <Text style={styles.roomBadge}>ROOM CODE: {roomCode}</Text>
          </View>
          <View style={styles.headerRight}>
            <Text style={styles.metaPill}>Shift: {durationSec}s</Text>
            <Pressable style={styles.ghostBtn} onPress={leaveRoom}>
              <Text style={styles.ghostText}>LEAVE ROOM</Text>
            </Pressable>
          </View>
        </View>

        <View style={styles.seatsRow}>
          {[0, 1, 2, 3].map((slot) => {
            const p = lobbyPlayers.find((lp) => lp.slot === slot);
            return (
              <View
                key={slot}
                style={[styles.seatCard, { borderTopColor: SEAT_COLORS[slot] }]}
              >
                <Text style={[styles.seatTag, { color: SEAT_COLORS[slot] }]}>
                  CHEF #{slot + 1}
                </Text>
                {p ? (
                  <>
                    <Text style={styles.seatName} numberOfLines={1}>
                      {p.name}
                      {p.id === hostId ? ' ★' : ''}
                    </Text>
                    <Text style={[styles.readyTag, p.ready ? styles.readyYes : styles.readyNo]}>
                      {p.ready ? 'READY' : 'NOT READY'}
                    </Text>
                  </>
                ) : (
                  <Text style={styles.seatEmpty}>Open Seat</Text>
                )}
              </View>
            );
          })}
        </View>

        {connectionError && <Text style={styles.error}>{connectionError}</Text>}

        <View style={styles.footerActions}>
          <Pressable
            style={[styles.actionBtn, me?.ready ? styles.unreadyBtn : styles.readyBtn]}
            onPress={toggleReady}
          >
            <Text style={styles.actionText}>{me?.ready ? 'CANCEL READY' : 'MARK READY'}</Text>
          </Pressable>

          {isHost && (
            <Pressable
              style={[styles.actionBtn, styles.startBtn, !allReady && styles.disabledBtn]}
              onPress={startGame}
            >
              <Text style={styles.actionText}>
                {lobbyPlayers.length === 1 ? 'START SOLO SHIFT' : 'START CO-OP SHIFT'}
              </Text>
            </Pressable>
          )}
        </View>
      </View>
    );
  }

  return (
    <View style={styles.root}>
      <View style={styles.header}>
        <View>
          <Text style={styles.subLabel}>CONNECTED AS {playerName.toUpperCase()}</Text>
          <Text style={styles.roomBadge}>Kitchen Directory</Text>
        </View>
        <View style={styles.headerRight}>
          <Pressable style={styles.ghostBtn} onPress={refreshRooms}>
            <Text style={styles.ghostText}>REFRESH</Text>
          </Pressable>
          <Pressable style={styles.ghostBtn} onPress={disconnectFromServer}>
            <Text style={styles.ghostText}>DISCONNECT</Text>
          </Pressable>
        </View>
      </View>

      <View style={styles.columns}>
        {/* Create or Join by Code */}
        <View style={styles.panel}>
          <Text style={styles.panelTitle}>Create New Kitchen</Text>
          <View style={styles.durRow}>
            {([90, 180] as const).map((sec) => (
              <Pressable
                key={sec}
                style={[styles.durChip, selectedDuration === sec && styles.durChipActive]}
                onPress={() => setSelectedDuration(sec)}
              >
                <Text style={styles.durText}>
                  {sec === 90 ? '90s Demo' : '180s Standard'}
                </Text>
              </Pressable>
            ))}
          </View>
          <Pressable style={styles.primaryBtn} onPress={() => createRoom(selectedDuration)}>
            <Text style={styles.primaryBtnText}>CREATE ROOM</Text>
          </Pressable>

          <Text style={[styles.panelTitle, { marginTop: 14 }]}>Join by Code</Text>
          <View style={styles.joinRow}>
            <TextInput
              style={styles.codeInput}
              value={joinCode}
              onChangeText={setJoinCode}
              placeholder="K4M2"
              placeholderTextColor="#78909C"
              autoCapitalize="characters"
              maxLength={4}
            />
            <Pressable
              style={styles.joinBtn}
              onPress={() => joinCode.trim() && joinRoom(joinCode)}
            >
              <Text style={styles.primaryBtnText}>JOIN</Text>
            </Pressable>
          </View>
          {connectionError && <Text style={styles.error}>{connectionError}</Text>}
        </View>

        {/* Open Rooms List */}
        <View style={styles.panel}>
          <Text style={styles.panelTitle}>Open Rooms ({openRooms.length})</Text>
          <FlatList
            data={openRooms}
            keyExtractor={(item) => item.code}
            ListEmptyComponent={
              <Text style={styles.emptyList}>No open rooms yet. Create one to start!</Text>
            }
            renderItem={({ item }) => (
              <Pressable style={styles.roomRow} onPress={() => joinRoom(item.code)}>
                <View>
                  <Text style={styles.roomRowCode}>{item.code}</Text>
                  <Text style={styles.roomRowMeta}>
                    {item.players}/{item.maxPlayers} Chefs • {item.durationSec}s
                  </Text>
                </View>
                <Text style={styles.joinPill}>JOIN →</Text>
              </Pressable>
            )}
          />
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#161920',
    paddingHorizontal: 22,
    paddingVertical: 14,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  headerRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  subLabel: {
    color: '#FFCA28',
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 1,
  },
  roomBadge: {
    color: '#FFFFFF',
    fontSize: 22,
    fontWeight: '900',
  },
  metaPill: {
    color: '#CFD8DC',
    backgroundColor: '#263238',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 6,
    fontSize: 12,
    fontWeight: '700',
  },
  ghostBtn: {
    borderWidth: 1,
    borderColor: '#455A64',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  ghostText: {
    color: '#ECEFF1',
    fontSize: 11,
    fontWeight: '800',
  },
  columns: {
    flex: 1,
    flexDirection: 'row',
    gap: 16,
  },
  panel: {
    flex: 1,
    backgroundColor: '#212630',
    borderRadius: 12,
    padding: 14,
    borderWidth: 1,
    borderColor: '#303846',
  },
  panelTitle: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '800',
    marginBottom: 8,
  },
  durRow: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 10,
  },
  durChip: {
    flex: 1,
    backgroundColor: '#161920',
    borderRadius: 8,
    paddingVertical: 8,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#374151',
  },
  durChipActive: {
    borderColor: '#FFCA28',
    backgroundColor: '#2E2A1E',
  },
  durText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '700',
  },
  primaryBtn: {
    backgroundColor: '#FF7043',
    borderRadius: 8,
    paddingVertical: 10,
    alignItems: 'center',
  },
  primaryBtnText: {
    color: '#161920',
    fontWeight: '900',
    fontSize: 13,
  },
  joinRow: {
    flexDirection: 'row',
    gap: 8,
  },
  codeInput: {
    flex: 1,
    backgroundColor: '#161920',
    color: '#FFFFFF',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#374151',
    paddingHorizontal: 12,
    paddingVertical: 8,
    fontSize: 16,
    fontWeight: '800',
    letterSpacing: 3,
  },
  joinBtn: {
    backgroundColor: '#26A69A',
    borderRadius: 8,
    paddingHorizontal: 20,
    justifyContent: 'center',
  },
  emptyList: {
    color: '#78909C',
    fontSize: 12,
    marginTop: 12,
  },
  roomRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#161920',
    borderRadius: 8,
    padding: 10,
    marginBottom: 8,
  },
  roomRowCode: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '900',
    letterSpacing: 2,
  },
  roomRowMeta: {
    color: '#90A4AE',
    fontSize: 11,
  },
  joinPill: {
    color: '#26C6DA',
    fontWeight: '800',
    fontSize: 12,
  },
  seatsRow: {
    flex: 1,
    flexDirection: 'row',
    gap: 12,
    alignItems: 'center',
  },
  seatCard: {
    flex: 1,
    backgroundColor: '#212630',
    borderRadius: 12,
    borderTopWidth: 5,
    padding: 14,
    minHeight: 118,
    justifyContent: 'space-between',
  },
  seatTag: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1,
  },
  seatName: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '800',
  },
  seatEmpty: {
    color: '#607D8B',
    fontSize: 13,
    fontStyle: 'italic',
  },
  readyTag: {
    fontSize: 11,
    fontWeight: '900',
  },
  readyYes: {
    color: '#66BB6A',
  },
  readyNo: {
    color: '#FFA726',
  },
  footerActions: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 14,
    marginTop: 12,
  },
  actionBtn: {
    paddingHorizontal: 28,
    paddingVertical: 12,
    borderRadius: 10,
  },
  readyBtn: {
    backgroundColor: '#26A69A',
  },
  unreadyBtn: {
    backgroundColor: '#546E7A',
  },
  startBtn: {
    backgroundColor: '#FF7043',
  },
  disabledBtn: {
    opacity: 0.5,
  },
  actionText: {
    color: '#FFFFFF',
    fontWeight: '900',
    fontSize: 14,
  },
  error: {
    color: '#FF5252',
    fontSize: 12,
    fontWeight: '700',
    marginTop: 8,
    textAlign: 'center',
  },
});
