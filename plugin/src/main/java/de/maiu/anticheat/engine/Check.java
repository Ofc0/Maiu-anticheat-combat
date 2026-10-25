package de.maiu.anticheat.engine;

public enum Check {
    REACH("Reach"),
    WALLHIT("WallHit"),
    KILLAURA("KillAura"),
    AIMASSIST("AimAssist"),
    TRIGGERBOT("TriggerBot");

    private final String display;

    Check(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }
}
