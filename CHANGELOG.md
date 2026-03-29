## Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- Project scaffolding and initial Fabric 1.21.x example mod setup.
- `ECHOES_MOD_DEVDOC.md` high‑level design document.
- `ECHOES_BUILD_ROADMAP.md` implementation roadmap for a production‑grade, future‑proof Echoes mod.

---

## [0.1.0] – In development

### Added
- **Core Engine**: `EchoRecord`, `EchoFrame`, `EchoWorldState` with NBT persistence and spatial indexing.
- **Render System**: `GhostPlayerRenderer` using translucency, `AlphaVertexConsumer`, and sub-tick interpolation.
- **Audio System**: Tier-based spatial sound effects for echo playback.
- **Event Taxonomy**: Triggers for Death, Discovery (Biome, Structure, Dimension), Boss Kills, Taming, Major Crafting, and Journeys.
- **Echo Crystal**: Manual recording, sense pulses, and metadata tooltips.
- **Privacy**: `EchoPrivacy` utility, player opt-out commands, and anonymization support.
- **Recording**: Asynchronous frame capture and safe-position anchoring for void deaths.

### Fixed
- RESTORED compilation by removing dead `AgentDebugLogger`.
- Fixed particle type mismatches across all tiers.
- Removed legacy `ExampleMixin` and `ExampleClientMixin`.

