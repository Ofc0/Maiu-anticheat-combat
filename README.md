# 🛡️ MaiuCombatAC (v2.0)

A high-performance, packet-level **Combat Anti-Cheat** featuring an **in-depth AuraBot decoy** and **asynchronous local AI verification (Ollama)** for Minecraft servers (**Spigot, Paper, Purpur, Folia**) across versions **1.8.9 to 1.21+**.

Inspired by [MLSAC](https://github.com/SoMax1soft/mls-network-plugin) and powered by [PacketEvents](https://github.com/retrooper/packetevents) & local neural network inference (`qwen2.5:3b-instruct`).

---

## ✨ Key Features

### 1. ⚔️ Deterministic Combat Checks (Zero Ticks Delay)
- **True GCD & Mouse Lattice Analysis**: Uses Minecraft's mathematical rotation formula `(sens * 0.6 + 0.2)^3 * 8 * 0.15` to detect AimAssist smoothing and interpolation. Rapid mouse-wiggling by legit players is **never** flagged!
- **Rotational Jerk & Aim Curvature**: Analyzes the mathematical derivative of angular acceleration. Human hands accelerate and decelerate in smooth curves, whereas software aimbots exhibit unnatural direction flips and micro-correction spikes.
- **Reach Detection (3.41m Hard Limit)**: Precise 3D raycasting with historical, two-sided lag-compensated bounding boxes. Blocks hits exceeding realistic reach limits.
- **WallHit Occlusion**: Ping-compensated line-of-sight analysis that checks historical target bounding boxes. Hits through solid blocks are immediately prevented, while stairs, slabs, and fences are handled with zero false flags.
- **TriggerBot & Click Consistency**: Identifies click intervals with unnaturally low standard deviation (`< 22ms`), instant re-acquire clicks on crosshair re-entry, and extreme CPS spikes.

---

### 2. 🤖 Authentic Decoy / AuraBot (9 Client-Side AntiBot Bypasses)
When KillAura or AimAssist is suspected (or triggered via `/maiu bot`), an invisible packet-based FakePlayer is spawned directly above/behind the player's head. It dynamically stays closer than the attacked victim (forcing aimbots to lock onto it) and bypasses modern cheat client AntiBot checks:
- ✅ **Ping Check**: Sent via `PlayerInfoUpdate` with realistic ping/latency values.
- ✅ **Name Check**: Uses valid, human-like player naming conventions.
- ✅ **Ticks Existed**: Spawns and stays persistent for 5–8 seconds.
- ✅ **Ground Check**: Positioned with proper physics flags.
- ✅ **Sleep & Dead Check**: Normal entity state without sleep or death packet flags.
- ✅ **Swing & Animation Check**: Plays realistic arm swings and sneak animations.
- ✅ **Movement Check**: Relative position updates matching player velocity behind the target.
- ✅ **Sound Check**: Plays footstep and weapon sweep sounds.
- ✅ **Equipment**: Rendered with armor and main-hand weapons.

---

### 3. 🧠 Local AI Second Opinion (`maiu-combat-ai`)
- **Zero Server Lag**: Runs 100% asynchronously via local Ollama REST API on your GPU (NVIDIA RTX / AMD RX series).
- **Qwen 2.5 3B-Instruct**: Evaluates multi-dimensional telemetry windows (mouse lattice, tracking lock, click intervals, missed-swing ratios).
- **Continuous Learning Pipeline**: Live combat features are logged to `plugins/MaiuCombatAC/ai_dataset/combat_telemetry.jsonl` to build training datasets for fine-tuning.

---

## 🎮 Commands & Permissions

| Command | Description | Permission |
|---|---|---|
| `/maiu status` | Checks connection to the local Ollama AI model and latency | `maiu.admin` |
| `/maiu alerts` | Toggles staff cheat notification alerts | `maiu.alerts` |
| `/maiu inspect <player>` | Displays detailed combat telemetry, violation levels, and AI verdict | `maiu.admin` |
| `/maiu debug <player>` | Toggles live second-by-second actionbar HUD | `maiu.admin` |
| `/maiu bot <player> [sec]` | Spawns the Decoy Bot in the player's blind spot for `x` seconds | `maiu.admin` |
| `/maiu bot <player> [sec] front` | Spawns the Decoy Bot in front of the player (for visual testing) | `maiu.admin` |
| `/maiu bot stop <player>` | Removes any active decoy bot immediately | `maiu.admin` |
| `/maiu reset <player>` | Clears violations, combat states, and active bot traps | `maiu.admin` |
| `/maiu reload` | Reloads `config.yml` configuration | `maiu.admin` |

---

## 🚀 Quick Setup

### The All-in-One Launcher:
Double-click **`run_all.bat`** in the root directory. It automatically:
1. Verifies and starts the Ollama neural service in the background.
2. Checks that the `maiu-combat-ai` model is loaded in your GPU VRAM.
3. Compiles the latest plugin code with Gradle Shadow.
4. Deploys the built JAR directly to your server and launches Minecraft Purpur 1.20.4!

---

## 📜 Credits & License
- **PacketEvents** by [retrooper](https://github.com/retrooper/packetevents)
- Inspired by **MLSAC** by [SoMax1soft](https://github.com/SoMax1soft/mls-network-plugin)
