package fr.croquemitaine.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mémorise le dernier joueur ayant infligé un dégât AUTORISÉ à chaque victime.
 * Nécessaire car le combat tracker vanilla ignore nos attributions indirectes
 * (mobs d'œuf, TNT…) et ne connaît pas la matrice PvP. Pas d'import Bukkit : testable.
 */
public final class KillTracker {
    private record Hit(UUID attacker, long at) {
    }

    private final Map<UUID, Hit> lastHits = new HashMap<>();
    private long windowMillis;

    public KillTracker(int windowSeconds) {
        this.windowMillis = windowSeconds * 1000L;
    }

    public void setWindowSeconds(int seconds) {
        this.windowMillis = seconds * 1000L;
    }

    public void record(UUID attacker, UUID victim, long nowMillis) {
        if (attacker.equals(victim)) return;
        lastHits.put(victim, new Hit(attacker, nowMillis));
    }

    /** Tueur attribué si le dernier coup date de moins de la fenêtre configurée. */
    public UUID killer(UUID victim, long nowMillis) {
        Hit h = lastHits.get(victim);
        if (h == null || nowMillis - h.at() > windowMillis) return null;
        return h.attacker();
    }

    public void clear(UUID victim) {
        lastHits.remove(victim);
    }

    public void clearAll() {
        lastHits.clear();
    }
}
