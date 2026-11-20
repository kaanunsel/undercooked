# ARCHITECTURE.md — Undercooked System & Sequence Diagrams

This document visualizes the overall architecture and core concurrent interaction flows of **Undercooked**.

---

## 1. Overall System Architecture

```mermaid
flowchart TB
    subgraph Clients["Mobile & Test Clients (Pure Raw TCP)"]
        M1["Chef 1 Phone (Bare RN — Android APK / iOS Build)<br/>Skia 2D + Reanimated + Gesture Handler + Zustand<br/>react-native-tcp-socket"]
        M2["Chef 2..4 Phones (Bare RN — Android APK / iOS Build)<br/>react-native-tcp-socket (JSON lines)"]
        CLI["Java TCPClient Stress Test / nc<br/>java.net.Socket (JSON lines)"]
    end

    subgraph GCP["GCP Project: project-72f15835-9e20-4691-877 (Terraform Managed)"]
        FW["GCP Firewall Rule<br/>Allow TCP :6789 (Static External IP)"]

        subgraph VM["Single Compute Engine VM (e2-small, Ubuntu 24.04, JDK 21, systemd)"]
            subgraph Server["Plain Java TCP Server (Zero Frameworks, No j.u.c)"]
                SS["UndercookedServer<br/>java.net.ServerSocket(6789)<br/>accept() Loop"]
                CHT1["ClientHandlerThread #1<br/>BufferedReader.readLine() / OutputStream"]
                CHT2["ClientHandlerThread #2..N<br/>Explicit java.lang.Thread per Socket"]
                REG["RoomRegistry<br/>Guarded by BinarySemaphore"]

                subgraph Room["Room Instance (1..4 Players, Bounded N=6 Thread Slots)"]
                    MON["RoomStateMonitor<br/>Java Monitor (wait/notifyAll)"]
                    RWL["ReadersWritersLock<br/>Writer-Fair Snapshot Guard"]
                    MAP["KitchenMap 12x8 + Stations<br/>Per-Station BakeryLock +<br/>Ascending resourceId Lock Order"]
                    OB["OrderBoard (Cap=5)<br/>CountingSemaphore(emptySlots) +<br/>BinarySemaphore(orderMutex)"]
                    PP["PlatePool (Cap=4)<br/>CountingSemaphore(dirtyPlates) +<br/>BinarySemaphore(plateMutex)"]
                    BQ["BroadcastQueue (Cap=64)<br/>BoundedBuffer (Semaphores)"]

                    T_TICK["GameTickThread (20 Hz)<br/>Cook/Burn Timers + Expiry + Snapshot"]
                    T_ORD["OrderGeneratorThread<br/>Producer -> OrderBoard"]
                    T_WASH["DishwasherThread<br/>Consumer <- dirtyPlates (4s wash)"]
                    T_BC["RoomBroadcasterThread<br/>Consumer <- BroadcastQueue"]
                end
            end
        end
    end

    M1 -->|"Raw TCP :6789 (JSON lines)"| FW
    M2 -->|"Raw TCP :6789 (JSON lines)"| FW
    CLI -->|"Raw TCP :6789 (JSON lines)"| FW
    FW --> SS

    SS -->|"new Thread().start()"| CHT1
    SS -->|"new Thread().start()"| CHT2

    CHT1 --> REG
    CHT2 --> REG
    REG --> Room

    CHT1 -->|"Interact / Chop / Move"| MAP
    CHT2 -->|"Interact / Chop / Move"| MAP
    CHT1 -->|"Atomic Serve"| OB
    CHT2 -->|"Atomic Serve"| OB
    OB -->|"dirtyPlates.V()"| PP

    T_ORD -->|"emptySlots.P() + deposit"| OB
    T_WASH -->|"dirtyPlates.P() + return clean plate"| PP
    T_TICK -->|"Read/Update Timers"| MAP
    T_TICK -->|"Expire Orders"| OB
    T_TICK -->|"startRead() / endRead()"| RWL
    T_TICK -->|"deposit(STATE_SNAPSHOT)"| BQ

    T_BC -->|"fetch()"| BQ
    T_BC -->|"writeMutex.P() -> Socket.write()"| CHT1
    T_BC -->|"writeMutex.P() -> Socket.write()"| CHT2
```

---

## 2. Sequence Diagram 1 — Raw TCP Connection, Room Join & Lobby Start (`RoomStateMonitor`)

Shows explicit `ServerSocket.accept()` thread creation (`lecture4-2.pdf`), newline-delimited TCP JSON messages, `RoomRegistry` mutual exclusion, and Java Monitor coordination waking room worker threads.

```mermaid
sequenceDiagram
    autonumber
    participant C1 as Chef 1 (Bare RN TCP Client)
    participant C2 as Chef 2 (Bare RN TCP Client)
    participant SS as UndercookedServer (ServerSocket :6789)
    participant H1 as ClientHandlerThread #1
    participant H2 as ClientHandlerThread #2
    participant REG as RoomRegistry (BinarySemaphore)
    participant MON as RoomStateMonitor (Java Monitor)
    participant WK as Room Workers (Tick / OrderGen / Dishwasher)

    C1->>SS: TCP SYN / Connect (:6789)
    SS->>H1: accept() -> new ClientHandlerThread(socket1).start()
    C1->>H1: {"type":"HELLO","name":"Chef1"}\n
    H1-->>C1: {"type":"WELCOME","playerId":"p1"}\n

    C1->>H1: CREATE_ROOM (durationSec=180)
    H1->>REG: registryMutex.P()
    REG->>MON: Create Room "K4M2" (state=WAITING)
    REG->>WK: Spawn GameTick / OrderGen / Dishwasher / Broadcaster
    WK->>MON: waitUntilPlaying() -> while(WAITING) wait()
    REG-->>H1: Room "K4M2" (slot=0)
    H1->>REG: registryMutex.V()
    H1-->>C1: LOBBY_STATE (1/4 players)

    C2->>SS: TCP SYN / Connect (:6789)
    SS->>H2: accept() -> new ClientHandlerThread(socket2).start()
    C2->>H2: JOIN_ROOM ("K4M2")
    H2->>REG: registryMutex.P()
    REG-->>H2: Joined Room "K4M2" (slot=1)
    H2->>REG: registryMutex.V()
    H2-->>C1: LOBBY_STATE (2/4 players)
    H2-->>C2: LOBBY_STATE (2/4 players)

    C1->>H1: SET_READY (true)
    C2->>H2: SET_READY (true)
    C1->>H1: START_GAME
    H1->>MON: synchronized transitionTo(PLAYING) -> notifyAll()
    MON-->>WK: All worker threads wake up & begin shift
    H1-->>C1: GAME_START (12x8 map + stations)
    H1-->>C2: GAME_START (12x8 map + stations)
```

---

## 3. Sequence Diagram 2 — Contested Station Grab & Deadlock-Free Ordered Locking

Shows two chefs pressing `GRAB` on the exact same Stove `[S0]` holding a single `COOKED_PATTY`, protected by **Lamport's `BakeryLock`** and strict ascending **`resourceId` ordering** (`Player 0/1` < `Station 14`) to prevent item duplication and deadlock.

```mermaid
sequenceDiagram
    autonumber
    participant C1 as Chef 0 (Slot 0)
    participant C2 as Chef 1 (Slot 1)
    participant H1 as ClientHandlerThread #0
    participant H2 as ClientHandlerThread #1
    participant P0 as Player #0 (resId=0)
    participant P1 as Player #1 (resId=1)
    participant S0 as Stove #0 (resId=14, BakeryLock)

    Note over C1,C2: Stove #0 holds 1x COOKED_PATTY. Both chefs tap GRAB simultaneously.
    C1->>H1: INTERACT
    C2->>H2: INTERACT

    par Ordered Lock Acquisition (Player resId < Station resId)
        H1->>P0: BakeryLock.requestCS(slot=0)
        P0-->>H1: Acquired Player #0
        H1->>S0: BakeryLock.requestCS(slot=0) -> Ticket (1, 0)
    and
        H2->>P1: BakeryLock.requestCS(slot=1)
        P1-->>H2: Acquired Player #1
        H2->>S0: BakeryLock.requestCS(slot=1) -> Ticket (1, 1)
    end

    Note over S0: Tie-break: (1, 0) < (1, 1) -> Slot 0 enters CS first; Slot 1 waits (FIFO fair)
    S0-->>H1: Enter CS (Stove #0)
    H1->>S0: Read item == COOKED_PATTY -> Set Stove #0 item = null
    H1->>P0: Set Chef 0 heldItem = COOKED_PATTY
    H1->>S0: BakeryLock.releaseCS(slot=0) -> number[0] = 0
    H1->>P0: BakeryLock.releaseCS(slot=0)

    S0-->>H2: Enter CS (Stove #0)
    H2->>S0: Read item == null -> No-op (No duplication!)
    H2->>S0: BakeryLock.releaseCS(slot=1) -> number[1] = 0
    H2->>P1: BakeryLock.releaseCS(slot=1)
```

---

## 4. Sequence Diagram 3 — Producer–Consumer Orders, Atomic Serving & Dishwasher Pool

Shows:
1. `OrderGeneratorThread` blocking on `emptySlots.P()` when 5/5 orders are active.
2. Two chefs racing to serve the **only** matching `BURGER` order — atomic transaction prevents duplicate scoring.
3. `emptySlots.V()` waking `OrderGeneratorThread` and `dirtyPlates.V()` waking `DishwasherThread`.

```mermaid
sequenceDiagram
    autonumber
    participant OG as OrderGeneratorThread (Producer)
    participant OB as OrderBoard (emptySlots=5, orderMutex)
    participant H1 as ClientHandlerThread #0 (Chef 0)
    participant H2 as ClientHandlerThread #1 (Chef 1)
    participant PP as PlatePool (dirtyPlates Sem)
    participant DW as DishwasherThread (Consumer)

    Note over OG,OB: OrderBoard already has 5/5 active orders (emptySlots == 0)
    OG->>OB: emptySlots.P()
    Note over OG: BLOCKED in semaphore wait queue (zero CPU)

    Note over H1,H2: Only 1x BURGER order (#7) exists. Both chefs serve BURGER simultaneously!
    par Simultaneous Serving Race
        H1->>OB: orderMutex.P()
    and
        H2->>OB: orderMutex.P() (waits on orderMutex)
    end

    Note over H1,OB: Atomic Transaction (Chef 0 wins)
    H1->>OB: Match Order #7 (!completed && !expired)
    H1->>OB: Mark completed=true, remove #7, score += 70, combo++
    H1->>OB: orderMutex.V()
    H1->>OB: emptySlots.V()
    H1->>PP: dirtyPlates.V() (send plate to dishwasher)

    par Woken Workers & Second Chef
        OB-->>OG: Wakes up from emptySlots.P()!
        OG->>OB: orderMutex.P() -> Insert new Order #12 -> orderMutex.V()
    and
        PP-->>DW: Wakes up from dirtyPlates.P()!
        DW->>DW: Util.mySleep(4000ms) [Washing plate]
        DW->>PP: Return clean plate to Plate Dispenser (+1)
    and
        OB-->>H2: Enters orderMutex CS
        H2->>OB: No matching BURGER order found -> score -= 10 (WRONG_DISH)
        H2->>OB: orderMutex.V()
        H2->>PP: dirtyPlates.V()
    end
```

---

## 5. Sequence Diagram 4 — 20 Hz Game Tick & Decoupled Broadcast Pipeline

Shows how `GameTickThread` advances stove/order timers, captures a consistent world snapshot via `ReadersWritersLock`, and hands it off to `RoomBroadcasterThread` through `BoundedBuffer` so slow TCP sockets never block simulation.

```mermaid
sequenceDiagram
    autonumber
    participant TICK as GameTickThread (20 Hz)
    participant MAP as Stations (BakeryLock)
    participant OB as OrderBoard (orderMutex)
    participant RWL as ReadersWritersLock
    participant BQ as BroadcastQueue (BoundedBuffer)
    participant BC as RoomBroadcasterThread
    participant SOCK as ClientConnections (writeMutex)

    loop Every 50ms while Room == PLAYING
        TICK->>MAP: Advance stove cook/burn timers (per-station BakeryLock, slot=4)
        TICK->>OB: Expire overdue orders atomically (orderMutex.P/V -> emptySlots.V)

        TICK->>RWL: startRead()
        TICK->>TICK: Build consistent JSON STATE_SNAPSHOT (LamportClock.tick())
        TICK->>RWL: endRead()

        TICK->>BQ: deposit(snapshotJson) [empty.P() -> mutex.P() -> push -> mutex.V() -> filled.V()]
        BQ-->>BC: Wakes from filled.P()
        BC->>BQ: fetch() -> snapshotJson
        loop For each connected ClientConnection
            BC->>SOCK: writeMutex.P() -> sendLine(snapshotJson) -> writeMutex.V()
        end
    end
```
