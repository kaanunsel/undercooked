# EXECUTION_PLAN.md — Undercooked Phased Roadmap

This document defines the exact repository layout, build/test workflow, and step-by-step execution phases so subsequent agents can build **Undercooked** cleanly and verify every milestone.

---

## 1. Target Repository Structure

```text
undercooked/
├── AGENTS.md                        # Hard CMPE 436 constraints & slide paths
├── CONCURRENCY.md                   # Living register of concurrency scenarios & proofs
├── GAME_DESIGN.md                   # Game rules, 12x8 layout, recipes, wire protocol
├── EXECUTION_PLAN.md                # This phased implementation roadmap
├── README.md                        # Quick-start overview & doc links
├── docs/
│   └── ARCHITECTURE.md              # Overall architecture & Mermaid sequence diagrams
├── server/                          # Plain Java 21 backend (ZERO external dependencies)
│   ├── Makefile                     # compile, jar, test, run targets
│   └── src/undercooked/
│       ├── Main.java                # Entry point (port arg, default 6789)
│       ├── concurrency/             # Hand-rolled CMPE 436 primitives (NO j.u.c!)
│       │   ├── Util.java
│       │   ├── Lock.java
│       │   ├── PetersonLock.java
│       │   ├── BakeryLock.java
│       │   ├── TestAndSet.java
│       │   ├── HWMutex.java
│       │   ├── BinarySemaphore.java
│       │   ├── CountingSemaphore.java
│       │   ├── BoundedBuffer.java
│       │   ├── ReadersWritersLock.java
│       │   ├── ResourceLocker.java
│       │   └── LamportClock.java
│       ├── net/                     # Explicit raw TCP ServerSocket (\n JSON stream)
│       │   ├── UndercookedServer.java
│       │   ├── ClientHandlerThread.java
│       │   ├── ClientConnection.java
│       │   └── MiniJson.java        # Tiny plain-Java JSON parser/builder
│       ├── game/                    # Authoritative kitchen simulation
│       │   ├── RoomRegistry.java
│       │   ├── Room.java
│       │   ├── RoomStateMonitor.java
│       │   ├── KitchenMap.java
│       │   ├── Station.java
│       │   ├── Player.java
│       │   ├── Item.java
│       │   ├── Recipe.java
│       │   ├── Order.java
│       │   ├── OrderBoard.java
│       │   ├── PlatePool.java
│       │   ├── GameTickThread.java
│       │   ├── OrderGeneratorThread.java
│       │   ├── DishwasherThread.java
│       │   └── RoomBroadcasterThread.java
│       └── tests/                   # Standalone plain-Java stress tests
│           ├── ConcurrencyTestSuite.java
│           ├── MutexStressTest.java
│           ├── BoundedBufferStressTest.java
│           ├── ReadersWritersStressTest.java
│           ├── DuplicateOrderRaceTest.java
│           ├── StationContentionTest.java
│           └── TcpLoadTest.java
├── mobile/                          # Bare React Native CLI + TypeScript (NO Expo)
│   ├── android/                     # Native Android project (Gradle -> release APK)
│   ├── ios/                         # Native iOS project (CocoaPods / Xcode build)
│   ├── src/
│   │   ├── App.tsx                  # React Navigation root stack
│   │   ├── screens/
│   │   │   ├── ConnectScreen.tsx    # Server IP/Port & Nickname screen
│   │   │   ├── LobbyScreen.tsx      # Room Create / Join / Ready screen
│   │   │   ├── GameScreen.tsx       # Landscape 2D Skia Kitchen screen
│   │   │   └── ResultsScreen.tsx    # Shift Summary & Star Rating screen
│   │   ├── net/tcpClient.ts         # react-native-tcp-socket client (\n stream framer)
│   │   ├── store/useGameStore.ts    # Zustand store (connection, lobby, snapshot, events)
│   │   ├── game/
│   │   │   ├── KitchenCanvas.tsx    # @shopify/react-native-skia 2D renderer
│   │   │   ├── Joystick.tsx         # Gesture Handler virtual thumbstick
│   │   │   ├── ActionButtons.tsx    # GRAB/DROP & CHOP tactile buttons
│   │   │   ├── OrderBar.tsx         # Top HUD order tickets + timers + score
│   │   │   └── soundManager.ts      # react-native-sound SFX triggers
│   │   └── types/protocol.ts        # TypeScript mirror of wire protocol
│   ├── assets/sounds/               # Tiny royalty-free / synthesized WAV SFX
│   └── package.json
└── infra/
    └── terraform/                   # Single GCP VM deployment
        ├── main.tf                  # Compute Engine VM, static IP, firewall, startup script
        ├── variables.tf             # project_id, region, zone, machine_type, port
        ├── outputs.tf               # server_ip, server_port, ssh_command
        ├── terraform.tfvars         # Pre-filled with project-72f15835-9e20-4691-877
        └── deploy.sh                # Helper: build server jar + scp + restart systemd
```

---

## 2. Phased Implementation Plan

### Phase 1 — Plain Java Concurrency Primitives & Stress Tests
**Goal**: Build and rigorously verify every CMPE 436 synchronization primitive before writing any game logic.
- **Tasks**:
  1. Create `server/Makefile` (pure `javac` + `jar`, zero external JARs).
  2. Implement `Util`, `Lock`, `PetersonLock`, `BakeryLock` (with `volatile` slot wrappers + periodic ticket reset), `TestAndSet`, `HWMutex`.
  3. Implement `BinarySemaphore`, `CountingSemaphore`, `BoundedBuffer<T>` (strict Attempt 2 semaphore ordering), `ReadersWritersLock` (writer-fair Monitor), `ResourceLocker` (ascending `resourceId` lock ordering), `LamportClock`.
  4. Implement `MutexStressTest`, `BoundedBufferStressTest`, `ReadersWritersStressTest` and run `ConcurrencyTestSuite`.
- **Exit Criteria**:
  - `grep -r "java.util.concurrent" server/src` returns **0 matches**.
  - All stress tests pass 100% deterministically across repeated runs with zero lost updates and zero deadlocks.
  - Update `CONCURRENCY.md` with verified class names.

### Phase 2 — Multithreaded Raw TCP Server & Room Lobby Engine
**Goal**: Stand up the explicit `ServerSocket` raw TCP server (`lecture4-2.pdf` pattern) and room lobby management.
- **Tasks**:
  1. Implement `MiniJson` (compact plain-Java JSON object/array parser & serializer).
  2. Implement `UndercookedServer` (`ServerSocket(6789)` accept loop spawning `new ClientHandlerThread(socket, server).start()`), `ClientConnection` (`BufferedReader.readLine()` + `OutputStream` guarded by per-socket `writeMutex`), `RoomRegistry` (guarded by `BinarySemaphore`), and `RoomStateMonitor` (Java Monitor).
  3. Support `HELLO`, `LIST_ROOMS`, `CREATE_ROOM`, `JOIN_ROOM`, `SET_READY`, `START_GAME`, `LEAVE_ROOM`, and graceful disconnect cleanup.
  4. Implement `TcpLoadTest` (plain Java `Socket` clients matching `lecture4-2.pdf` slides 45–46 creating/joining rooms concurrently).
- **Exit Criteria**:
  - Multiple raw TCP clients (`nc localhost 6789` and Java `Socket` test clients) connect to port `6789`, join the same room, and receive identical `\n`-delimited `LOBBY_STATE` broadcasts.
  - `TcpLoadTest` passes with zero leaked threads on disconnect.

### Phase 3 — Authoritative Kitchen Simulation & Recipes
**Goal**: Implement the complete 12×8 kitchen engine, recipes, timed orders, dishwasher pool, and concurrency stress tests.
- **Tasks**:
  1. Implement `KitchenMap` (12×8 grid), `Station` (each with `BakeryLock(6)` and `resourceId`), `Player` (inventory + `resourceId`), `Item`, `Recipe` (4 recipes), and `Order`.
  2. Implement `OrderBoard` (Producer–Consumer `CountingSemaphore(5)` + atomic `BinarySemaphore` completion/expiry) and `PlatePool` (`CountingSemaphore` clean/dirty plate lifecycle).
  3. Implement the 4 explicit room worker threads: `GameTickThread` (20 Hz movement, collision, stove cook/burn timers, order expiry, snapshot enqueue), `OrderGeneratorThread`, `DishwasherThread`, and `RoomBroadcasterThread`.
  4. Implement `INTERACT` (grab/drop/plate/serve using `ResourceLocker.withOrderedLocks`) and `CHOP` mechanics.
  5. Implement `DuplicateOrderRaceTest` and `StationContentionTest` and run full `ConcurrencyTestSuite`.
- **Exit Criteria**:
  - Full game shift playable end-to-end via CLI/raw TCP clients.
  - `DuplicateOrderRaceTest` proves 0 duplicate completions across 1,000 simultaneous delivery races.
  - `StationContentionTest` proves 0 item duplications, invariant `totalPlates == 4`, and 0 deadlocks.
  - Update `CONCURRENCY.md` with final class/method references.

### Phase 4 — Bare React Native 2D Mobile Client (Android APK & iOS Native Builds)
**Goal**: Build a smooth, responsive, fun 2D bare React Native client talking directly over raw TCP sockets (`react-native-tcp-socket`).
- **Tasks**:
  1. Initialize bare React Native TypeScript project in `mobile/` (`npx @react-native-community/cli init Undercooked`) with `react-native-tcp-socket`, `@shopify/react-native-skia`, `react-native-reanimated`, `react-native-gesture-handler`, `zustand`, `@react-navigation/native`, `@react-navigation/native-stack`, `react-native-sound`, `react-native-orientation-locker`.
  2. Implement `tcpClient.ts` (`TcpSocket.createConnection({ host, port: 6789 })`, `\n`-delimited stream chunk buffering, auto-cleanup, configurable server IP/port) and `useGameStore.ts`.
  3. Build `ConnectScreen.tsx` (Server IP/Port + Nickname) and `LobbyScreen.tsx` (Create Room, Open Rooms list, Room Code join, Ready toggle, Duration selector, Start button).
  4. Build landscape `GameScreen.tsx`:
     - `KitchenCanvas.tsx` (Skia): floor checkerboard, color-coded stations & crates, chefs with colored hats + direction eyes + held item stack, glowing target tile outline, radial chop/cook gauges, smoke animation when burning.
     - `OrderBar.tsx`: live order cards with ingredient badges, countdown progress bars, team score, combo badge, clean plate counter, shift timer.
     - `Joystick.tsx` + `ActionButtons.tsx`: left thumbstick (20 Hz throttled `MOVE`), right `GRAB/DROP` and hold-to-`CHOP` buttons.
     - `soundManager.ts`: lightweight WAV SFX (`react-native-sound`) for chop, sizzle, ding, buzz, burn warning.
  5. Build `ResultsScreen.tsx` (team score, 1–3 stars, per-chef stats, Play Again / Back to Lobby).
- **Exit Criteria**:
  - Android & iOS builds compile cleanly (`./gradlew assembleDebug` / Xcode build).
  - 2+ phones / emulators connect over raw TCP to the local Java server, join a room, cook all 4 recipes cooperatively, see real-time 60fps interpolated movement and progress bars, and finish a shift cleanly.

### Phase 5 — GCP Terraform Infrastructure & Live Deployment
**Goal**: Provision a single reproducible GCP VM and deploy the plain-Java server JAR.
- **Tasks**:
  1. Write `infra/terraform/` targeting GCP project **`project-72f15835-9e20-4691-877`**:
     - `google_compute_address` (static external IP in `europe-west3` Frankfurt for low latency to Istanbul).
     - `google_compute_firewall` allowing TCP `6789` (and SSH `22`) to tag `undercooked-server`.
     - `google_compute_instance` (`e2-small`, 2 vCPU / 2 GB RAM, Ubuntu 24.04 LTS) with startup script installing `openjdk-21-jre-headless` and configuring `undercooked.service` (`systemd`).
  2. Write `infra/terraform/deploy.sh` to build `undercooked-server.jar`, upload via `gcloud compute scp`, and restart `undercooked.service`.
  3. Update default server host in `mobile/` to the provisioned static IP (still editable on the Connect screen for local dev!).
- **Exit Criteria**:
  - `terraform validate` and `terraform apply` succeed cleanly.
  - Remote TCP port `6789` responds to both CLI raw TCP and mobile `react-native-tcp-socket` clients over cellular/Wi-Fi.

### Phase 6 — Classroom Demo Builds & Presentation Package
**Goal**: Produce installable mobile builds and prepare for live classroom multiplayer demo and Prof. Alper Şen's technical evaluation.
- **Tasks**:
  1. Build distributable **Android release `.apk`** (`cd mobile/android && ./gradlew assembleRelease`) to share directly with classmates, plus verify **iOS build** (`cd mobile/ios && pod install`, Xcode build for iOS simulator/device).
  2. Add an optional server debug flag (`--log-concurrency`) printing concise lock/semaphore events (e.g., `[BAKERY] Slot 0 acquired Stove#1`, `[ORDER_BOARD] EmptySlots.P() blocked — 5/5 full`, `[ORDER_BOARD] Atomic complete Order#7 by Chef 0`) so concurrency behavior can be shown live in a terminal during the presentation!
  3. Final audit of `CONCURRENCY.md` and `docs/ARCHITECTURE.md` against actual source code.
- **Exit Criteria**:
  - Live 4-player dry run completes with zero crashes, zero deadlocks, and verifiable concurrency logs.
