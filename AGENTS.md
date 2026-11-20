# AGENTS.md — Undercooked (CMPE 436 Term Project)

## Core Motivation
**Undercooked** is a 2D multiplayer cooperative cooking game (inspired by *Overcooked*) built for **Boğaziçi University CMPE 436: Concurrent and Distributed Programming** (Prof. Alper Şen, Fall 2026–2027).

The primary grading criterion is **genuine, textbook-faithful concurrent and distributed programming in plain Java** — not over-engineered infrastructure or auth. Every shared kitchen resource (stations, orders, plates, room state, client sockets) must visibly and verifiably apply concurrency primitives taught in class.

---

## Hard Course Constraints (DO NOT VIOLATE)

### 1. Plain Java Backend — Zero Frameworks
- Write the backend in **plain Java (JDK 21)** using only core standard library packages (`java.lang`, `java.io`, `java.net`, `java.util`).
- **FORBIDDEN**: Spring, Netty, Vert.x, Tomcat, Jetty, Undertow, Java-WebSocket, Gson/Jackson (write a minimal JSON serializer/parser in plain Java — keep dependencies at **zero**).
- Build with plain `javac` / `jar` (or a minimal shell script / `Makefile`).

### 2. Hand-Rolled Concurrency Primitives — NO `java.util.concurrent.*`
- **FORBIDDEN**: `ReentrantLock`, `ReadWriteLock`, `Semaphore`, `BlockingQueue`, `ArrayBlockingQueue`, `ConcurrentHashMap`, `CopyOnWriteArrayList`, `ExecutorService`, `ThreadPoolExecutor`, `CountDownLatch`, `CyclicBarrier`, `Atomic*`.
- **REQUIRED**: Implement our own primitives in `server/src/undercooked/concurrency/` mirroring Prof. Alper Şen's CMPE 436 slides:
  - `Lock` interface (`requestCS(int pid)`, `releaseCS(int pid)`) → `PetersonLock`, `BakeryLock` (Lamport's Bakery), `HWMutex` (`TestAndSet`).
  - `Util.myWait(Object)` and `Util.mySleep(long)`.
  - `BinarySemaphore` (`P()`, `V()`) and `CountingSemaphore` (`P()`, `V()`) built on `synchronized` + `wait()` + `notify()`/`notifyAll()`.
  - `BoundedBuffer<T>` (Producer–Consumer with `CountingSemaphore` + `BinarySemaphore`, preserving deadlock-free `P()` ordering).
  - `ReadersWritersLock` (built from custom semaphores / monitor).
  - Java Monitors (`synchronized` + `while (!cond) wait()` + `notifyAll()`, e.g., `ListQueue<T>`, `RoomStateMonitor`).
  - Strict linear resource ordering (`resourceId`) for multi-resource operations to prevent deadlocks (break Circular Wait).
- *JMM note*: For software locks (`PetersonLock`, `BakeryLock`), wrap per-slot flags/tickets in tiny `volatile` holder objects (plain Java `volatile`, NOT `java.util.concurrent.atomic`) and call `Thread.yield()` inside spin loops to ensure visibility on multi-core JVMs without pegging CPU.

### 3. Explicit Multithreaded TCP Server (`java.net.ServerSocket`)
- Implement the TCP server explicitly following `lecture4-2.pdf`:
  - Main accept loop on `ServerSocket(port)` calling `accept()`.
  - Spawn an explicit Java `Thread` (`new ClientHandlerThread(socket, server).start()`) for every incoming client connection.
  - Spawn explicit room worker threads (`GameTickThread`, `OrderGeneratorThread`, `DishwasherThread`, `RoomBroadcasterThread`) — **no thread pools / executors**.
- **Pure Raw TCP Byte Stream (`\n`-delimited JSON)** directly over `BufferedReader` / `OutputStream` — NO WebSocket, NO HTTP framing. Identical to `lecture4-2.pdf` slides 45–50. Both the bare React Native app (`react-native-tcp-socket`) and raw TCP CLI / Java test clients connect to the exact same TCP port (`6789`).

### 4. Keep Everything Else As Simple As Possible
- Classroom demo only (no public App Store release, no user accounts, no passwords, no database, no TLS/OAuth, no auto-scaling).
- Always keep `CONCURRENCY.md` updated whenever a concurrency scenario or primitive is added, changed, or verified.

---

## Local Course Reference Slides
All concurrency patterns and networking structures should stay aligned with these local CMPE 436 lecture PDFs:

- **Slides Directory**: `/Users/kaanunsel/Library/Mobile Documents/com~apple~CloudDocs/Eğitim Materyalleri/Üniversite Materyalleri/2026-2027 : Fall/CMPE 436/slides`
- **Syllabus**: `/Users/kaanunsel/Library/Mobile Documents/com~apple~CloudDocs/Eğitim Materyalleri/Üniversite Materyalleri/2026-2027 : Fall/CMPE 436/syllabus.pdf`

| Slide File | Path | Key Concepts to Mirror |
|---|---|---|
| `lecture1.pdf` | `.../slides/lecture1.pdf` | Processes, threads, race conditions, critical sections |
| `lecture2.pdf` | `.../slides/lecture2.pdf` | Java `Thread`/`Runnable`/`join`, `Lock` interface, Peterson's, Lamport's Bakery, `TestAndSet`/`HWMutex` |
| `lecture3-semaphore-monitor.pdf` | `.../slides/lecture3-semaphore-monitor.pdf` | `BinarySemaphore`, `CountingSemaphore`, `BoundedBuffer`, `ReaderWriter`, Dining Philosophers, Java Monitors (`while` + `wait` + `notifyAll`), `ListQueue`, Atomic Transactions |
| `lecture4-1-deadlock.pdf` | `.../slides/lecture4-1-deadlock.pdf` | 4 Deadlock Conditions, RAG, Deadlock Prevention (lock ordering), Banker's Algorithm |
| `lecture4-2.pdf` | `.../slides/lecture4-2.pdf` | `java.net.ServerSocket`, `Socket`, `InputStream`/`OutputStream`, `BufferedReader`, TCP server pattern |
| `lecture4-race.pdf` | `.../slides/lecture4-race.pdf` | Data race detection, lockset discipline, happens-before |
| `lecture5-2.pdf` | `.../slides/lecture5-2.pdf` | Logical clocks (Lamport timestamps) for event ordering |
| `lecture10.pdf` | `.../slides/lecture10.pdf` | Message ordering (FIFO, Causal, Total) |

---

## Tech Stack & Infrastructure Summary
- **Backend (`server/`)**: Plain Java 21, zero third-party dependencies, pure raw TCP `ServerSocket` on port `6789` (`\n`-delimited JSON).
- **Mobile (`mobile/`)**: Bare **React Native** (`@react-native-community/cli`, **NO Expo**) + TypeScript, direct Android (`.apk`) & iOS native builds, `react-native-tcp-socket` (native TCP client matching `lecture4-2.pdf`), `@shopify/react-native-skia` (2D top-down kitchen), `react-native-reanimated`, `react-native-gesture-handler`, `zustand`, `@react-navigation/native` + `@react-navigation/native-stack`, `react-native-sound`, landscape gameplay (`react-native-orientation-locker` / native config).
- **Infrastructure (`infra/terraform/`)**: Single GCP Compute Engine VM (`e2-small`, Ubuntu 24.04 LTS, static IP, firewall TCP `6789`), managed via Terraform.
  - **GCP Project ID**: `project-72f15835-9e20-4691-877` (project name: `undercooked`).

---

## Project Documentation Map
- [GAME_DESIGN.md](GAME_DESIGN.md) — Rules, 12×8 kitchen layout, recipes, scenarios, wire protocol, finalized stack.
- [CONCURRENCY.md](CONCURRENCY.md) — Every concurrency scenario, CMPE 436 primitive mapping, deadlock-freedom proof, and stress tests (**keep updated!**).
- [EXECUTION_PLAN.md](EXECUTION_PLAN.md) — Phased implementation plan, repo structure, verification gates.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — Overall architecture and sequence diagrams.
