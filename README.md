# MaiuCombatAC (v2.0)

BEFORE YOU SAY ANYTHING ITS IN BETA AND NOT FOR A SERVER ITS COULD BE HAVE FALSE FLAGGS IF YOU HAVE A BETTER AI YOU COULD RUN IT "/NOT FOR SERVERS THAT NEED A COMBAT ANTICHEAT\"

A high-performance, packet-level **Combat Anti-Cheat** with an **in-depth AuraBot decoy** and **asynchronous local AI verification (Ollama)** for Minecraft servers (**Spigot, Paper, Purpur, Folia**) across versions **1.8.9 to 1.21+**.

Inspired by [MLSAC](https://github.com/SoMax1soft/mls-network-plugin) [MLSAC-WEB](https://mlsac.net) and powered by [PacketEvents](https://github.com/retrooper/packetevents) & local AI inference (`qwen2.5:3b-instruct`).

---

## Key Features

### 1. Deterministic Combat Checks (Zero Ticks Delay)
- **True GCD & Sensitivity Lattice Analysis**: Uses Minecraft's mathematical rotation formula `(sens * 0.6 + 0.2)^3 * 8 * 0.15` to detect AimAssist smoothing and interpolation. Rapid mouse-wiggling by legit players is **never** flagged!
- **Reach Detection (3.41m Hard Limit)**: Precise 3D raycasting with lag-compensated bounding boxes. Blocks any hit exceeding realistic reach limits.
- **WallHit Occlusion**: 3-ray line-of-sight analysis that instantly cancels hits through solid blocks.
- **TriggerBot & Click Consistency**: Analyzes millisecond reaction delays upon crosshair intersection, click interval standard deviations, and unnatural CPS distributions.
- **KillAura Detection**: Detects snap angles, multi-target lock-ons, and blind-spot attacks.

---

### 2. Authentic Decoy / AuraBot (9 Client-Side AntiBot Bypasses)
When KillAura or AimAssist is suspected (or triggered via `/maiu bot`), an invisible packet-based FakePlayer is spawned in the suspect's blind spot. It is designed to pass modern hacked client AntiBot filters:
- ✅ **Ping Check**: Sent via `PlayerInfoUpdate` with realistic ping/latency values.
- ✅ **Name Check**: Uses valid, human-like player naming conventions.
- ✅ **Ticks Existed**: Spawns and stays persistent for 5–8 seconds.
- ✅ **Ground Check**: Positioned with proper `onGround=true` physics flags.
- ✅ **Sleep & Dead Check**: Normal entity state without sleep or death packet flags.
- ✅ **Swing & Animation Check**: Plays realistic arm swings and sneak animations.
- ✅ **Movement Check**: Relative position updates matching player velocity behind the target.
- ✅ **Sound Check**: Plays footstep sounds when moving.
- ✅ **Equipment**: Rendered with armor and main-hand weapons.

---

### 3. Local AI Second Opinion (`maiu-combat-ai`)
- **Zero Server Lag**: Runs 100% asynchronously via local Ollama REST API on your GPU (RTX / RX series).
- **No False Positives**: High-suspicion cases are evaluated by the AI arbiter to confirm whether a player is legitimately flicking or using combat hacks.
- **Fallback Safe**: Heuristic math checks handle enforcement seamlessly even if Ollama is offline.

---

## Commands & Permissions

| Command | Description | Permission |
|---|---|---|
| `/maiu status` | Checks connection to the local Ollama AI model and latency | `maiu.admin` |
| `/maiu alerts` | Toggles staff cheat notification alerts | `maiu.alerts` |
| `/maiu debug <player>` | Displays live actionbar telemetry (distance, angle, GCD error, CPS, VL) | `maiu.admin` |
| `/maiu inspect <player>` | Inspects player violation levels and the latest AI arbiter decision | `maiu.admin` |
| `/maiu bot <player> [sec]` | Spawns the Decoy Bot in the player's blind spot for `x` seconds | `maiu.admin` |
| `/maiu bot <player> [sec] front` | Spawns the Decoy Bot in front of the player (for visual testing) | `maiu.admin` |
| `/maiu bot stop <player>` | Removes any active decoy bot immediately | `maiu.admin` |
| `/maiu reset <player>` | Clears violations, combat states, and active bot traps | `maiu.admin` |
| `/maiu reload` | Reloads `config.yml` configuration | `maiu.admin` |

---

## Quick Setup

### 1. Setup the AI Model (Optional, Recommended)
1. Install [Ollama](https://ollama.com/).
2. Run `ollama run qwen2.5:3b-instruct`.
3. In the repository's `ai/` directory, run:
   ```bash
   ollama create maiu-combat-ai -f Modelfile
