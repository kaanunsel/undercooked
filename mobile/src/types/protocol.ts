export type Facing = 'N' | 'S' | 'E' | 'W';

export type RoomState = 'WAITING' | 'PLAYING' | 'FINISHED' | 'CLOSED';

export type StationType =
  | 'WALL'
  | 'COUNTER'
  | 'CRATE_BUN'
  | 'CRATE_MEAT'
  | 'CRATE_LETTUCE'
  | 'CRATE_TOMATO'
  | 'CRATE_CHEESE'
  | 'CHOPPING_BOARD'
  | 'STOVE'
  | 'PLATE_DISPENSER'
  | 'SERVING_WINDOW'
  | 'TRASH_BIN';

export type ItemType =
  | 'BUN'
  | 'RAW_LETTUCE'
  | 'CHOPPED_LETTUCE'
  | 'RAW_TOMATO'
  | 'CHOPPED_TOMATO'
  | 'RAW_CHEESE'
  | 'SLICED_CHEESE'
  | 'RAW_PATTY'
  | 'COOKED_PATTY'
  | 'BURNT_PATTY'
  | 'PLATE';

export type RecipeId = 'SALAD' | 'BURGER' | 'CHEESEBURGER' | 'DELUXE_BURGER';

export interface ItemDto {
  type: ItemType;
  contents?: ItemType[];
  recipe?: RecipeId;
}

export interface RoomSummaryDto {
  code: string;
  players: number;
  maxPlayers: number;
  durationSec: number;
  state: RoomState;
}

export interface LobbyPlayerDto {
  id: string;
  slot: number;
  name: string;
  ready: boolean;
}

export interface StationStaticDto {
  id: number;
  x: number;
  y: number;
  type: StationType;
}

export interface StationItemDto {
  id: number;
  x: number;
  y: number;
  item: ItemDto;
  chopRatio?: number;
  cookRatio?: number;
  burnRatio?: number;
}

export interface PlayerSnapshotDto {
  id: string;
  slot: number;
  name: string;
  x: number;
  y: number;
  facing: Facing;
  chopping: boolean;
  heldItem: ItemDto | null;
}

export interface OrderDto {
  id: number;
  recipe: RecipeId;
  name: string;
  basePoints: number;
  remainingMs: number;
  durationMs: number;
  ingredients: ItemType[];
}

export interface PlayerStatsDto {
  id: string;
  slot: number;
  name: string;
  ordersServed: number;
  itemsChopped: number;
  pattiesCooked: number;
}

export type ServerMessage =
  | { type: 'WELCOME'; playerId: string; name?: string; serverVersion: string }
  | { type: 'ROOM_LIST'; rooms: RoomSummaryDto[] }
  | {
      type: 'LOBBY_STATE';
      seq: number;
      roomCode: string;
      hostId: string;
      state: RoomState;
      durationSec: number;
      players: LobbyPlayerDto[];
    }
  | {
      type: 'GAME_START';
      seq: number;
      roomCode: string;
      gridW: number;
      gridH: number;
      durationSec: number;
      stations: StationStaticDto[];
    }
  | {
      type: 'STATE_SNAPSHOT';
      seq: number;
      timeLeftMs: number;
      score: number;
      combo: number;
      cleanPlates: number;
      washingPlates: number;
      players: PlayerSnapshotDto[];
      stationItems: StationItemDto[];
      orders: OrderDto[];
    }
  | {
      type: 'GAME_EVENT';
      seq: number;
      event: string;
      actorId?: string;
      detail: string;
      pointsDelta: number;
    }
  | {
      type: 'GAME_OVER';
      seq: number;
      score: number;
      stars: number;
      ordersCompleted: number;
      ordersExpired: number;
      playerStats: PlayerStatsDto[];
    }
  | { type: 'LEFT_ROOM' }
  | { type: 'PONG'; t: number }
  | { type: 'ERROR'; code: string; message: string };

export type ClientMessage =
  | { type: 'HELLO'; name: string }
  | { type: 'LIST_ROOMS' }
  | { type: 'CREATE_ROOM'; durationSec: number }
  | { type: 'JOIN_ROOM'; roomCode: string }
  | { type: 'SET_READY'; ready: boolean }
  | { type: 'START_GAME' }
  | { type: 'RETURN_LOBBY' }
  | { type: 'MOVE'; dx: number; dy: number; facing?: Facing }
  | { type: 'INTERACT' }
  | { type: 'CHOP'; active: boolean }
  | { type: 'LEAVE_ROOM' }
  | { type: 'PING' };
