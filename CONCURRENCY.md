# CONCURRENCY.md — Concurrency Scenarios & CMPE 436 Solutions

> **Living Document**: Every agent implementing or modifying backend code MUST keep this register synchronized with the actual Java classes under `server/src/undercooked/concurrency/` and `server/src/undercooked/game/`.

This document records every real concurrency problem occurring inside the **Undercooked** multiplayer Java server, the exact **CMPE 436** primitive implemented from scratch to solve it (with **zero** `java.util.concurrent.*` usage), the formal safety/liveness properties satisfied, and how each scenario is stress-tested.

---

## 1. Custom Synchronization Primitives Inventory

All primitives live in `server/src/undercooked/concurrency/` and mirror Vijay Garg's *Concurrent and Distributed Computing in Java* & Prof. Alper Şen's slides:

| Class / Interface | CMPE 436 Slide Reference | Implementation Basis | Where Used in Undercooked |
|---|---|---|---|
| `Util` (`myWait`, `mySleep`) | `lecture2.pdf` p.17, `lecture3` p.9 | Plain `Object.wait()` / `Thread.sleep()` wrappers | All blocking primitives & worker loops |
| `Lock` (`requestCS`, `releaseCS`) | `lecture2.pdf` p.16 | Interface for software/HW mutexes | Implemented by `PetersonLock`, `BakeryLock`, `HWMutex` |
| `PetersonLock` | `lecture2.pdf` p.21 | 2-thread software mutex (`wantCS[]`, `turn`) with `volatile` holders | Unit/stress-tested; optional 2-thread room handoff |
| `BakeryLock` | `lecture2.pdf` p.31–33 | N-thread Lamport's Bakery (`choosing[]`, `number[]`, periodic reset) | Per-station mutual exclusion (Chopping Boards, Stoves, Counters) |
| `TestAndSet` / `HWMutex` | `lecture2.pdf` p.41 | Synchronized `testAndSet(int)` spin mutex | Stress-tested & used for lightweight ID/sequence generator |
| `BinarySemaphore` | `lecture3` p.9, 11 | `synchronized` + `while (!value) myWait(this)` + `notifyAll()` | Mutual exclusion for `RoomRegistry`, `OrderBoard`, `BoundedBuffer` |
| `CountingSemaphore` | `lecture3` p.10, 12 | `synchronized` + `while (value == 0) myWait(this)` + `notifyAll()` | `BoundedBuffer` (`emptySlots`/`filledSlots`), Clean/Dirty Plate Pools |
| `BoundedBuffer<T>` | `lecture3` p.15–20 | Circular array + `CountingSemaphore` (`isFull`, `isEmpty`) + `BinarySemaphore` (`mutex`) | `BroadcastQueue` (MPSC event fan-out) & `OrderBoard` capacity gating |
| `ReadersWritersLock` | `lecture3` p.21–24 | Custom Monitor / Semaphores with writer-preference fairness | Consistent kitchen snapshot reads vs. structural mutations |
| `RoomStateMonitor` | `lecture3` p.38–58 | Java Monitor (`synchronized`, `while (!cond) myWait(this)`, `notifyAll()`) | Room lobby readiness, pausing/waking room threads, clean shutdown |
| `LamportClock` | `lecture5-2.pdf`, `lecture10.pdf` | Monotonic logical timestamp per room | Total ordering of state broadcasts & event logs |

> **Java Memory Model (JMM) Note on Software Locks (`PetersonLock`, `BakeryLock`)**:
> On real multi-core CPUs (such as the GCP VM), plain `int[]`/`boolean[]` array elements are not `volatile` in Java, so the JIT compiler can cache reads in registers and cause infinite spins or stale reads. Without touching `java.util.concurrent.atomic`, we wrap each array slot in a tiny inner class (`class VolatileInt { volatile int value; }`, `class VolatileBool { volatile boolean value; }`) and invoke `Thread.yield()` inside spin loops. This preserves the textbook algorithm line-for-line while guaranteeing JMM happens-before visibility and preventing CPU starvation.

---

## 2. Concurrency Scenarios in Undercooked

### Scenario C1 — Concurrent Client Connections (Explicit Multithreaded TCP Server)
- **Gameplay Trigger**: 2–4 mobile clients (plus multiple simultaneous rooms) connect to TCP port `6789`, continuously sending movement and interaction commands while receiving 20 Hz state snapshots.
- **Hazard**: A single-threaded `ServerSocket` loop (`lecture4-2.pdf` slide 49) blocks on one client's `readLine()` and freezes all other players.
- **CMPE 436 Solution**:
  - Main accept thread listens on `new ServerSocket(6789)`.
  - On each `Socket conn = welcomeSocket.accept()`, explicitly spawns `new ClientHandlerThread(conn, server).start()` (`extends Thread`, no `ExecutorService`).
  - Each active `Room` explicitly spawns 4 dedicated worker threads:
    1. `GameTickThread` (20 Hz simulation: stove cooking/burning timers, order countdowns, movement integration).
    2. `OrderGeneratorThread` (Producer: generates timed customer orders).
    3. `DishwasherThread` (Resource recycler: washes dirty plates and returns clean plates).
    4. `RoomBroadcasterThread` (Consumer: drains `BroadcastQueue` and writes serialized frames to all clients in the room).
- **Slide Ref**: `lecture4-2.pdf` p.33–50, `lecture2.pdf` p.4–9.
- **Properties**: Responsive concurrent I/O; clean thread lifecycle termination via `volatile boolean running` + `interrupt()` + `join()`.

---

### Scenario C2 — Contested Kitchen Station Access (Mutual Exclusion + Starvation-Freedom)
- **Gameplay Trigger**: Chef 0 and Chef 1 both stand next to **Stove #1** (which holds 1 cooked burger patty) and tap `GRAB` at the same millisecond — or both try to drop raw ingredients onto **Chopping Board #0** simultaneously.
- **Hazard**: Classic **Read-Modify-Write Race Condition** (`lecture2.pdf` p.11). Both threads read `station.item == COOKED_PATTY`, both assign it to their hands (item duplication!), or one overwrites the other's ingredient (lost update).
- **CMPE 436 Solution**:
  - Each kitchen station has its own **`BakeryLock(MAX_ROOM_THREADS)`** (Lamport's Bakery Algorithm, `lecture2.pdf` p.31–33).
  - Why `BakeryLock` is ideal here: Every room has a small, strictly bounded thread count (`MAX_ROOM_THREADS = 6`: player slots `0..3`, `GameTickThread = 4`, `DishwasherThread = 5`). Each thread has a fixed integer `threadSlotId` in `0..5`, directly solving Bakery's slide 38 caveat ("awkward for dynamic threads / large N").
  - Periodic ticket reset: when `releaseCS(i)` observes all `number[j] == 0`, tickets naturally restart from `1`, preventing integer overflow (`lecture2.pdf` p.32 callout).
- **Properties**:
  - **Mutual Exclusion (Safety)**: At most one thread mutates a given station's item or chop/cook timer at a time.
  - **Progress (Liveness)**: If a station is free and ≥1 chef requests it, one enters without deadlock.
  - **Starvation-Freedom / FIFO Fairness (Liveness)**: Strictly ordered by `(ticketNumber, threadSlotId)`, so no chef can be starved out of a contested stove or chopping board.

---

### Scenario C3 — Timed Order Generation & Capacity Limit (Producer–Consumer Bounded Buffer)
- **Gameplay Trigger**: The kitchen's Order Board holds at most **`MAX_ACTIVE_ORDERS = 5`** tickets simultaneously. `OrderGeneratorThread` creates a new timed order every 12–16 seconds, while players deliver dishes (removing orders) or orders expire.
- **Hazard**: Without bounded coordination, the order board overflows indefinitely (unbounded growth) or `OrderGeneratorThread` wastes CPU busy-waiting when the board is full. Incorrect semaphore ordering (`mutex.P()` before `emptySlots.P()`) causes permanent deadlock (`lecture3` p.19).
- **CMPE 436 Solution**:
  - `OrderBoard` uses:
    - `CountingSemaphore emptySlots = new CountingSemaphore(5)` (blocks producer when 5 orders are active).
    - `BinarySemaphore mutex = new BinarySemaphore(true)` (protects the active order list).
  - **Producer (`OrderGeneratorThread`)**:
    1. Calls `emptySlots.P()` **FIRST** (blocks without holding `mutex` when board is full!).
    2. Calls `mutex.P()`.
    3. Appends the new `Order` with a unique `orderId` and deadline.
    4. Calls `mutex.V()`.
  - **Consumer (`ClientHandlerThread` on delivery / `GameTickThread` on expiry)**:
    - Acquires `mutex.P()`, removes at most one matching/expired order, calls `mutex.V()`, and if an order was removed, calls `emptySlots.V()` to wake up the blocked `OrderGeneratorThread`.
- **Slide Ref**: `lecture3-semaphore-monitor.pdf` p.14–20 (Bounded Buffer Attempt 2 vs. Attempt 3 deadlock).
- **Invariant**: `0 <= activeOrders.size() <= 5`, and `activeOrders.size() + emptySlots.value == 5` outside critical sections.

---

### Scenario C4 — Preventing Duplicate Order Completion & Expiry Races (Atomic Transaction)
- **Gameplay Trigger**:
  1. Chef A and Chef B both carry a finished `CHEESEBURGER` and press `SERVE` at the Serving Window at the exact same instant when only **one** `CHEESEBURGER` order is active.
  2. Chef A delivers `SALAD` at the exact millisecond `GameTickThread` checks whether that `SALAD` order's timer hit `0.0s`.
- **Hazard**: **Check-Then-Act Atomicity Violation** (`lecture3` p.66–67). If matching the order and marking it completed/scoring are split across two lock acquisitions, both chefs receive points for the same order, or an already-expired order gets double-processed.
- **CMPE 436 Solution**:
  - Entire sequence runs as a **single atomic transaction** under `OrderBoard.mutex`:
    ```text
    mutex.P()
      find oldest order matching dish where !order.completed && !order.expired
      if found:
        order.completed = true
        remove order from activeOrders
        update teamScore += base + speedBonus + comboBonus
        combo++
        signalEmptySlot = true
      else:
        teamScore = max(0, teamScore - WRONG_DISH_PENALTY)
    mutex.V()
    if (signalEmptySlot) emptySlots.V()
    ```
  - `GameTickThread.expireOrders()` uses the **exact same** `OrderBoard.mutex` to check `now >= order.deadline && !order.completed`, set `order.expired = true`, remove it, deduct penalty, reset combo, and then call `emptySlots.V()`.
- **Slide Ref**: `lecture3-semaphore-monitor.pdf` p.62–67 (Issue 1: Same Lock, Issue 2: Atomic Transactions).
- **Properties**: An order transitions into **at most one** terminal state (`COMPLETED` or `EXPIRED`), never both, never twice.

---

### Scenario C5 — Shared Clean Plate Pool & Dishwasher Recycling (Counting Semaphore + Event Ordering)
- **Gameplay Trigger**: Every recipe must be plated before serving. The kitchen has **`TOTAL_PLATES = 4`**. Serving a dish consumes a plate; `4.0` seconds later, a clean plate reappears at the `PLATE_DISPENSER`.
- **Hazard**: Unlimited plate duplication breaks game balance; polling in a loop to check if a dirty plate arrived wastes CPU.
- **CMPE 436 Solution**:
  - Two custom `CountingSemaphore`s + a `BinarySemaphore` for counter visibility:
    - `dirtyPlates = new CountingSemaphore(0)` — signals `DishwasherThread` (Event Ordering, `lecture3` p.12).
    - `cleanPlates` pool (starts at `4`) — checked non-blockingly by players (`tryTakeCleanPlate()`) so a player's TCP thread never freezes if all 4 plates are currently in use/washing; instead the player receives an immediate `"WAIT_FOR_PLATE"` feedback event.
  - **`DishwasherThread` lifecycle**:
    1. Blocks on `dirtyPlates.P()` (sleeps with zero CPU until a dish is served).
    2. Sleeps `4000ms` (simulating washing).
    3. Increments available clean plates at `PLATE_DISPENSER` (under station lock / plate mutex) and emits a `"PLATE_RETURNED"` event.
- **Slide Ref**: `lecture3-semaphore-monitor.pdf` p.8–12 (Counting Semaphores & Event Ordering).
- **Invariant**: `platesInDispenser + platesOnCountersOrInHands + platesBeingWashed == TOTAL_PLATES (4)`.

---

### Scenario C6 — Multi-Resource Actions & Plating/Swapping (Deadlock Prevention via Lock Ordering)
- **Gameplay Trigger**: A player interacts with a station while holding an item — modifying **both** the `Player` inventory state AND the `Station` state atomically (e.g., combining a `COOKED_PATTY` in hand with a `PLATE(BUN)` on `Counter #3`, or swapping items between hand and station, or transferring from `Stove #1` directly onto a plated counter).
- **Hazard**: **Deadlock via Circular Wait** (`lecture3` p.28–33, `lecture4-1-deadlock.pdf` p.6–15). If Thread 1 holds `Lock(A)` and requests `Lock(B)` while Thread 2 holds `Lock(B)` and requests `Lock(A)`, both threads block forever.
- **CMPE 436 Solution**:
  - **Deadlock Prevention by Breaking Circular Wait (Total Linear Ordering)**:
    - Every lockable game object has a unique, immutable integer `resourceId`:
      - Player inventories: `0 .. 3`
      - Kitchen stations: `10 .. 49`
    - Helper `ResourceLocker.withOrderedLocks(int pid, Lockable a, Lockable b, Runnable action)` ALWAYS locks the smaller `resourceId` first, then the larger `resourceId`, and releases in reverse order inside `finally`.
- **Slide Ref**: `lecture3-semaphore-monitor.pdf` p.31, 33; `lecture4-1-deadlock.pdf` p.9, 15.
- **Proof**: Since every edge in the Wait-For / Resource Allocation Graph points strictly from lower `resourceId` to higher `resourceId`, no directed cycle can exist → **Deadlock is structurally impossible**.

---

### Scenario C7 — Event Broadcast Pipeline (Multi-Producer Single-Consumer `BoundedBuffer`)
- **Gameplay Trigger**: Up to 4 `ClientHandlerThread`s + `GameTickThread` + `OrderGeneratorThread` + `DishwasherThread` all generate outbound messages (state snapshots, chop sound triggers, order alerts, score popups) that must be sent over TCP sockets to every player in the room.
- **Hazard**: Writing directly to slow mobile TCP sockets from inside a station or order critical section stalls game simulation for all other players; unsynchronized concurrent writes to the same `Socket.getOutputStream()` corrupt framed messages (interleaved JSON bytes).
- **CMPE 436 Solution**:
  - Each room owns a `BoundedBuffer<String> broadcastQueue` (capacity `64`) built with our custom `CountingSemaphore` (`emptySlots`, `filledSlots`) + `BinarySemaphore` (`mutex`), strictly using Attempt 2 ordering (`lecture3` p.18).
  - Game threads produce serialized JSON lines into `broadcastQueue.deposit(msg)` **after** releasing gameplay locks.
  - A dedicated `RoomBroadcasterThread` blocks on `broadcastQueue.fetch()` and writes sequentially to each connected client's output stream (each `ClientConnection` also guards its own `OutputStream` with a `BinarySemaphore writeMutex` so direct error/pong replies never interleave with room broadcasts).
- **Slide Ref**: `lecture3-semaphore-monitor.pdf` p.14–20, 61.

---

### Scenario C8 — Consistent World Snapshot Reads vs. Mutations (Readers–Writers Lock)
- **Gameplay Trigger**: `GameTickThread` / `RoomBroadcasterThread` (and late-joining/syncing clients) need a consistent snapshot of the room (all players, stations, items, orders, score) so an item never appears duplicated both in a chef's hand and on a counter in the same frame.
- **Hazard**: Reading mutable room structures concurrently with structural updates causes `ConcurrentModificationException` or torn snapshots. A naive mutex blocks concurrent readers unnecessarily, while a reader-preferred RW lock (`lecture3` p.23) can starve writers.
- **CMPE 436 Solution**:
  - Custom **`ReadersWritersLock`** (writer-fair Monitor using `synchronized`, `activeReaders`, `activeWriter`, `waitingWriters`, `while (...) Util.myWait(this)`, `notifyAll()`):
    - New readers wait if a writer is active **or waiting** (`activeWriter || waitingWriters > 0`), eliminating the writer-starvation flaw highlighted on `lecture3` p.23–24!
    - Multiple snapshot readers execute concurrently; state-mutating transactions acquire write access (or per-entity ordered locks under a shared read lock for non-structural updates, and exclusive write lock for round start/reset/player join/leave).
- **Slide Ref**: `lecture3-semaphore-monitor.pdf` p.21–24, 38–58.

---

### Scenario C9 — Room Lobby Lifecycle & Thread Coordination (Java Monitor)
- **Gameplay Trigger**: Players join a room (`WAITING`), toggle `READY`, host starts the round (`PLAYING`), round timer reaches `0` (`FINISHED`), and players optionally click `PLAY_AGAIN`.
- **Hazard**: Worker threads (`GameTickThread`, `OrderGeneratorThread`, `DishwasherThread`) spinning in `while (state != PLAYING);` burn 100% CPU; using `if (state != PLAYING) wait();` suffers from spurious wakeups and Mesa-monitor race conditions (`lecture3` p.55–57).
- **CMPE 436 Solution**:
  - **`RoomStateMonitor`**:
    - `synchronized void waitUntilPlaying()`: uses a **`while (state == WAITING)`** loop calling `Util.myWait(this)` (`lecture3` p.57).
    - `synchronized void transitionTo(RoomState next)`: updates state and calls **`notifyAll()`** (`lecture3` p.71 — safer than `notify()` when multiple worker threads wait on the same monitor!).
- **Slide Ref**: `lecture3-semaphore-monitor.pdf` p.38–58, 71.

---

### Scenario C10 — Global Room Registry (Server-Wide Mutual Exclusion)
- **Gameplay Trigger**: Multiple `ClientHandlerThread`s simultaneously execute `CREATE_ROOM`, `LIST_ROOMS`, `JOIN_ROOM`, or disconnect cleanup.
- **Hazard**: Two players racing to take seat #4 of a room can both pass `players.size() < 4`, overflowing the room; creating duplicate room codes concurrently.
- **CMPE 436 Solution**:
  - `RoomRegistry` guards its `HashMap<String, Room>` with a custom **`BinarySemaphore registryMutex = new BinarySemaphore(true)`** (always released in `finally`).

---

## 3. Global Lock Hierarchy (Deadlock-Freedom Proof)

To guarantee **zero deadlocks across the entire backend**, every thread MUST respect this strict partial order when nesting locks (a thread holding a lock at Level `k` may ONLY acquire a lock at Level `> k`, never `<= k` except strictly ascending `resourceId` within Level 2):

```text
Level 0: RoomRegistry.registryMutex (BinarySemaphore)
           └─ NEVER holds any room/station/order/socket lock inside
Level 1: Room.rwLock (ReadersWritersLock) & Room.stateMonitor (Java Monitor)
           └─ Acquired before touching room entities
Level 2: Entity Locks (Player 0..3, Station 10..49 — BakeryLock)
           └─ Multiple Level-2 locks ALWAYS acquired in strictly ascending resourceId order
Level 3: OrderBoard.mutex (BinarySemaphore)
           └─ Atomic match/complete/expire only; NEVER calls blocking emptySlots.P() while held
Level 4: PlatePool.mutex (BinarySemaphore)
           └─ Non-blocking plate count update; NEVER calls blocking dirtyPlates.P() while held
Level 5: BroadcastQueue.mutex (BinarySemaphore) & ClientConnection.writeMutex (BinarySemaphore)
           └─ Leaf locks — NEVER acquire any other lock inside
```

**Critical Non-Blocking Rule for Blocking Semaphores**:
- `OrderBoard.emptySlots.P()` is called **only** by `OrderGeneratorThread` when holding **zero** other locks.
- `PlatePool.dirtyPlates.P()` is called **only** by `DishwasherThread` when holding **zero** other locks.
- `BroadcastQueue.filledSlots.P()` is called **only** by `RoomBroadcasterThread` when holding **zero** other locks.
- Player `ClientHandlerThread`s NEVER block on condition semaphores; they execute bounded critical sections and return immediately.

Because the lock acquisition graph is a strict DAG (acyclic), **Circular Wait is impossible**, proving system-wide deadlock freedom.

---

## 4. Verification & Stress-Testing Results (Verified ✅)

All concurrency primitives, gameplay race conditions, and raw TCP networking are implemented in plain Java (verified `0` occurrences of `java.util.concurrent`) and tested via `cd server && make test` (`undercooked.tests.ConcurrencyTestSuite`):

1. **`MutexStressTest`** (`server/src/undercooked/tests/MutexStressTest.java`) — **PASSED ✅**:
   - `PetersonLock` (`N=2`, 100,000 increments) → exact count, zero mutex violations.
   - `BakeryLock` (`N=6`, 120,000 increments) → exact count, zero mutex violations.
   - `HWMutex` / `TestAndSet` (`N=6`, 120,000 increments) → exact count, zero mutex violations.
   - `BinarySemaphore` (`N=6`, 120,000 increments) → exact count, zero mutex violations.
   - `ResourceLocker` ordered transfers (`N=6`, opposing `(a,b)` vs `(b,a)` pairs) → zero deadlock, total items conserved (`400`).
   - `LamportClock` (`N=6`, 60,000 ticks) → strictly monotonic final time `60,000`.
2. **`BoundedBufferStressTest`** (`server/src/undercooked/tests/BoundedBufferStressTest.java`) — **PASSED ✅**:
   - `BoundedBuffer` (`4P/4C`, `cap=8`, `40,000` items) → exact checksum, zero loss, zero duplicates, zero deadlock.
   - `CountingSemaphore` (`permits=4`, `8` threads) → `maxConcurrent == 4 <= 4`.
   - `ListQueue` Monitor (`4P/4C`, `32,000` items) → exact delivery.
3. **`ReadersWritersStressTest`** (`server/src/undercooked/tests/ReadersWritersStressTest.java`) — **PASSED ✅**:
   - Writer-fair `ReadersWritersLock` (`6R/3W`, `24,000` writes) → `maxConcurrentReaders >= 4`, zero torn reads, zero writer starvation.
4. **`DuplicateOrderRaceTest`** (`server/src/undercooked/tests/DuplicateOrderRaceTest.java`) — **PASSED ✅**:
   - `4` chefs racing for `1` active `BURGER` order across `300` rounds → strictly `1` winner every round.
   - Simultaneous completion vs. expiry race across `200` rounds → strictly `1` outcome per round (`completed=97`, `expired=103`, zero double outcomes).
   - `OrderBoard` producer blocking → `OrderGenerator` genuinely blocked at `5/5` and unblocked on `emptySlots.V()`.
5. **`StationContentionTest`** (`server/src/undercooked/tests/StationContentionTest.java`) — **PASSED ✅**:
   - `4` chefs racing for `1` cooked patty on `Stove #0` across `400` rounds → strictly `1` pickup, zero duplication.
   - `PlatePool` + `DishwasherThread` across `160` served dishes → `cleanPlates == 4/4` conserved.
6. **`TcpLoadTest`** (`server/src/undercooked/tests/TcpLoadTest.java`) — **PASSED ✅**:
   - `8` concurrent raw TCP `Socket` clients across `2` rooms → lobby, start, concurrent `MOVE`/`INTERACT`/`CHOP`, monotonically ordered `STATE_SNAPSHOT`s, `0` leaked connections on disconnect.
