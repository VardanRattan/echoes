# ECHOES — Development Document
### A Fabric Minecraft Mod
**Version**: 1.0 (Aligned Spec)  
**Target**: Minecraft 26.1.1 (Fabric Loader 0.18.6+, Java 25)  
**Scope**: Solo / SMP  
**Status**: Feature-Complete & Aligned

---

## Table of Contents

1. [Vision & Philosophy](#1-vision--philosophy)
2. [Core Concept](#2-core-concept)
3. [Echo Taxonomy — What Gets Recorded](#3-echo-taxonomy--what-gets-recorded)
4. [Recording System — Technical Design](#4-recording-system--technical-design)
5. [Playback System — Technical Design](#5-playback-system--technical-design)
6. [Visual & Audio Language](#6-visual--audio-language)
7. [The Echo Crystal — Player-Facing Item](#7-the-echo-crystal--player-facing-item)
8. [Multiplayer Architecture](#8-multiplayer-architecture)
9. [Data Storage & Performance](#9-data-storage--performance)
10. [Configuration & Server Admin Controls](#10-configuration--server-admin-controls)
11. [Edge Cases & Failure Modes](#11-edge-cases--failure-modes)
12. [MVP Feature Scope (Week-by-Week)](#12-mvp-feature-scope-week-by-week)
13. [Post-MVP Roadmap](#13-post-mvp-roadmap)
14. [Differentiation & Competitive Positioning](#14-differentiation--competitive-positioning)
15. [Success Metrics](#15-success-metrics)
16. [Open Design Questions](#16-open-design-questions)

---

## 1. Vision & Philosophy

### The Problem We're Solving

Minecraft worlds have no memory.

You can spend 200 hours on a server — build a massive base, die in the nether a dozen times, be the first person to find the stronghold — and the world retains zero evidence of any of it. Remove your player, and the world is indifferent. It doesn't know you existed.

This is especially pronounced on multiplayer servers, where a world is theoretically *shared* — but in practice each player is playing a parallel solo game. You see other players in real time, but you have no relationship with their *history* in the world.

### The Solution

**Echoes** makes the world remember.

Significant player moments — deaths, discoveries, milestones, long journeys — leave behind ghostly imprints at the coordinates where they occurred. Other players (or you, returning later) can encounter these echoes: a translucent, desaturated ghost of the original player performing the original action, fading after a few seconds.

No UI. No map markers. No XP numbers. Just a ghost, briefly, where something happened.

### Design Philosophy

> **"The world should feel like it has been lived in."**

Every design decision runs through this filter. If a feature makes the world feel more *inhabited*, it ships. If it turns Echoes into a tracking tool, a stats dashboard, or a game mechanic with numbers — it doesn't.

**Principles:**
- **Ambient over active.** Echoes appear without being summoned. The player doesn't manage them.
- **Emotional over informational.** An echo should create a feeling, not deliver data.
- **Scarcity preserves meaning.** Not every action becomes an echo. Only significant ones. Commonness kills wonder.
- **Invisible infrastructure.** The best session with Echoes installed is one where the player forgets it's a mod.
- **No grief vectors.** Echoes cannot be used to track live player positions, stalk players, or gain competitive advantage.

---

## 2. Core Concept

### What Is an Echo?

An echo is a **passive, location-bound, time-limited ghost recording** of a player performing a significant action at a specific coordinate.

When a player passes within **trigger range** of an echo they haven't seen before, the echo **plays back once**: a semi-transparent version of the original player performs the recorded action in place, accompanied by a subtle particle effect and muted sound. After playback, the echo is marked as "seen" for that player and won't replay unless manually reset.

Echoes are:
- **Passive** — they appear without player input
- **Non-interactive** (base version) — you can't click them, loot them, or destroy them
- **Time-limited** — they decay after a configurable period (default: 30 real days)
- **Coordinate-bound** — anchored to the exact location where the event occurred
- **Multiplayer-shared** — on servers, all players can see other players' echoes

Echoes are not:
- Live player tracking
- Death loot markers
- Quest objectives
- Stats or achievement logs

---

## 3. Echo Taxonomy — What Gets Recorded

Echoes are tiered by emotional weight. **Tier determines visual intensity, particle density, and decay time.**

### Tier 1 — Whisper Echoes
*Faint, brief, easily missed. Common.*

| Event | Trigger Condition | Recording Length |
|---|---|---|
| **First Biome Visit** | Player enters a biome type for the first time in this world | 3 seconds (player looking around) |
| **Long Journey End** | Player travels 500+ blocks from last rest point in one session | 3 seconds (player stopping, standing) |
| **First Sleep** | First time a player uses a bed in the world | 3 seconds |
| **First Trade** | First successful villager trade | 3 seconds |

### Tier 2 — Mark Echoes
*Clearly visible, moderate particle effect. Uncommon.*

| Event | Trigger Condition | Recording Length |
|---|---|---|
| **Death** | Player death (any cause) | 5 seconds (death animation + fall) |
| **Structure Discovery** | Player enters a structure for the first time (stronghold, mansion, temple, etc.) | 5 seconds |
| **First Nether/End Entry** | Player steps through a portal dimension for the first time | 5 seconds |
| **Major Craft** | First crafting of: diamond tools, netherite gear, enchanting table, beacon, elytra equip | 5 seconds |
| **Taming** | Player successfully tames a mob | 4 seconds |

### Tier 3 — Scar Echoes
*Dramatic, high particle density, long duration. Rare.*

| Event | Trigger Condition | Recording Length |
|---|---|---|
| **Boss Kill** | Ender Dragon, Wither, Elder Guardian defeated | 8 seconds |
| **First Elytra Flight** | Player equips elytra and launches for the first time | 8 seconds |
| **Catastrophic Death** | Death with 30+ levels of XP or full enchanted gear | 8 seconds |
| **Marathon Journey** | Player travels 2000+ blocks in a single session without teleport | 6 seconds |
| **World First** | First player in a world/server to reach End, kill Dragon, find Stronghold | 10 seconds (unique golden tint) |
| **Manual Crystal Mark** | Player manually records a memory with Echo Crystal (Sneak + Right-click) | 8 seconds |

### Design Notes on Taxonomy

- **Death echoes are the anchor product.** They're the most emotionally resonant, universally relatable ("we've all died to something stupid"), and most likely to be shared. Every other echo category supports this core.
- **"World First" echoes** are the multiplayer flex — the server's collective history of who did what first. These are prestigious. They should feel different visually.
- **Tier 1 echoes decay faster** (7 days default) to prevent world clutter from mundane events. Tier 3 echoes persist longest (60 days default).
- **Config allows server admins to disable any tier or specific event type** individually.

---

## 4. Recording System — Technical Design

### What We Record

Each echo stores the minimum viable data to reconstruct a recognizable ghost:

```
EchoRecord {
  uuid: UUID                    // unique echo ID
  playerUUID: UUID              // who created it (for skin rendering)
  playerName: String            // display name at time of recording
  eventType: EchoEventType      // enum: DEATH, DISCOVERY, BOSS_KILL, etc.
  tier: EchoTier                // WHISPER, MARK, SCAR
  dimension: RegistryKey        // overworld / nether / end / modded dims
  anchorPos: BlockPos           // where the echo is anchored
  timestamp: Long               // world time tick when recorded
  realTimestamp: Long           // real-world epoch ms (for decay calculation)
  frames: List<EchoFrame>       // positional + rotation snapshots
  seenBy: Set<UUID>             // players who have already witnessed this echo
}

EchoFrame {
  relX: float    // relative to anchorPos (keep values small)
  relY: float
  relZ: float
  yaw: float
  pitch: float
  limbSwing: float
  animationState: EchoAnimState  // WALKING, IDLE, DYING, FALLING, etc.
  tickOffset: int                // frame's position in the playback timeline
}
```

### Recording Resolution

- **Frame capture rate**: Every 2 ticks (10 fps equivalent) — sufficient for recognizable motion, minimal storage
- **Max frames per echo**: 100 frames (covers ~10 seconds at 10fps)
- **Relative coordinates**: All frame positions stored relative to `anchorPos` — keeps values small, makes echoes portable

### Event Detection — Hook Points

```
// Death (Normal & Catastrophic)
ServerLivingEntityEvents.AFTER_DEATH → filter for ServerPlayer
  ↳ Evaluates XP level (>= 30) or full enchanted armor for CATASTROPHIC_DEATH
  ↳ Safe death position: onGround + !isInLava + !below.isAir + !below.is(Lava)

// Structure discovery
DiscoveryEchoHandler.onWorldTick → world.structureManager().getStructureWithPieceAt(pos)
  ↳ Deduplicated via hasDiscoveredStructureNear(structureId, pos, 256.0)

// Dimension transition  
DiscoveryEchoHandler.onWorldTick → world.dimension() vs player's visitedDimensions

// Boss kills
ServerLivingEntityEvents.AFTER_DEATH → filter for EnderDragon, WitherBoss, ElderGuardian
  ↳ Granular World First keys (e.g. world_first:BOSS_KILL:ender_dragon)
  ↳ Anchored to killer player position

// Crafting milestones
ResultSlotMixin.onTake → checks item against milestone set (Diamond, Netherite, Beacon, etc.)

// Trading milestones
MerchantResultSlotMixin.onTake → FIRST_TRADE on successful villager transaction

// Sleeping milestones
ServerPlayerSleepMixin.startSleeping → FIRST_SLEEP on valid bed sleep

// Taming milestones
TamableAnimalMixin.tame → TAMING on pet tame

// Biome discovery
DiscoveryEchoHandler.onWorldTick (throttled to 20 ticks) → world.getBiomeManager().getBiome(pos)

// Journey tracking
JourneyEchoHandler.onWorldTick → tick-by-tick distance accumulator (delta > 0.0001)
  ↳ Teleport detection threshold (distSq > 100.0 resets session)
  ↳ Elytra flight detection (player.isFallFlying)
```

### Player State at Capture

At the moment of event trigger, record:
- Full equipment snapshot (head, chest, legs, feet, mainhand, offhand) via `EquipmentSnapshot`
- Player skin (via UUID — resolved or anonymized based on privacy settings)
- Exact position + look direction (yaw, pitch)
- Discrete animation states (`EchoAnimState`: IDLE, WALKING, RUNNING, JUMPING, FALLING, CROUCHING, SWIMMING, ELYTRA_FLYING, DYING)

---

## 5. Playback System — Technical Design

### Trigger Logic

Every 10 ticks (0.5 seconds), for each online player:
1. Query echo storage for echoes within **trigger radius** (default: 16 blocks) in their current dimension via spatial chunk indexing (`chunkEchoMap`)
2. Filter out echoes already in `seenBy` for this player (unless `repeat-death-echoes` is enabled)
3. Filter out echoes if `canPlayerSeeEcho(playerUuid, echo)` returns false (opt-out / self-visibility rules)
4. Sort by distance, take closest unplayed echo
5. Downsample frames by 75% for bandwidth reduction and send `EchoPlaybackPayload`
6. Begin playback

**Only one echo plays per player at a time.** Queued echoes wait until current playback finishes. This prevents sensory overload in echo-dense areas.

### Ghost Entity — Rendering Approach

**Decision: Option B (Client-side ghost entity controller)**

Implementation:
- Lightweight controller `GhostPlayerEntity` (client-only, no server tick overhead)
- Client receives `EchoPlaybackPayload` and deserializes downsampled frames
- Smooth 60fps reconstruction via Catmull-Rom positional splines and Slerp angular interpolation
- **Geometry & Matrix Orientation**:
  ```java
  poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
  // Animation-specific tilts:
  // DYING: Z-axis tilt progression up to 90 degrees
  // ELYTRA_FLYING: X-axis 90-degree horizontal orientation
  poseStack.scale(-1.0F, -1.0F, 1.0F);
  poseStack.translate(0.0F, -1.501F, 0.0F);
  ```
- **Skin & Outer Layers**: Outer layers (`showHat`, `showJacket`, `showLeftPants`, `showRightPants`, `showLeftSleeve`, `showRightSleeve`, `showCape`) explicitly enabled.
- **Equipment Rendering**: Helmet, chestplate/elytra, leggings, boots, and held items populated onto `AvatarRenderState`.
- **Pipeline & Fallback**: Custom `GHOST_PIPELINE` with automatic fallback to `RenderTypes.entityTranslucent(tex)`.
- **Right-Click Inspection**: Holding an Echo Crystal and right-clicking an active ghost within 10 blocks inspects author name, relative age, and event type via `UseItemCallback`.

### Playback Sequence

```
1. Server detects player in range of unseen echo
2. Server downsamples frames and sends EchoPlaybackPayload { echoRecord, playerUuid, frames }
3. Client receives payload → initializes GhostPlayerEntity controller
4. Client adds to activeGhosts; sound & particles burst on start
5. Intro: ghost fades IN over first 20 ticks (alpha 0 → configured tier opacity)
6. Playback: Catmull-Rom spline evaluates pose & animations for recording duration
7. Outro: ghost fades OUT over last 30 ticks; plays subtle glass break audio on completion
8. Client sends EchoSeenPayload back to server
9. Server marks player in echo.seenBy and updates PlayerEchoData
```

### Opacity & Visual Modifiers by Tier

| Tier | Base Opacity | Particle Density | Color Tint | Sound |
|---|---|---|---|---|
| Whisper | 25% | Sparse | Slight blue-grey desaturation | Near-silent whoosh |
| Mark | 45% | Moderate | Clear desaturation, slight blue | Soft ambient chord |
| Scar | 70% | Dense | Near-white with color flash at start | Distinct echo sound |
| World First | 70% | Dense + trails | Gold tint | Distinct + longer reverb |

---

## 6. Visual & Audio Language

### Visual Identity: What Does a Ghost Look Like?

The ghost should read as:
- **Clearly not a real player** — cannot be mistaken for a live player
- **Clearly a past player** — recognizably human, wearing the right skin and gear
- **Emotionally evocative** — slightly uncanny, not cartoonish

Visual treatment:
- **Desaturated** — colors washed out to ~30% saturation
- **Translucent** — see-through, alpha varies by tier
- **Slightly blue-shifted** — cool color temperature reads as "past / memory"
- **No name tag** — ghost has no floating name above head
- **Ambient particle halo** — small particles orbit the ghost (vanilla soul sand particles work for this aesthetic, or end rod particles)
- **No shadow** — ghosts don't cast shadows, reinforcing their non-physical nature
- **Subtle outline** (optional, configurable) — thin outline for visibility in dark areas

### Sound Design

Use only vanilla sounds + pitch/reverb manipulation. No custom audio assets in MVP.

| Moment | Sound | Treatment |
|---|---|---|
| Echo detected (nearby, not yet playing) | `block.amethyst_block.chime` | Very quiet, 0.15 volume |
| Echo begins playback | `entity.enderman.ambient` | Pitched up 1.5x, 0.3 volume, reverbed |
| Death echo specifically | `entity.player.death` | Filtered, distant, 0.4 volume |
| Boss kill echo | `ui.toast.challenge_complete` | Distant, 0.5 volume |
| Echo fades out | `block.glass.break` | Pitched up 2x, 0.1 volume |

All sounds spatially positioned at anchor location. Players who don't want sound can mute via standard Minecraft sound sliders (categorize under "Players" sound channel).

### Particle Systems (Vanilla Only for MVP)

| Tier | Particle Type | Behavior |
|---|---|---|
| Whisper | `minecraft:soul` | 1-2 particles/sec, drift upward, fade |
| Mark | `minecraft:soul` + `minecraft:enchant` | 3-4/sec, orbit ghost |
| Scar | `minecraft:soul` + `minecraft:end_rod` | 8-10/sec, dramatic drift |
| World First | `minecraft:soul` + `minecraft:end_rod` + `minecraft:flash` | Trail + burst on spawn |

---

## 7. The Echo Crystal — Player-Facing Item

### Why This Item Exists

The mod is 95% passive and ambient. But players need *one* active touchpoint — something they can craft, hold, and use that makes Echoes feel like a deliberate part of their world, not just a background process.

The Echo Crystal also solves a discoverability problem: new players on a server can use it to find nearby echoes rather than stumbling on them. And it enables manual echo creation for moments the automatic system doesn't capture.

### Crafting Recipe

```
  [Amethyst Shard] [Amethyst Shard] [Amethyst Shard]
  [Amethyst Shard] [Ghast Tear]     [Amethyst Shard]
  [Amethyst Shard] [Amethyst Shard] [Amethyst Shard]
```

Rationale: Amethyst is associated with memory and time in vanilla lore. Ghast tears (rare, from the nether) add a cost gate that keeps the item from being trivially craftable on day one but reachable within a week of normal play.

### Item Functions

**Right-click (empty hand on item):**
Pulses a 32-block "echo sense" — nearby echoes briefly glow with a visible particle burst, showing their locations without playing them back. Duration: 3 seconds. Cooldown: 30 seconds.

**Sneak + Right-click:**
**Manual echo recording.** Records the next 8 seconds of your movement and saves it as a Tier 3 (Scar) echo at your current position. Consumes one durability (Crystal has 16 uses). Use case: Players can manually mark a location that meant something to them — a spot where they built something, where something funny happened, where they want to leave a message in ghost-form.

**Right-click on existing echo (if echo interaction is enabled in config):**
Shows echo metadata: who created it, when (relative: "3 weeks ago"), what event type. Brief tooltip, no full UI.

### Item Tooltip

```
Echo Crystal
An amethyst resonant with memory.
Reveals the echoes of those who walked here.
[Right-click] Sense nearby echoes
[Sneak + Right-click] Leave your mark
```

---

## 8. Multiplayer Architecture

### Server-Side Responsibilities

- **All recording logic runs server-side** — event hooks, frame capture, storage writes
- **Echo storage is authoritative on the server** — clients never write echoes directly
- **Playback triggering is server-side** — server decides when to send playback packets
- **Seen-state tracking is server-side** — prevents re-triggering on reconnect
- **Admin controls apply server-wide** — per-player opt-out respected via player data

### Client-Side Responsibilities

- Ghost entity rendering
- Particle and sound playback
- Echo Crystal visual effects
- Sending `EchoSeenPacket` after playback completes

### Packet Types

```
// Server → Client (PayloadTypeRegistry.clientboundPlay)
EchoPlaybackPayload {
  echoId: UUID
  dimension: ResourceKey<Level>
  anchorPos: BlockPos
  tier: EchoTier
  eventType: EchoEventType
  playerUuid: UUID        // for skin lookup (or null if anonymized)
  playerName: String      // display name (or "Anonymous")
  realTimestamp: Long
  equipment: EquipmentSnapshot
  frames: List<EchoFrame> // downsampled 75% for bandwidth optimization
}

EchoSensePayload {
  // Response to Crystal right-click
  nearbyEchoes: List<SenseEntry{echoId, pos, tier, eventType, playerName, timestamp}>
}

// Client → Server (PayloadTypeRegistry.serverboundPlay)
EchoSeenPayload {
  echoId: UUID
}
```

*Note: Manual recording sessions are initiated server-authoritatively when using `EchoCrystalItem` on a `ServerPlayer`, ensuring anti-cheat security and eliminating redundant client-to-server trigger packets.*

### Player Opt-Out

Players can opt out of two things independently:
1. **Their echoes being visible to others** — `echoes opt-out visibility`
2. **Seeing other players' echoes** — `echoes opt-out display`

These are set via a simple command: `/echoes optout [visibility|display|all]`

Visibility opt-out is important for privacy-conscious players and streamers who don't want their patterns recorded.

### Compatibility with Offline/Singleplayer

In singleplayer, all echoes are your own. The experience shifts from "discovering the ghosts of others" to "encountering your own past self" — which is a genuinely different and still resonant experience. Returning to a death site and seeing yourself die is powerful.

Singleplayer also functions as the development testing environment since you don't need a server to see echoes work.

---

## 9. Data Storage & Performance

### Storage Architecture

**Per-world PersistentState** (extends `PersistentState`):

```
EchoWorldState {
  // Spatial index: chunk position → list of echoes in that chunk
  chunkEchoMap: HashMap<ChunkPos, List<EchoRecord>>
  
  // Per-player tracking
  playerData: HashMap<UUID, PlayerEchoData>
}

PlayerEchoData {
  discoveredBiomes: Set<RegistryKey<Biome>>
  discoveredStructures: Set<StructurePos>  // structure type + approx location
  visitedDimensions: Set<RegistryKey<World>>
  craftedMilestones: Set<String>           // item IDs
  seenEchos: Set<UUID>
  optedOut: boolean
  displayOptedOut: boolean
  sessionOrigin: BlockPos                  // for journey tracking
  sessionDistanceTraveled: float
}
```

### Storage Limits

Default caps (all configurable):

| Scope | Default Cap | Rationale |
|---|---|---|
| Per chunk | 8 echoes | Prevents dense-area bloat |
| Per player per world | 50 echoes | Hard cap on a single player's history |
| Global per world | 2000 echoes | Server-wide ceiling |
| Frame data per echo | 100 frames | ~10 sec at 10fps |

When cap is reached: **oldest echo of same or lower tier is evicted**. A Scar echo won't be evicted to make room for a Whisper echo.

### Decay System

Echoes have a `realTimestamp` field. A background task runs on server startup and every 24 real hours:

```
for each echo in world:
  age = now - echo.realTimestamp (in days)
  if age > decayDays(echo.tier):
    delete echo
```

Default decay periods:

| Tier | Default Decay |
|---|---|
| Whisper | 7 real days |
| Mark | 30 real days |
| Scar | 60 real days |
| World First | Never (or configurable, default infinite) |

Admins can disable decay entirely (`decay-enabled: false`) for archival servers.

### Performance Budgets

**Recording overhead**: Negligible. Event hooks fire infrequently. Frame capture during recording runs for max 10 seconds, capturing every 2 ticks — ~1ms of work per captured frame.

**Playback trigger scan**: Runs every 10 ticks per online player. Queries `chunkEchoMap` for player's current chunk + adjacent 8 chunks (3x3 grid). With the per-chunk cap of 8, this is at most 72 echo lookups per scan — trivially fast with HashMap.

**Ghost entity rendering**: Client-side only. One ghost entity at a time per player. No server-side entity tick.

**Packet size**: A full Scar echo with 80 frames, equipment snapshot, etc. is approximately 2-4KB. Acceptable for a one-time playback trigger.

**Worst case**: 50 players online, all triggering echoes simultaneously → 50 playback packets sent in same tick. Each 4KB = 200KB burst. Completely within normal server network capacity.

---

## 10. Configuration & Server Admin Controls

Full config file at `config/echoes.toml`:

```toml
[general]
# Enable/disable the entire mod server-side
enabled = true

# Allow players to see their own echoes
self-echoes-visible = true

# Allow players to opt out of recording
allow-player-optout = true

[triggers]
# Enable/disable specific echo types
death = true
structure-discovery = true
boss-kill = true
dimension-enter = true
major-craft = true
biome-discovery = true
taming = true
world-first = true
manual-crystal = true

# Journey distance thresholds (in blocks)
journey-tier1-distance = 500
journey-tier2-distance = 2000

[decay]
enabled = true
whisper-days = 7
mark-days = 30
scar-days = 60
world-first-days = -1  # -1 = never

[limits]
max-echoes-per-chunk = 8
max-echoes-per-player = 50
max-echoes-global = 2000

[playback]
# Trigger radius in blocks
trigger-radius = 16

# Max simultaneous echo playbacks per player
max-concurrent = 1

# Whether death echoes replay repeatedly on proximity (default: false, plays once per player)
repeat-death-echoes = false

# Opacity multiplier (0.0 - 1.0)
whisper-opacity = 0.25
mark-opacity = 0.45
scar-opacity = 0.70

[performance]
# Ticks between playback trigger scans per player
scan-interval-ticks = 10

# Async frame capture (recommended true)
async-recording = true

[privacy]
# Disable echo of player names in tooltip
hide-player-names = false

# Anonymize echoes (ghost has no skin, generic Steve appearance)
anonymize-all = false
```

---

## 11. Edge Cases & Failure Modes

### Edge Case Handling

| Scenario | Handling |
|---|---|
| Player dies in the void | Anchor to last solid position before void fall |
| Player dies in lava | Echo plays, fire particle overlay added |
| Player uses /tp or teleports | Journey tracking resets (no teleport-assisted journey echoes) |
| Player disconnects mid-recording | Discard partial recording (don't save incomplete echoes) |
| Chunk unloads during playback | Playback packet already sent to client — client handles it locally, no issue |
| Player skin not loaded | Render ghost with default Steve skin, queue async skin fetch, update on next encounter |
| Two players die at exact same block | Both echoes stored — they play back sequentially when triggered |
| Player opts out, then opts back in | Historical echoes (from before opt-out) are gone; new echoes recorded from opt-in forward |
| World corruption | EchoWorldState fails gracefully — log error, initialize fresh state, world continues |
| Very old echo in moved/destroyed area | Echoes are positional, not block-attached. A death echo at a coordinate plays even if the ground is gone. This is intentional — it's more haunting. |
| Server running Paper/Purpur instead of vanilla | Tested compatibility list maintained in README. Most hooks are Fabric API, not vanilla-server-specific |

### Known Failure Modes & Mitigations

**Failure: Cold start problem**
*Players install the mod, play for 20 minutes, see nothing, uninstall.*
Mitigation:
- README prominently states "Echoes take time to accumulate. Give it a few play sessions."
- First-time player message (one-time, dismissable): *"Echoes is now recording. Significant moments in your world will leave ghostly traces for others to find."*
- Singleplayer players will see their own first echoes within one session (death, biome discovery) fairly quickly.

**Failure: Multiplayer server never installs it**
*Server admins don't install optional mods unless there's obvious value.*
Mitigation:
- Singleplayer experience must be compelling enough that players request it on their servers.
- Clear server-admin documentation showing zero performance impact.
- Modrinth page targets "SMP" keywords specifically.

**Failure: Ghosts look broken/janky**
*If the ghost rendering is poor, the emotional impact collapses.*
Mitigation:
- Don't ship until ghosts look right. This is non-negotiable. Delay MVP rather than ship bad visuals.
- Early alpha testing specifically for "does the ghost read as a ghost?" before anything else.

**Failure: Echo spam on competitive servers**
*Players abuse manual crystal recording to fill servers with noise.*
Mitigation:
- Manual crystal has durability (16 uses, expensive to craft)
- Per-player cap (50 echoes) prevents spam
- Admin command: `/echoes clear [player] [radius]`
- Config option to disable manual recording entirely

**Failure: Privacy concerns / harassment vector**
*Someone claims echoes reveal too much about player activity patterns.*
Mitigation:
- No live position data, ever. Echoes are historical.
- Player opt-out for recording (`/echoes optout visibility`)
- Admin `anonymize-all` config option
- Seen-state means echoes don't replay repeatedly — can't camp an echo to infer patterns

---

## 12. MVP Feature Scope (Week-by-Week)

### Week 1: Foundation
- [x] Mod skeleton setup (Fabric mod template, build.gradle, mixin setup)
- [x] `EchoRecord` and `EchoFrame` data structures
- [x] `EchoWorldState` PersistentState implementation
- [x] Basic NBT serialization/deserialization for echo data
- [x] Death event hook → records 5 seconds of frame data → saves to world state
- [x] Console logging to verify recording works

### Week 2: Basic Playback
- [x] `EchoPlaybackPacket` network packet (server→client)
- [x] `EchoSeenPacket` (client→server)
- [x] Client-side `GhostPlayerEntity` (smooth Catmull-Rom spline interpolation)
- [x] Proximity trigger scan (every 10 ticks, 16 block radius)
- [x] Seen-state tracking
- [x] **Milestone**: Die, walk back to death spot, see a ghost replay your death

### Week 3: Visual Polish
- [x] Custom `GhostPlayerRenderer` with translucency
- [x] Desaturation + blue-shift shader pass (`ghost_desaturate.fsh`)
- [x] Fade in / fade out animation + glass break audio cue
- [x] Tier-appropriate particle systems (vanilla particles with distance throttling)
- [x] Shadow and name tag suppression
- [x] **Milestone**: Ghost looks ethereal and distinct with pulsing glow

### Week 4: Full Echo Taxonomy
- [x] Structure discovery hook + per-player tracking
- [x] Biome discovery hook
- [x] Boss kill detection
- [x] Major craft milestones
- [x] Dimension transition hook
- [x] Tier assignment logic (Whispers, Marks, Scars, World Firsts)
- [x] **Milestone**: All 13 taxonomy echo types + World Firsts working

### Week 5: Echo Crystal + Config
- [x] Echo Crystal item + crafting recipe
- [x] Right-click sense function (particle burst on nearby echoes + metadata inspection)
- [x] Sneak+click manual recording (8s Tier 3 Scar echo)
- [x] `echoes.toml` config with documented fields
- [x] `/echoes optout` command
- [x] `/echoes clear` admin command
- [x] Decay system
- [x] Storage caps + eviction logic

### Week 6: Polish, Testing, Launch Prep
- [x] Singleplayer testing (full session, verify all echo types fire correctly)
- [x] Multiplayer testing (SMP network payload downsampling & privacy controls)
- [x] Performance profiling & distance culling (64-block culling, particle throttle)
- [x] Comprehensive unit test suite (`EchoesConfigTest`, `EchoFrameTest`, `EchoWorldStateTest`)
- [x] README and developer documentation fully aligned
- [x] Release build ready (Minecraft 26.1.1, Fabric Loader 0.18.6+, Java 25)

**All Tier 3 (Scar) echoes and World First features (originally scheduled for v1.1) are completed and fully integrated in this release.**

---

## 13. Post-MVP Roadmap

### v1.1 — Scar Echoes & World Firsts
- Tier 3 echo visual treatment (gold tint, trail particles)
- World First detection and persistent storage
- `/echoes worldfirsts` command — lists server's world-first records

### v1.2 — Echo Archive
- Craftable **Echo Archive** block — a journal block that stores echoes in a radius and displays them as a gallery
- Players can "pin" echoes to an Archive to prevent decay
- Right-clicking Archive shows list of stored echoes with names + event types

### v1.3 — Atmosphere Pass
- Ambient audio system: faint sounds near echo-dense areas (even before playback triggers)
- Optional: echo "imprints" on blocks (subtle texture overlay on the block where a Scar echo is anchored)
- Night/darkness visual variation — echoes glow slightly brighter at night

### v1.4 — Server Social Layer
- Shared "echo board" at server spawn — automatically displays recent Scar echoes and World Firsts
- Echo "reactions" — players can leave a small floating symbol near an echo (thumbs up, heart, skull) using Echo Crystals
- `/echoes near` — text command listing nearby unplayed echoes for accessibility

### v2.0 — Echo Lore System (Major Feature)
- Echoes accumulate "resonance" when viewed by many players
- High-resonance echoes slowly generate environmental changes — extra vines, moss, flowers — at their location
- The world physically responds to its most-remembered moments
- Deepens the original vision: the world remembers, and it shows

---

## 14. Differentiation & Competitive Positioning

### Closest Existing Mods

| Mod | Overlap | Key Difference |
|---|---|---|
| **Gravestones / Corail Tombstone** | Death location tracking | Static marker, loot mechanic — not experiential, not ghostly, no replay |
| **ReplayMod** | Player recording | Manual, full-session, for video creation — not ambient, not social, not multiplayer-passive |
| **Presence Footsteps** | Ambient immersion | Sound only, no visual, no recording, no social layer |
| **Waystones** | Location marking | Teleport utility mod, player-placed markers — completely different use case |
| **Carpet Mod** | Player action logging | Technical debug tool, not experiential, invisible to other players |
| **BedrockIfy / Bedrock parity mods** | Various QoL | Not related to world history or social layer |

### Search Keyword Positioning (Modrinth/CurseForge)

Primary: `ghost`, `echoes`, `world history`, `SMP`, `multiplayer immersion`, `death replay`
Secondary: `ambient`, `vanilla+`, `survival`, `social`, `memories`

### Modrinth Page Headline

> *"Your world remembers. Significant moments leave ghostly echoes — find where your friends died, where the dragon was first slain, where someone walked a thousand blocks alone. Echoes turns your world into a living history."*

---

## 15. Success Metrics

### 3-Month Targets (Post-Launch)
- 10,000 Modrinth downloads
- 500+ followers / mod page watchers
- At least one r/Minecraft or r/feedthebeast post reaching front page
- Included in at least one popular modpack or SMP server

### 6-Month Targets
- 50,000 downloads
- Active Discord community (500+ members)
- Content creator coverage (one YouTuber with 100k+ subscribers covers it organically)

### Health Signals (Qualitative)
- Players posting screenshots/clips of specific echo encounters
- Server admins recommending it to players as a default install
- Players reporting "I forgot it was a mod" (the highest compliment)
- Requests for features that extend the core concept (not requests to change it)

### Warning Signs
- Most feedback is "I never see any echoes" → cold start problem, needs tutorial improvement
- Most feedback is "too many echoes, world feels cluttered" → tighten trigger thresholds and caps
- Reports of performance issues → audit scan interval and packet frequency

---

## 16. Open Design Questions

These are unresolved design decisions that need answers before or during development:

**Q1: Do players see their own echoes by default?**
Lean yes. Returning to a site of your own death is a powerful experience. But it changes the emotional character — "encountering ghosts of others" vs "encountering your past self." Both are valid. Default on, configurable off.

**Q2: Should echoes be destroyable?**
Argument for: Griefing prevention, player agency, server control.
Argument against: Destroyable echoes adds a mechanic layer that contradicts the "ambient, not interactive" principle.
Current lean: No destruction in base mod. Admin `/echoes clear` command covers legitimate removal needs.

**Q3: How do we handle servers where nobody has the mod client-side?**
All playback is client-side, so players without the mod installed simply won't see ghosts. Recording still happens server-side. When they install the mod, they'll see all accumulated echoes. This is actually a good dynamic — it incentivizes installation.

**Q4: Echo frequency tuning — how rare is rare enough?**
This needs playtesting, not design-doc guessing. Instrument the system to log how many echoes are created per hour of playtime. Target: ~3-5 echoes per full 8-hour play session in a new world. Adjust triggers accordingly.

**Q5: Should manual echoes (Crystal) look different from automatic echoes?**
Probably yes — manual echoes could have a warmer tint (amber vs blue) to indicate intentionality. "Someone chose to leave this mark here." Differentiates the emotional register.

**Q6: Mod interoperability — what happens with other mods' dimensions?**
The dimension key system should handle modded dimensions automatically (echoes are stored per `RegistryKey<World>`). Test with at least Nether Depths Upgrade and Aether on the modpack compatibility list.

---

## Appendix A: File Structure

```
echoes/
├── src/main/java/com/vardanrattan/echoes/
│   ├── Echoes.java                          # Mod initializer
│   ├── command/
│   │   └── EchoesCommand.java               # /echoes commands (optout, clear, debug, worldfirsts)
│   ├── config/
│   │   └── EchoesConfig.java                # Standalone TOML configuration
│   ├── data/
│   │   ├── EchoAnimState.java               # Animation state enum
│   │   ├── EchoEventType.java               # Taxonomy event enum (13 types)
│   │   ├── EchoFrame.java                   # Relative position & rotation snapshot
│   │   ├── EchoRecord.java                  # Stored echo metadata + frames
│   │   ├── EchoTier.java                    # Intensity tier enum (WHISPER, MARK, SCAR, WORLD_FIRST)
│   │   ├── EchoWorldState.java              # SavedData world storage & spatial index
│   │   ├── EquipmentSnapshot.java           # Equipment NBT / Codec snapshot
│   │   ├── PlayerEchoData.java              # Per-player discovery & privacy tracking
│   │   └── VisitedStructure.java            # Structure discovery record
│   ├── entity/
│   │   └── GhostPlayerEntity.java           # Client-side ghost pose & spline controller
│   ├── events/
│   │   ├── BossKillEchoHandler.java         # Boss death & world first trigger
│   │   ├── BufferedFrame.java               # Raw rolling buffer frame
│   │   ├── DiscoveryEchoHandler.java        # Biome, structure & dimension triggers
│   │   ├── EchoesServerEvents.java          # Server event registry
│   │   ├── EchoService.java                 # Central echo factory & tier mapper
│   │   ├── FrameSampler.java                # Player state & limb swing capture
│   │   ├── JourneyEchoHandler.java          # Distance tracking & elytra flight trigger
│   │   ├── MilestoneEchoHandler.java        # Crafting, taming, sleeping & trading triggers
│   │   ├── PlaybackTriggerService.java      # Proximity scan & playback dispatcher
│   │   └── RecordingSessionManager.java     # Rolling frame buffer & death capture
│   ├── item/
│   │   ├── EchoCrystalItem.java             # Echo Crystal item implementation
│   │   └── EchoItems.java                   # Item registry
│   ├── mixin/
│   │   ├── CraftingResultSlotMixin.java     # Major craft detection
│   │   ├── MerchantResultSlotMixin.java     # Villager trade detection
│   │   ├── ServerPlayerSleepMixin.java      # First sleep detection
│   │   └── TamableAnimalMixin.java          # Animal taming detection
│   └── network/
│       ├── EchoNetworking.java              # Payload registration & server receivers
│       ├── EchoPlaybackPayload.java         # Server → Client playback payload
│       ├── EchoPrivacy.java                 # Anonymization & privacy utilities
│       ├── EchoSeenPayload.java             # Client → Server seen notification
│       └── EchoSensePayload.java            # Server → Client crystal sense payload
├── src/client/java/com/vardanrattan/echoes/
│   ├── EchoesClient.java                    # Client entrypoint
│   ├── mixin/client/
│   │   └── GhostSuppressMixin.java          # Ghost shadow radius suppression
│   ├── network/
│   │   └── EchoClientNetworking.java        # Client payload receivers & tick handlers
│   └── render/
│       └── GhostPlayerRenderer.java         # Translucent GLSL shader pipeline & particle FX
├── src/client/resources/
│   └── assets/echoes/shaders/core/
│       ├── ghost_desaturate.fsh             # Custom GLSL fragment shader
│       ├── ghost_desaturate.json            # Shader program definition
│       └── ghost_desaturate.vsh             # Vertex shader
├── src/main/resources/
│   ├── fabric.mod.json
│   ├── echoes.mixins.json
│   ├── echoes.client.mixins.json
│   ├── assets/echoes/
│   │   ├── lang/en_us.json
│   │   ├── models/item/echo_crystal.json
│   │   └── textures/item/echo_crystal.png
│   └── data/echoes/recipe/echo_crystal.json
└── build.gradle
```

---

## Appendix B: Inspirations & Reference Points

- **Outer Wilds** — the world tells you what happened through physical evidence, not text
- **Dark Souls bloodstains** — asynchronous ghost replays of other players' deaths; exact spiritual predecessor
- **Journey** — anonymous co-presence; other players felt as ambient phenomena
- **Minecraft Hermitcraft Season 8** — the emotional attachment players form to specific world locations
- **What Remains of Edith Finch** — environmental storytelling through past presence

The Dark Souls bloodstain mechanic is the closest existing implementation of this concept in any game. The key insight: it works because it's *passive* and *discovered*, not requested. You stumble on a ghost of someone dying to a boss and it feels like a warning, a shared experience, a moment of connection. That's what Echoes is bringing to Minecraft.

---

*Document Version: 1.0 | Last Updated: 2026 | Status: Implementation Complete & Fully Aligned*
