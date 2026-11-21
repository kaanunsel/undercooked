import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useGameStore } from '../store/useGameStore';

export const ResultsScreen: React.FC = () => {
  const { gameOver, playerId, hostId, startGame, returnToLobby, leaveRoom } = useGameStore();
  if (!gameOver) {
    return null;
  }

  const isHost = playerId === hostId;
  const starsDisplay = '★'.repeat(gameOver.stars) + '☆'.repeat(Math.max(0, 3 - gameOver.stars));

  return (
    <View style={styles.root}>
      <View style={styles.summaryCol}>
        <Text style={styles.label}>SHIFT COMPLETE</Text>
        <Text style={styles.stars}>{starsDisplay}</Text>
        <Text style={styles.score}>{gameOver.score} PTS</Text>
        <Text style={styles.meta}>
          Orders Served: {gameOver.ordersCompleted}  •  Expired: {gameOver.ordersExpired}
        </Text>

        <View style={styles.btnRow}>
          {isHost && (
            <>
              <Pressable style={styles.primaryBtn} onPress={startGame}>
                <Text style={styles.primaryText}>PLAY AGAIN</Text>
              </Pressable>
              <Pressable style={styles.secondaryBtn} onPress={returnToLobby}>
                <Text style={styles.secondaryText}>LOBBY</Text>
              </Pressable>
            </>
          )}
          <Pressable style={styles.secondaryBtn} onPress={leaveRoom}>
            <Text style={styles.secondaryText}>LEAVE</Text>
          </Pressable>
        </View>
      </View>

      <View style={styles.statsCard}>
        <Text style={styles.statsTitle}>Chef Contributions</Text>
        {gameOver.playerStats.map((ps) => (
          <View key={ps.id} style={styles.statRow}>
            <Text style={styles.chefName}>{ps.name}</Text>
            <Text style={styles.chefNumbers}>
              Served {ps.ordersServed}  •  Chopped {ps.itemsChopped}
            </Text>
          </View>
        ))}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#161920',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 32,
    gap: 28,
  },
  summaryCol: {
    flex: 1,
    alignItems: 'center',
  },
  label: {
    color: '#90A4AE',
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 1.5,
  },
  stars: {
    color: '#FFD54F',
    fontSize: 44,
    marginVertical: 4,
  },
  score: {
    color: '#FFFFFF',
    fontSize: 34,
    fontWeight: '900',
  },
  meta: {
    color: '#B0BEC5',
    fontSize: 13,
    marginTop: 6,
  },
  btnRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 20,
  },
  primaryBtn: {
    backgroundColor: '#FF7043',
    borderRadius: 10,
    paddingHorizontal: 22,
    paddingVertical: 11,
  },
  primaryText: {
    color: '#161920',
    fontWeight: '900',
    fontSize: 13,
  },
  secondaryBtn: {
    borderWidth: 1,
    borderColor: '#546E7A',
    borderRadius: 10,
    paddingHorizontal: 18,
    paddingVertical: 11,
  },
  secondaryText: {
    color: '#ECEFF1',
    fontWeight: '800',
    fontSize: 13,
  },
  statsCard: {
    flex: 1,
    backgroundColor: '#212630',
    borderRadius: 14,
    padding: 18,
    borderWidth: 1,
    borderColor: '#313949',
  },
  statsTitle: {
    color: '#FFD54F',
    fontSize: 15,
    fontWeight: '800',
    marginBottom: 12,
  },
  statRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#2C3340',
  },
  chefName: {
    color: '#FFFFFF',
    fontWeight: '800',
    fontSize: 14,
  },
  chefNumbers: {
    color: '#B0BEC5',
    fontSize: 13,
  },
});
