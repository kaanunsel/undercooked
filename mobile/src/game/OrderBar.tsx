import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import type { ItemType, OrderDto } from '../types/protocol';

interface OrderBarProps {
  orders: OrderDto[];
  score: number;
  combo: number;
  timeLeftMs: number;
  cleanPlates: number;
  washingPlates: number;
}

const ING_BADGE: Record<string, { label: string; color: string }> = {
  BUN: { label: 'BUN', color: '#E6A14A' },
  COOKED_PATTY: { label: 'PTY', color: '#795548' },
  CHOPPED_LETTUCE: { label: 'LET', color: '#43A047' },
  CHOPPED_TOMATO: { label: 'TOM', color: '#E53935' },
  SLICED_CHEESE: { label: 'CHS', color: '#FBC02D' },
};

function formatClock(ms: number): string {
  const totalSec = Math.max(0, Math.ceil(ms / 1000));
  const mins = Math.floor(totalSec / 60);
  const secs = totalSec % 60;
  return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
}

export const OrderBar: React.FC<OrderBarProps> = ({
  orders,
  score,
  combo,
  timeLeftMs,
  cleanPlates,
  washingPlates,
}) => {
  const insets = useSafeAreaInsets();
  const padLeft = Math.max(24, insets.left);
  const padRight = Math.max(24, insets.right);

  return (
    <View style={[styles.bar, { paddingLeft: padLeft, paddingRight: padRight }]}>
      <View style={styles.ordersRow}>
        {orders.length === 0 ? (
          <Text style={styles.waitingText}>Waiting for orders...</Text>
        ) : (
          orders.map((o) => {
            const ratio = o.durationMs > 0 ? Math.max(0, Math.min(1, o.remainingMs / o.durationMs)) : 0;
            const barColor = ratio > 0.5 ? '#43A047' : ratio > 0.25 ? '#FB8C00' : '#E53935';
            return (
              <View key={o.id} style={styles.ticket}>
                <View style={styles.ticketHeader}>
                  <Text style={styles.ticketTitle} numberOfLines={1}>
                    {o.name}
                  </Text>
                  <Text style={styles.ticketPts}>+{o.basePoints}</Text>
                </View>
                <View style={styles.ingRow}>
                  {o.ingredients.map((ing: ItemType, i) => {
                    const meta = ING_BADGE[ing] ?? { label: ing.slice(0, 3), color: '#607D8B' };
                    return (
                      <View key={`${o.id}-${i}`} style={[styles.pill, { backgroundColor: meta.color }]}>
                        <Text style={styles.pillText}>{meta.label}</Text>
                      </View>
                    );
                  })}
                </View>
                <View style={styles.timerTrack}>
                  <View style={[styles.timerFill, { width: `${ratio * 100}%`, backgroundColor: barColor }]} />
                </View>
              </View>
            );
          })
        )}
      </View>

      <View style={styles.statsBox}>
        <View style={styles.statItem}>
          <Text style={styles.statLabel}>PLATES</Text>
          <Text style={styles.statVal}>
            {cleanPlates}/4{washingPlates > 0 ? ` (${washingPlates}w)` : ''}
          </Text>
        </View>
        <View style={styles.statItem}>
          <Text style={styles.statLabel}>SCORE</Text>
          <Text style={styles.scoreVal}>
            {score}
            {combo >= 2 ? ` x${combo}` : ''}
          </Text>
        </View>
        <View style={[styles.clockPill, timeLeftMs < 20_000 && styles.clockUrgent]}>
          <Text style={styles.clockText}>{formatClock(timeLeftMs)}</Text>
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  bar: {
    height: 60,
    backgroundColor: '#14171D',
    borderBottomWidth: 1,
    borderBottomColor: '#2B313D',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
  },
  ordersRow: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    overflow: 'hidden',
  },
  waitingText: {
    color: '#90A4AE',
    fontSize: 12,
    fontStyle: 'italic',
  },
  ticket: {
    width: 106,
    backgroundColor: '#FFFDF7',
    borderRadius: 6,
    paddingHorizontal: 6,
    paddingVertical: 4,
  },
  ticketHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  ticketTitle: {
    fontSize: 9.5,
    fontWeight: '800',
    color: '#263238',
    maxWidth: 70,
  },
  ticketPts: {
    fontSize: 9,
    fontWeight: '800',
    color: '#E65100',
  },
  ingRow: {
    flexDirection: 'row',
    gap: 2.5,
    marginTop: 3,
    marginBottom: 4,
  },
  pill: {
    borderRadius: 3,
    paddingHorizontal: 3,
    paddingVertical: 1,
  },
  pillText: {
    color: '#FFFFFF',
    fontSize: 7.5,
    fontWeight: '800',
  },
  timerTrack: {
    height: 4,
    backgroundColor: '#E0E0E0',
    borderRadius: 2,
    overflow: 'hidden',
  },
  timerFill: {
    height: 4,
    borderRadius: 2,
  },
  statsBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  statItem: {
    alignItems: 'center',
  },
  statLabel: {
    fontSize: 8,
    color: '#90A4AE',
    fontWeight: '700',
  },
  statVal: {
    fontSize: 13,
    color: '#ECEFF1',
    fontWeight: '800',
  },
  scoreVal: {
    fontSize: 15,
    color: '#FFD54F',
    fontWeight: '900',
  },
  clockPill: {
    backgroundColor: '#2E3440',
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  clockUrgent: {
    backgroundColor: '#C62828',
  },
  clockText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '900',
    fontVariant: ['tabular-nums'],
  },
});
