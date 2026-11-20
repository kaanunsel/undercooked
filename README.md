# Undercooked — CMPE 436 Term Project

A 2D multiplayer cooperative cooking game (inspired by *Overcooked*) built for **Boğaziçi University CMPE 436: Concurrent and Distributed Programming** (Fall 2026–2027).

- **Backend (`server/`)**: Plain Java 21 raw TCP server (`java.net.ServerSocket`, `\n`-delimited JSON, explicit `Thread` per connection, zero frameworks, zero `java.util.concurrent.*` — all locks, semaphores, bounded buffers, and monitors implemented from scratch following course lectures).
- **Mobile Client (`mobile/`)**: Bare React Native (`@react-native-community/cli`, Android APK & iOS native builds) + TypeScript (`react-native-tcp-socket`, `@shopify/react-native-skia`, Reanimated, Gesture Handler, Zustand, React Navigation, `react-native-sound`).
- **Infrastructure (`infra/terraform/`)**: Single GCP Compute Engine VM managed via Terraform (`project-72f15835-9e20-4691-877`).

## Quick Start

### 1. Java TCP Server & Concurrency Stress Tests
```bash
cd server
make test          # Verifies 0 java.util.concurrent imports + runs all 6 stress test suites
make jar           # Builds dist/undercooked-server.jar
make run           # Starts TCP server on port 6789
# Live classroom demo with concurrency event logging:
java -jar dist/undercooked-server.jar 6789 --log-concurrency
```

### 2. Deploy Single VM to GCP (Terraform)
```bash
cd infra/terraform
terraform init
terraform apply    # Provisions e2-small VM, static IP, TCP :6789 firewall, OpenJDK 21 + systemd
./deploy.sh        # Builds JAR, uploads via gcloud scp, restarts undercooked.service
```

### 3. Mobile Client (Bare React Native — Android APK & iOS)
```bash
cd mobile
npm install --legacy-peer-deps

# Android (Dev / Release APK for classmates)
npx react-native run-android
cd android && ./gradlew assembleRelease
# Output APK: mobile/android/app/build/outputs/apk/release/app-release.apk

# iOS (requires Xcode & CocoaPods)
cd ios && pod install && cd ..
npx react-native run-ios
```

## Documentation
- [AGENTS.md](AGENTS.md) — Hard course constraints, local CMPE 436 slide references, and development guidelines.
- [GAME_DESIGN.md](GAME_DESIGN.md) — Rules, 12×8 kitchen layout, recipes, example multiplayer scenarios, tech stack, and wire protocol.
- [CONCURRENCY.md](CONCURRENCY.md) — Detailed register of all concurrency scenarios, CMPE 436 primitive mappings, deadlock-freedom proof, and verified stress tests.
- [EXECUTION_PLAN.md](EXECUTION_PLAN.md) — Monorepo structure and phased implementation roadmap.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — Overall architecture and Mermaid sequence diagrams.
