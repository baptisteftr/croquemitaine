package fr.croquemitaine.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** État persistant d'un joueur inscrit dans la partie. */
public final class PlayerRecord {
    private UUID uuid;
    private String name;
    private int lives;
    private List<DeathRecord> deaths = new ArrayList<>();

    /** Requis par la désérialisation JSON. */
    @SuppressWarnings("unused")
    private PlayerRecord() {
    }

    public PlayerRecord(UUID uuid, String name, int lives) {
        this.uuid = uuid;
        this.name = name;
        this.lives = lives;
    }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public void name(String name) { this.name = name; }
    public int lives() { return lives; }

    /** Borne les vies à 0 minimum ; retourne l'ancienne valeur. */
    public int setLives(int newLives) {
        int old = lives;
        lives = Math.max(0, newLives);
        return old;
    }

    public Category category() { return Category.fromLives(lives); }
    public boolean isAlive() { return lives > 0; }

    public List<DeathRecord> deaths() {
        if (deaths == null) deaths = new ArrayList<>();
        return deaths;
    }
}
