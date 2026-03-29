## Echoes — A Fabric Mod for World Memory

Echoes is a **Fabric 1.21.x** mod that makes Minecraft worlds remember what happened in them. Significant player moments — deaths, discoveries, milestones, long journeys — leave behind ghostly echoes at the places they occurred. Other players (or you, returning later) can encounter these echoes as translucent ghosts replaying the original moment.

This repository contains the implementation of the Echoes mod, enabling players to witness and replay visually rich, memorable moments in the Minecraft world.

---

## Project Status

- **Stage**: Beta.
- **Minecraft**: 1.21.x (see `gradle.properties` for exact version).
- **Loader**: Fabric Loader (see `fabric.mod.json`).
- **Java**: 21.

Feel free to use this build in your servers, and please report any issues you encounter.

---

## Development Setup

1. Install:
   - Java 21 (JDK).
   - A recent Gradle‑capable environment (Gradle wrapper is included).
2. Clone the repository and run:

```bash
./gradlew runClient
```

This starts a development client with Echoes loaded.

---

## High‑Level Architecture

The implementation is organized as:

- `Echoes` / `EchoesClient`: main + client Fabric entrypoints.
- `data/`: echo records, frames, tiers, event types, player echo data, world state.
- `events/`: Fabric event listeners that detect in‑game events and create echoes.
- `network/`: custom packets for echo playback, sense, and manual recording.
- `entity/` & `render/`: client‑side ghost entity and renderer.
- `item/`: Echo Crystal and any future echo‑related items/blocks.
- `command/`: `/echoes` commands (opt‑out, clear, debug, etc.).
- `config/`: TOML‑backed configuration (`config/echoes.toml`).

---

## Profiling & Performance Verification

Echoes is designed to be **safe for SMPs**, but you should still profile it on your own setup:

- **Use a server profiler** (e.g. [Spark](https://spark.lucko.me/)) alongside Echoes on a dev server.
- **Exercise worst‑case behavior**:
  - Many deaths in a small area (stress `RecordingSessionManager`).
  - High echo density around spawn (stress `PlaybackTriggerService`).
- **Inspect the profile**:
  - Verify that `PlaybackTriggerService.onServerTick` remains a small fraction of the tick budget, even under synthetic load.
  - Confirm that frame capture and echo creation stay under budget (well below **1 ms per capture burst** on your hardware).

If profiling ever shows Echoes as a top‑level hot spot, please open an issue with your profile report and config so we can tune caps, scan intervals, or algorithms.

---

## Contributing

At this stage, the project is early and APIs may move around. If you open a PR:

- Keep changes small and focused.
- Match existing code style and package layout.
- Avoid introducing dependencies beyond Fabric API without discussion.

Bug reports and design feedback that reference specific scenarios (singleplayer vs SMP, modpack context, etc.) are especially helpful.

