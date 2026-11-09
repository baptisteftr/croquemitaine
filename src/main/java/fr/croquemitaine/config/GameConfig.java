package fr.croquemitaine.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Lecture + validation de config.yml en un objet immuable.
 * Toutes les valeurs réglables du jeu passent par ici (aucune constante de gameplay en dur).
 */
public final class GameConfig {
    public final int startLives;

    public final int mapSizeMin, mapSizeMax, playersMin, playersMax, centerX, centerZ, spawnAttempts;
    public final String world;

    public final boolean pvpEnforce;
    public final double defenseRadius;
    public final int killCreditSeconds;

    public final int roundInitialSeconds, roundDecrementSeconds, roundMinSeconds;
    public final int intermissionSeconds, announceSuspenseSeconds, powerChoiceSeconds, rouletteDelaySeconds;

    public enum PowerMode { CHOICE, RANDOM }

    public final PowerMode powerMode;
    public final boolean powerInherit;
    public final Map<String, Integer> powerWeights;
    public final int reprieveSeconds, shadowDuration, shadowCooldown, blindDuration, blindCooldown;
    public final double blindRadius, shadowStepRange;
    public final int trackerUpdateSeconds, biteSlownessSeconds, revealDuration;

    public final boolean eliminatedSpectate, eliminatedPrivateChat;
    public final int boxY, boxInnerSize, boxInnerHeight;

    public final int shrinkDelay, shrinkTarget, shrinkDuration;

    public final double mobEggChance;
    public final Set<String> mobEggExcluded;
    public final boolean tntSimplified, helmetBan, helmetPoison, phantoms, enchantingTableAtCenter, immediateRespawn, recoveryCompass;

    public final boolean muteEliminated;
    public final int autosaveSeconds;

    public static final List<String> POWER_IDS = List.of(
            "tracker", "shadow", "predator", "brute", "shell", "reprieve",
            "disguise", "second_wind", "blind", "shadow_step", "bite", "reveal");

    public GameConfig(FileConfiguration c, Logger log) {
        startLives = positive(c.getInt("lives.start", 5), 5, "lives.start", log);

        mapSizeMin = positive(c.getInt("map.size-min", 300), 300, "map.size-min", log);
        mapSizeMax = Math.max(mapSizeMin, c.getInt("map.size-max", 500));
        playersMin = positive(c.getInt("map.players-min", 5), 5, "map.players-min", log);
        playersMax = Math.max(playersMin, c.getInt("map.players-max", 25));
        centerX = c.getInt("map.center.x", 0);
        centerZ = c.getInt("map.center.z", 0);
        world = c.getString("map.world", "world");
        spawnAttempts = positive(c.getInt("map.spawn-attempts", 100), 100, "map.spawn-attempts", log);

        pvpEnforce = c.getBoolean("pvp.enforce", false);
        defenseRadius = c.getDouble("pvp.defense-radius", 40);
        killCreditSeconds = positive(c.getInt("pvp.kill-credit-seconds", 10), 10, "pvp.kill-credit-seconds", log);

        roundInitialSeconds = seconds(c, "rounds.initial", 20);
        roundDecrementSeconds = Math.max(0, secondsAllowZero(c, "rounds.decrement", 2));
        roundMinSeconds = seconds(c, "rounds.min", 6);
        intermissionSeconds = Math.max(0, c.getInt("rounds.intermission-seconds", 0));
        announceSuspenseSeconds = Math.max(1, c.getInt("rounds.announce-suspense-seconds", 5));
        powerChoiceSeconds = Math.max(1, c.getInt("rounds.power-choice-seconds", 15));
        rouletteDelaySeconds = Math.max(0, c.getInt("rounds.roulette-delay-seconds", 3));

        String mode = c.getString("powers.mode", "choice").toLowerCase(Locale.ROOT);
        if (!mode.equals("choice") && !mode.equals("random")) log.warning("powers.mode inconnu (" + mode + "), « choice » utilisé");
        powerMode = mode.equals("random") ? PowerMode.RANDOM : PowerMode.CHOICE;
        powerInherit = c.getBoolean("powers.inherit", true);
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (String id : POWER_IDS) weights.put(id, Math.max(0, c.getInt("powers." + id, id.equals("disguise") ? 0 : 10)));
        powerWeights = Map.copyOf(weights);
        reprieveSeconds = c.getInt("powers.settings.reprieve-minutes", 3) * 60;
        shadowDuration = c.getInt("powers.settings.shadow-duration-seconds", 20);
        shadowCooldown = c.getInt("powers.settings.shadow-cooldown-seconds", 120);
        blindRadius = c.getDouble("powers.settings.blind-radius", 8);
        blindDuration = c.getInt("powers.settings.blind-duration-seconds", 4);
        blindCooldown = c.getInt("powers.settings.blind-cooldown-seconds", 90);
        shadowStepRange = c.getDouble("powers.settings.shadow-step-range", 30);
        trackerUpdateSeconds = Math.max(1, c.getInt("powers.settings.tracker-update-seconds", 5));
        biteSlownessSeconds = c.getInt("powers.settings.bite-slowness-seconds", 3);
        revealDuration = c.getInt("powers.settings.reveal-duration-seconds", 10);

        String elimMode = c.getString("elimination.mode", "box").toLowerCase(Locale.ROOT);
        if (!elimMode.equals("spectator") && !elimMode.equals("box")) log.warning("elimination.mode inconnu (" + elimMode + "), « box » utilisé");
        eliminatedSpectate = elimMode.equals("spectator");
        eliminatedPrivateChat = c.getBoolean("elimination.private-chat", true);
        boxY = c.getInt("elimination-box.y", 300);
        boxInnerSize = Math.max(3, c.getInt("elimination-box.inner-size", 9));
        boxInnerHeight = Math.max(2, c.getInt("elimination-box.inner-height", 4));

        shrinkDelay = Math.max(0, c.getInt("endgame.shrink-delay-seconds", 60));
        shrinkTarget = Math.max(1, c.getInt("endgame.shrink-target-size", 30));
        shrinkDuration = Math.max(1, c.getInt("endgame.shrink-duration-seconds", 600));

        mobEggChance = Math.clamp(c.getDouble("rules.mob-egg-chance", 0.25), 0.0, 1.0);
        Set<String> excluded = new HashSet<>();
        for (String s : c.getStringList("rules.mob-egg-excluded")) excluded.add(s.toUpperCase(Locale.ROOT));
        mobEggExcluded = Set.copyOf(excluded);
        tntSimplified = c.getBoolean("rules.tnt-simplified", true);
        helmetBan = c.getBoolean("rules.helmet-ban", true);
        helmetPoison = !"block".equalsIgnoreCase(c.getString("rules.helmet-mode", "poison"));
        phantoms = c.getBoolean("rules.phantoms", false);
        enchantingTableAtCenter = c.getBoolean("rules.enchanting-table-at-center", true);
        immediateRespawn = c.getBoolean("rules.immediate-respawn", false);
        recoveryCompass = c.getBoolean("rules.recovery-compass", false);

        muteEliminated = c.getBoolean("voice.mute-eliminated", true);
        autosaveSeconds = Math.max(5, c.getInt("storage.autosave-seconds", 30));

        ConfigurationSection powers = c.getConfigurationSection("powers");
        if (powers != null) {
            for (String key : powers.getKeys(false)) {
                if (!Set.of("settings", "mode", "inherit").contains(key) && !POWER_IDS.contains(key)) {
                    log.warning("Pouvoir inconnu dans config.yml, ignoré : " + key);
                }
            }
        }
    }

    /** Les durées de manche sont en minutes, avec une surcharge en secondes pour les tests. */
    private static int seconds(FileConfiguration c, String base, int defMinutes) {
        return Math.max(1, secondsAllowZero(c, base, defMinutes));
    }

    private static int secondsAllowZero(FileConfiguration c, String base, int defMinutes) {
        int override = c.getInt(base + "-seconds-override", 0);
        if (override > 0) return override;
        return c.getInt(base + "-minutes", defMinutes) * 60;
    }

    private static int positive(int v, int def, String key, Logger log) {
        if (v > 0) return v;
        log.warning(key + " doit être > 0, valeur par défaut utilisée : " + def);
        return def;
    }
}
