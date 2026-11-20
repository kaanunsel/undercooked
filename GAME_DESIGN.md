# GAME_DESIGN.md — Undercooked Specification

## 1. Concept & Vision
**Undercooked** is a 2D top-down cooperative multiplayer cooking game for 1–4 players on mobile (inspired by *Overcooked*, 2016). Players share a compact kitchen with limited chopping boards, stoves, clean plates, and a single serving window. They must coordinate in real time to fulfill timed customer orders before deadlines expire.

Every core game mechanic maps naturally to a **CMPE 436** concurrency problem on the plain-Java backend (see [CONCURRENCY.md](CONCURRENCY.md)).

---

## 2. Game Rules & Mechanics

### 2.1 Session & Lobby
- **Players per Room**: `1` to `4` (1-player mode supported for easy solo testing; 2–4 players for classroom demos).
- **Room Creation**: Any player enters a nickname (e.g., `"Ali"`), taps **Create Room**, and receives a short 4-character room code (e.g., `K4M2`) or picks from the live **Open Rooms** list.
- **Chef Colors**: Assigned by seat slot (`0`: Red, `1`: Blue, `2`: Green, `3`: Yellow).
- **Round Duration**: Selectable in lobby: **`90s`** (Quick Classroom Demo) or **`180s`** (Standard Shift).
- **Start Condition**: Host taps **Start** once all connected players toggle **Ready** (or immediately if solo).

### 2.2 Kitchen Layout (`12 × 8` Top-Down Grid)
Landscape-optimized 2D grid (`x: 0..11`, `y: 0..7`). Outer border tiles hold stations; interior is walkable floor with a 2-tile central prep island that creates shared lanes and natural teamwork handoffs:

```text
   0   1   2   3   4   5   6   7   8   9   10  11
0 [W] [B] [M] [C] [S0][S1][C] [L] [T] [H] [C] [W]
1 [C]  .   .   .   .   .   .   .   .   .   .  [P]
2 [CB0].   .   .   .   .   .   .   .   .   .  [C]
3 [CB1].   .   .   .  [C] [C]  .   .   .   .  [SV]
4 [C]  .   .   .   .   .   .   .   .   .   .  [SV]
5 [C]  .   .   .   .   .   .   .   .   .   .  [C]
6 [TR] .   .   .   .   .   .   .   .   .   .  [C]
7 [W] [C] [C] [C] [C] [C] [C] [C] [C] [C] [C] [W]
```

| Symbol | Tile / Station Type | Count | Behavior & Concurrency Role |
|---|---|---|---|
| `.` | Walkable Floor | 58 | Free 2D continuous movement (`(x, y)` floats with tile collision) |
| `[W]` | Corner Wall | 4 | Decorative boundary |
| `[B]` | Bun Crate | 1 | Infinite `BUN` |
| `[M]` | Meat Crate | 1 | Infinite `RAW_PATTY` |
| `[L]` | Lettuce Crate | 1 | Infinite `RAW_LETTUCE` |
| `[T]` | Tomato Crate | 1 | Infinite `RAW_TOMATO` |
| `[H]` | Cheese Crate | 1 | Infinite `RAW_CHEESE` |
| `[CB0..1]` | Chopping Board | 2 | Contested (`BakeryLock`). Hold `CHOP` for `2.0s` to chop veggies/cheese |
| `[S0..1]` | Stove | 2 | Contested (`BakeryLock`). Auto-cooks `RAW_PATTY` (`4.0s` cooked, `9.0s` burnt) |
| `[P]` | Plate Dispenser | 1 | `CountingSemaphore` pool (starts with `4` clean plates; replenished by `DishwasherThread`) |
| `[SV]` | Serving Window | 2 tiles (1 logical station) | Atomic order delivery (`OrderBoard` transaction) + sends dirty plate to `DishwasherThread` |
| `[TR]` | Trash Bin | 1 | Discards held item; if holding a plate, empties the plate and keeps the clean plate |
| `[C]` | Neutral Counter | 21 | Drop, pass, or assemble ingredients onto plates (`BakeryLock` + ordered lock transfers) |

### 2.3 Ingredients & State Machine
| Raw Item | Process Station | Processing Rule | Output Item |
|---|---|---|---|
| `BUN` | None (ready) | Instant | `BUN` |
| `RAW_LETTUCE` | Chopping Board | Hold `CHOP` (`2.0s` total) | `CHOPPED_LETTUCE` |
| `RAW_TOMATO` | Chopping Board | Hold `CHOP` (`2.0s` total) | `CHOPPED_TOMATO` |
| `RAW_CHEESE` | Chopping Board | Hold `CHOP` (`2.0s` total) | `SLICED_CHEESE` |
| `RAW_PATTY` | Stove | Auto-cooks via `GameTickThread` (`0–4s` cooking, `4–9s` **COOKED**, `>9s` **BURNT**) | `COOKED_PATTY` (or `BURNT_PATTY`) |

- **Plating Rules (Zero Friction)**:
  - A `PLATE` holds a set of valid prepared ingredients (e.g., `PLATE[BUN, COOKED_PATTY]`).
  - Combining works **both ways**:
    - Chef holding `PLATE` taps `GRAB/DROP` on a counter/stove/board holding a prepared ingredient → ingredient moves onto the plate!
    - Chef holding a prepared ingredient taps `GRAB/DROP` on a counter holding a `PLATE` → ingredient merges onto that plate!
  - `RAW_*` and `BURNT_PATTY` items cannot be placed onto a plate (triggers gentle shake/error toast).

### 2.4 Recipes & Scoring
All recipes require a `PLATE`:

| Recipe ID | Display Name | Required Ingredients on Plate | Base Points | Order Time Limit |
|---|---|---|---|---|
| `SALAD` | Garden Salad | `CHOPPED_LETTUCE` + `CHOPPED_TOMATO` | `40` | `45s` |
| `BURGER` | Plain Burger | `BUN` + `COOKED_PATTY` | `60` | `55s` |
| `CHEESEBURGER` | Cheeseburger | `BUN` + `COOKED_PATTY` + `SLICED_CHEESE` | `80` | `65s` |
| `DELUXE_BURGER` | Deluxe Burger | `BUN` + `COOKED_PATTY` + `CHOPPED_LETTUCE` + `CHOPPED_TOMATO` | `110` | `75s` |

- **Speed Bonus**: `+20` pts if delivered with `>60%` time left; `+10` pts if `>30%` time left.
- **Combo Streak**: Consecutive on-time deliveries add `+10 × min(combo, 3)` bonus points.
- **Penalties**:
  - Order expires (`timer == 0`): `-20` pts, combo resets to `0`.
  - Wrong/unmatched dish served: `-10` pts, combo resets to `0` (plate still goes to dishwasher).
- **Star Rating (for 180s shift)**: ⭐ `150+` | ⭐⭐ `320+` | ⭐⭐⭐ `500+` (scaled by `0.5×` for 90s demo shift).

### 2.5 Mobile Controls (Landscape Layout)
- **Top HUD Strip**: Up to 5 order cards (recipe icon, ingredient pills, color-coded countdown bar) + Shared Team Score + Shift Countdown Timer.
- **Center Canvas**: `@shopify/react-native-skia` 2D kitchen rendering tiles, stations, chefs, held items, glowing target-tile indicator, circular chop/cook progress rings, and warning smoke when a patty is close to burning.
- **Bottom-Left**: Virtual Joystick (`react-native-gesture-handler`) outputting normalized `(dx, dy)` movement vector.
- **Bottom-Right**: Two large thumb buttons:
  - **`GRAB / DROP`** (Primary): Pick up, drop, plate, or serve on the highlighted facing tile.
  - **`CHOP`** (Secondary, hold): Chop item on a facing Chopping Board.

---

## 3. Example Gameplay Scenarios (Highlighting Real Concurrency)

### Scenario 1 — Cooperative Assembly Line (3 Chefs)
1. `OrderGeneratorThread` deposits Order #1 (`CHEESEBURGER`, 65s) and Order #2 (`SALAD`, 45s) into `OrderBoard`.
2. **Chef Red** grabs `RAW_PATTY` from `[M]` and places it on Stove `[S0]`. `GameTickThread` increments cook progress every 50ms.
3. **Chef Blue** grabs `RAW_CHEESE` from `[H]`, places it on Chopping Board `[CB0]`, and holds `CHOP` for `2.0s` until `SLICED_CHEESE` is ready.
4. **Chef Green** grabs a clean plate from `[P]` (`PlatePool` drops from `4 → 3`), puts a `BUN` on it, picks up `COOKED_PATTY` from `[S0]` and `SLICED_CHEESE` from `[CB0]`, and delivers at `[SV]`.
5. `OrderBoard` atomically matches Order #1, awards `80 + 20 = 100` pts, calls `emptySlots.V()` (allowing a new order to spawn), and signals `dirtyPlates.V()` so `DishwasherThread` washes and returns the plate `4s` later.

### Scenario 2 — Two Chefs Grab the Same Cooked Patty (Station Contention)
- Stove `[S0]` finishes cooking a patty (`COOKED_PATTY`). Chef Red and Chef Blue are both adjacent to `[S0]` and tap `GRAB` within 1ms of each other.
- Both `ClientHandlerThread`s invoke `station.lock.requestCS(threadSlotId)` (`BakeryLock`).
- Ticket `(1, 0)` enters first: transfers `COOKED_PATTY` to Chef Red, sets `S0.item = null`, releases lock.
- Ticket `(1, 1)` enters second: sees `S0.item == null`, performs no-op (no item duplication!).

### Scenario 3 — Simultaneous Delivery for the Last Matching Order (Duplicate Completion Race)
- Only **one** `BURGER` order (Order #7) is active on the board. Chef Red and Chef Green both walk to `[SV]` holding plated `BURGER`s and tap `GRAB/DROP` simultaneously.
- Both threads enter `OrderBoard.tryCompleteOrder()` guarded by `orderMutex`.
- Chef Red enters first: matches Order #7, marks `completed = true`, removes it, adds `+70 pts`.
- Chef Green enters second: Order #7 is gone, no matching `BURGER` order exists → receives `WRONG_DISH` (`-10 pts`) and buzz sound. Duplicate completion is impossible.

### Scenario 4 — Plate Shortage & Dishwasher Recovery
- All 4 clean plates are out on counters or being served. Chef Blue taps `[P]` (`PLATE_DISPENSER`).
- `PlatePool.tryTakeCleanPlate()` returns `false` immediately (non-blocking for TCP thread!) and sends a `TOAST` `"No clean plates — dishwasher running!"`.
- Meanwhile `DishwasherThread`, woken by `dirtyPlates.P()`, finishes its 4-second wash cycle and returns a clean plate to `[P]`, broadcasting `PLATE_RETURNED` with a chime sound.

---

## 4. Finalized Tech Stack

| Layer | Technology | Rationale |
|---|---|---|
| **Backend Language** | Plain Java 21 (Zero Dependencies) | Strict CMPE 436 compliance: no frameworks, no `java.util.concurrent.*`. |
| **Backend Networking** | Hand-written `java.net.ServerSocket` + `Socket` (`port 6789`) | Pure raw TCP byte stream (`\n`-delimited JSON over `BufferedReader` / `OutputStream`) with explicit `Thread` per client — 1-to-1 with `lecture4-2.pdf` slides 45–50. Zero WebSocket/HTTP overhead. |
| **Mobile Framework** | Bare **React Native** (`@react-native-community/cli`, **No Expo**) + TypeScript | Full native `android/` (direct `.apk` via Gradle) and `ios/` (direct Xcode build) projects, unlocking native TCP sockets without Expo Go restrictions. |
| **Mobile Networking** | `react-native-tcp-socket` | Direct native TCP client (`TcpSocket.createConnection`) with `\n` stream line-buffering — TypeScript counterpart to `lecture4-2.pdf`'s `TCPClient`. |
| **2D Rendering** | `@shopify/react-native-skia` | Crisp procedural 60fps 2D tiles, stations, chefs, layered plated ingredients, and radial progress rings — zero heavy sprite-sheet pipeline needed. |
| **Animations** | `react-native-reanimated` | Smooth 60fps client-side position interpolation between 20 Hz server state ticks. |
| **Touch Controls** | `react-native-gesture-handler` | Responsive left virtual joystick + right `GRAB/DROP` and `CHOP` buttons. |
| **Orientation** | `react-native-orientation-locker` (or native manifest/Info.plist) | Locks gameplay screen to landscape for ergonomic 2-thumb controls and wide 12×8 kitchen visibility. |
| **State Management** | `zustand` | Lightweight store for TCP connection status, room lobby, active game snapshot, and HUD events. |
| **Sound Effects** | `react-native-sound` | Lightweight native audio playback for chop, sizzle, plate clink, order chime, delivery ding, and burn alarm. |
| **Navigation** | `@react-navigation/native` + `@react-navigation/native-stack` | Clean 4-screen native stack: Connect/Home → Lobby → Kitchen Game → Shift Results. |
| **Infrastructure** | Terraform + GCP Compute Engine | Single `e2-small` Ubuntu 24.04 VM in `project-72f15835-9e20-4691-877`, static IP, TCP `6789` firewall rule, `systemd` service. |

---

## 5. Wire Protocol (Raw TCP — Newline-Delimited JSON)

Every message is a compact single-line UTF-8 JSON object terminated by `\n` over the raw TCP stream (`BufferedReader.readLine()` on Java; chunk buffer split on `\n` in TypeScript `TcpClient`). Every message includes a `"type"` field.

### Client → Server
| `type` | Payload Fields | Purpose |
|---|---|---|
| `HELLO` | `name` | Set player nickname |
| `LIST_ROOMS` | *(none)* | Request list of open rooms |
| `CREATE_ROOM` | `durationSec` (`90` or `180`) | Create new room & auto-join as host |
| `JOIN_ROOM` | `roomCode` | Join existing room (`WAITING` state) |
| `SET_READY` | `ready` (`true`/`false`) | Toggle ready state in lobby |
| `START_GAME` | *(none)* | Host starts the shift (wakes `RoomStateMonitor`) |
| `MOVE` | `dx`, `dy` (`-1.0..1.0`), `facing` (`N`/`S`/`E`/`W`) | Continuous movement input (~20 Hz while moving) |
| `INTERACT` | *(none)* | Primary `GRAB / DROP / PLATE / SERVE` action on facing tile |
| `CHOP` | `active` (`true`/`false`) | Start/stop chopping on facing Chopping Board |
| `LEAVE_ROOM` | *(none)* | Leave room cleanly |

### Server → Client
| `type` | Payload Fields | Purpose |
|---|---|---|
| `WELCOME` | `playerId`, `serverVersion` | Acknowledge connection |
| `ROOM_LIST` | `rooms: [{code, players, maxPlayers, state}]` | Open rooms snapshot |
| `LOBBY_STATE` | `roomCode`, `hostId`, `state`, `durationSec`, `players: [{id, slot, name, ready}]` | Lobby updates |
| `GAME_START` | `roomCode`, `gridW`, `gridH`, `stations: [...]`, `durationSec` | Static kitchen map + shift start |
| `STATE_SNAPSHOT` | `seq` (Lamport clock), `timeLeftMs`, `score`, `combo`, `cleanPlates`, `washingPlates`, `players: [...]`, `stationItems: [...]`, `orders: [...]` | Authoritative 20 Hz world snapshot |
| `GAME_EVENT` | `seq`, `event` (`ORDER_COMPLETE`, `ORDER_EXPIRED`, `WRONG_DISH`, `CHOP_DONE`, `PATTY_COOKED`, `PATTY_BURNT`, `PLATE_RETURNED`, `TOAST`), `actorId`, `detail`, `pointsDelta` | Sound/visual feedback triggers |
| `GAME_OVER` | `score`, `stars`, `ordersCompleted`, `ordersExpired`, `playerStats: [...]` | End-of-shift summary |
| `ERROR` | `code`, `message` | Validation or room error |
