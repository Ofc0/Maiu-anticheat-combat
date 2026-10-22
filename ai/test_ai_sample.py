import json
import urllib.request
import urllib.error
import time

OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL_NAME = "maiu-combat-ai"

# Sample 1: Completely legit player
LEGIT_SAMPLE = {
    "player": "LegitGamer123",
    "uuid": "456e4567-e89b-12d3-a456-426614174000",
    "observed_metrics": {
        "combat_ticks": 35,
        "attacks_count": 8,
        "max_reach_blocks": 2.84,
        "avg_reach_blocks": 2.52,
        "wall_hits_detected": 0,
        "max_delta_yaw_deg": 14.2,
        "max_delta_pitch_deg": 6.8,
        "avg_angle_offset_deg": 4.12,
        "angle_offset_variance": 1.84,
        "invalid_gcd_percent": 8.5,
        "distinct_targets": 1,
        "rapid_target_switches": 0,
        "average_cps": 7.4,
        "min_attack_interval_ms": 110.0
    },
    "cheat_rules_reference": {
        "reach_rule": "Minecraft max survival hit reach is 3.00 blocks. 3.01 - 3.20 can be ping compensation; >3.30 is definitive Reach cheat.",
        "wall_hit_rule": "Wall hits occur when raycast to target intersects solid occluding blocks. Any wall_hits > 0 indicates WallHit / Phasing cheat.",
        "gcd_rule": "Minecraft mouse sensitivity dictates rotation step GCD = (sens*0.6+0.2)^3 * 8 * 0.15. If invalid_gcd_percent > 65%, player uses synthetic aimbot smoothing or non-mouse rotational injection.",
        "aim_assist_rule": "Angle offset variance < 0.20 with high attacks indicates magnetic crosshair snapping (AimAssist).",
        "triggerbot_rule": "Minimum attack interval < 80ms upon crosshair intersection indicates superhuman reaction speed (TriggerBot).",
        "killaura_rule": "Rapid target switches >= 2 across distinct targets in < 150ms indicates Multi-Aura or KillAura switch."
    }
}

# Sample 2: Blatant KillAura & Reach cheater
KILLAURA_SAMPLE = {
    "player": "HackLord9000",
    "uuid": "789e4567-e89b-12d3-a456-426614174999",
    "observed_metrics": {
        "combat_ticks": 40,
        "attacks_count": 14,
        "max_reach_blocks": 3.78,
        "avg_reach_blocks": 3.65,
        "wall_hits_detected": 0,
        "max_delta_yaw_deg": 64.5,
        "max_delta_pitch_deg": 28.1,
        "avg_angle_offset_deg": 1.05,
        "angle_offset_variance": 0.08,
        "invalid_gcd_percent": 91.2,
        "distinct_targets": 3,
        "rapid_target_switches": 4,
        "average_cps": 15.2,
        "min_attack_interval_ms": 45.0
    },
    "cheat_rules_reference": {
        "reach_rule": "Minecraft max survival hit reach is 3.00 blocks. 3.01 - 3.20 can be ping compensation; >3.30 is definitive Reach cheat.",
        "wall_hit_rule": "Wall hits occur when raycast to target intersects solid occluding blocks. Any wall_hits > 0 indicates WallHit / Phasing cheat.",
        "gcd_rule": "Minecraft mouse sensitivity dictates rotation step GCD = (sens*0.6+0.2)^3 * 8 * 0.15. If invalid_gcd_percent > 65%, player uses synthetic aimbot smoothing or non-mouse rotational injection.",
        "aim_assist_rule": "Angle offset variance < 0.20 with high attacks indicates magnetic crosshair snapping (AimAssist).",
        "triggerbot_rule": "Minimum attack interval < 80ms upon crosshair intersection indicates superhuman reaction speed (TriggerBot).",
        "killaura_rule": "Rapid target switches >= 2 across distinct targets in < 150ms indicates Multi-Aura or KillAura switch."
    }
}

def query_ai(sample_data):
    req_body = {
        "model": MODEL_NAME,
        "prompt": json.dumps(sample_data),
        "stream": False,
        "format": "json"
    }

    req = urllib.request.Request(
        OLLAMA_URL,
        data=json.dumps(req_body).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )

    start = time.time()
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            elapsed = (time.time() - start) * 1000
            res = json.loads(resp.read().decode("utf-8"))
            parsed_inner = json.loads(res.get("response", "{}"))
            return parsed_inner, elapsed
    except urllib.error.URLError as e:
        return {"error": f"Ollama nicht erreichbar: {e}"}, 0

if __name__ == "__main__":
    print("=" * 60)
    print(" MAIU AI COMBAT CLASSIFIER - LOCAL GPU VERIFICATION TEST")
    print("=" * 60)

    print("\n[TEST 1] Sende legitime Spieler-Daten an KI...")
    result, ping = query_ai(LEGIT_SAMPLE)
    print(f"Antwortzeit: {ping:.1f}ms")
    print(json.dumps(result, indent=2, ensure_ascii=False))

    print("\n[TEST 2] Sende KillAura/Reach Cheater-Daten an KI...")
    result2, ping2 = query_ai(KILLAURA_SAMPLE)
    print(f"Antwortzeit: {ping2:.1f}ms")
    print(json.dumps(result2, indent=2, ensure_ascii=False))
