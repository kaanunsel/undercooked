import { create } from 'zustand';
import { tcpClient } from '../net/tcpClient';
import { playGameSound } from '../game/soundManager';
import type {
  Facing,
  LobbyPlayerDto,
  OrderDto,
  PlayerSnapshotDto,
  PlayerStatsDto,
  RoomState,
  RoomSummaryDto,
  ServerMessage,
  StationItemDto,
  StationStaticDto,
} from '../types/protocol';

export interface GameOverData {
  score: number;
  stars: number;
  ordersCompleted: number;
  ordersExpired: number;
  playerStats: PlayerStatsDto[];
}

export interface ToastNotice {
  id: number;
  text: string;
  tone: 'info' | 'good' | 'bad';
}

interface GameStore {
  // Connection state
  serverHost: string;
  serverPort: number;
  playerName: string;
  connected: boolean;
  connecting: boolean;
  connectionError: string | null;
  playerId: string | null;

  // Room directory & lobby
  openRooms: RoomSummaryDto[];
  roomCode: string | null;
  hostId: string | null;
  roomState: RoomState | null;
  durationSec: number;
  lobbyPlayers: LobbyPlayerDto[];

  // Live kitchen state
  gridW: number;
  gridH: number;
  stations: StationStaticDto[];
  lastSeq: number;
  timeLeftMs: number;
  score: number;
  combo: number;
  cleanPlates: number;
  washingPlates: number;
  players: PlayerSnapshotDto[];
  stationItems: StationItemDto[];
  orders: OrderDto[];
  toast: ToastNotice | null;

  // End of shift
  gameOver: GameOverData | null;

  // Actions
  setServerConfig: (host: string, port: number, name: string) => void;
  connectToServer: () => void;
  disconnectFromServer: () => void;
  refreshRooms: () => void;
  createRoom: (durationSec: number) => void;
  joinRoom: (code: string) => void;
  toggleReady: () => void;
  startGame: () => void;
  returnToLobby: () => void;
  leaveRoom: () => void;
  sendMove: (dx: number, dy: number, facing?: Facing) => void;
  sendInteract: () => void;
  sendChop: (active: boolean) => void;
  clearError: () => void;
}

let toastCounter = 1;
let initializedListeners = false;

export const useGameStore = create<GameStore>((set, get) => {
  if (!initializedListeners) {
    initializedListeners = true;

    tcpClient.onStatus((connected, error) => {
      if (connected) {
        const { playerName } = get();
        set({ connected: true, connecting: false, connectionError: null });
        tcpClient.send({ type: 'HELLO', name: playerName.trim() || 'Chef' });
        tcpClient.send({ type: 'LIST_ROOMS' });
      } else {
        set({
          connected: false,
          connecting: false,
          connectionError: error ?? null,
          roomCode: null,
          roomState: null,
          lobbyPlayers: [],
          players: [],
          orders: [],
          gameOver: null,
        });
      }
    });

    tcpClient.onMessage((msg: ServerMessage) => {
      switch (msg.type) {
        case 'WELCOME':
          set({ playerId: msg.playerId, connectionError: null });
          break;

        case 'ROOM_LIST':
          set({ openRooms: msg.rooms });
          break;

        case 'LOBBY_STATE':
          set({
            roomCode: msg.roomCode,
            hostId: msg.hostId,
            roomState: msg.state,
            durationSec: msg.durationSec,
            lobbyPlayers: msg.players,
            gameOver: null,
            connectionError: null,
          });
          break;

        case 'GAME_START':
          set({
            roomCode: msg.roomCode,
            roomState: 'PLAYING',
            gridW: msg.gridW,
            gridH: msg.gridH,
            durationSec: msg.durationSec,
            stations: msg.stations,
            lastSeq: msg.seq,
            timeLeftMs: msg.durationSec * 1000,
            score: 0,
            combo: 0,
            cleanPlates: 4,
            washingPlates: 0,
            stationItems: [],
            orders: [],
            gameOver: null,
          });
          playGameSound('NEW_ORDER');
          break;

        case 'STATE_SNAPSHOT': {
          // Enforce monotonic Lamport clock ordering on client
          if (msg.seq < get().lastSeq) {
            return;
          }
          set({
            lastSeq: msg.seq,
            timeLeftMs: msg.timeLeftMs,
            score: msg.score,
            combo: msg.combo,
            cleanPlates: msg.cleanPlates,
            washingPlates: msg.washingPlates,
            players: msg.players,
            stationItems: msg.stationItems,
            orders: msg.orders,
          });
          break;
        }

        case 'GAME_EVENT': {
          playGameSound(msg.event);
          let tone: ToastNotice['tone'] = 'info';
          let text = '';
          if (msg.event === 'ORDER_COMPLETE') {
            tone = 'good';
            text = `${msg.detail} (+${msg.pointsDelta} pts)`;
          } else if (msg.event === 'WRONG_DISH' || msg.event === 'ORDER_EXPIRED') {
            tone = 'bad';
            text = `${msg.detail} (${msg.pointsDelta} pts)`;
          } else if (msg.event === 'PATTY_BURNT') {
            tone = 'bad';
            text = 'Patty burnt on stove!';
          } else if (msg.event === 'TOAST' || msg.event === 'PLATE_RETURNED') {
            tone = msg.event === 'PLATE_RETURNED' ? 'good' : 'info';
            text = msg.detail;
          }
          if (text) {
            const id = toastCounter++;
            set({ toast: { id, text, tone } });
            setTimeout(() => {
              if (get().toast?.id === id) {
                set({ toast: null });
              }
            }, 2400);
          }
          break;
        }

        case 'GAME_OVER':
          playGameSound('ORDER_COMPLETE');
          set({
            roomState: 'FINISHED',
            score: msg.score,
            gameOver: {
              score: msg.score,
              stars: msg.stars,
              ordersCompleted: msg.ordersCompleted,
              ordersExpired: msg.ordersExpired,
              playerStats: msg.playerStats,
            },
          });
          break;

        case 'LEFT_ROOM':
          set({
            roomCode: null,
            hostId: null,
            roomState: null,
            lobbyPlayers: [],
            players: [],
            orders: [],
            gameOver: null,
          });
          tcpClient.send({ type: 'LIST_ROOMS' });
          break;

        case 'ERROR':
          set({ connectionError: msg.message });
          break;
      }
    });
  }

  return {
    serverHost: 'undercooked.socialeventmapper.com', // Cloudflare DNS -> GCP VM (editable for local dev)
    serverPort: 6789,
    playerName: 'Chef',
    connected: false,
    connecting: false,
    connectionError: null,
    playerId: null,

    openRooms: [],
    roomCode: null,
    hostId: null,
    roomState: null,
    durationSec: 180,
    lobbyPlayers: [],

    gridW: 12,
    gridH: 8,
    stations: [],
    lastSeq: 0,
    timeLeftMs: 180_000,
    score: 0,
    combo: 0,
    cleanPlates: 4,
    washingPlates: 0,
    players: [],
    stationItems: [],
    orders: [],
    toast: null,
    gameOver: null,

    setServerConfig: (host, port, name) =>
      set({ serverHost: host, serverPort: port, playerName: name }),

    connectToServer: () => {
      const { serverHost, serverPort } = get();
      set({ connecting: true, connectionError: null });
      tcpClient.connect(serverHost, serverPort);
    },

    disconnectFromServer: () => {
      tcpClient.disconnect();
      set({
        connected: false,
        connecting: false,
        roomCode: null,
        roomState: null,
        lobbyPlayers: [],
        gameOver: null,
      });
    },

    refreshRooms: () => {
      tcpClient.send({ type: 'LIST_ROOMS' });
    },

    createRoom: (durationSec) => {
      tcpClient.send({ type: 'CREATE_ROOM', durationSec });
    },

    joinRoom: (code) => {
      tcpClient.send({ type: 'JOIN_ROOM', roomCode: code.trim().toUpperCase() });
    },

    toggleReady: () => {
      const { lobbyPlayers, playerId } = get();
      const me = lobbyPlayers.find((p) => p.id === playerId);
      tcpClient.send({ type: 'SET_READY', ready: !(me?.ready ?? false) });
    },

    startGame: () => {
      tcpClient.send({ type: 'START_GAME' });
    },

    returnToLobby: () => {
      tcpClient.send({ type: 'RETURN_LOBBY' });
    },

    leaveRoom: () => {
      tcpClient.send({ type: 'LEAVE_ROOM' });
    },

    sendMove: (dx, dy, facing) => {
      tcpClient.send({ type: 'MOVE', dx, dy, facing });
    },

    sendInteract: () => {
      tcpClient.send({ type: 'INTERACT' });
    },

    sendChop: (active) => {
      tcpClient.send({ type: 'CHOP', active });
    },

    clearError: () => set({ connectionError: null }),
  };
});
