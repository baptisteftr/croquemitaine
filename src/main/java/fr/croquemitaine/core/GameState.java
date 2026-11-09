package fr.croquemitaine.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * État global de la partie, sérialisé tel quel dans game.json.
 * Volontairement « bête » : ce sont GameManager et les services qui portent la logique.
 */
public final class GameState {
    private Phase phase = Phase.LOBBY;
    private int round;
    /** Secondes restantes avant la fin de la manche en cours. */
    private int roundRemaining;
    /** Durée totale de la manche en cours (pour la progression de la bossbar). */
    private int roundTotal;
    /** Secondes d'entracte restantes avant la prochaine manche (0 = aucune). */
    private int intermissionRemaining;

    /** Croque de la manche ; null si aucun ou si sa mission est terminée. */
    private UUID croque;
    /** Nouveau pouvoir obtenu par le Croque actuel (null tant que la roulette n'est pas résolue). */
    private String croquePower;
    /** Pouvoirs acquis par les Croques successifs de la partie (héritage cumulatif). */
    private List<String> acquiredPowers = new ArrayList<>();
    private boolean secondWindUsed;

    private boolean endgameShrinking;
    private int shrinkDelayRemaining = -1;

    private String world;
    private int centerX;
    private int centerZ;
    private int borderSize;
    /** Position de la table d'enchantement protégée (null si non posée). */
    private int[] enchantingTable;

    private Map<UUID, PlayerRecord> players = new LinkedHashMap<>();

    public Phase phase() { return phase; }
    public void phase(Phase p) { phase = p; }
    public int round() { return round; }
    public void round(int r) { round = r; }
    public int roundRemaining() { return roundRemaining; }
    public void roundRemaining(int s) { roundRemaining = s; }
    public int roundTotal() { return roundTotal; }
    public void roundTotal(int s) { roundTotal = s; }
    public int intermissionRemaining() { return intermissionRemaining; }
    public void intermissionRemaining(int s) { intermissionRemaining = s; }

    public UUID croque() { return croque; }
    public void croque(UUID u) { croque = u; }
    public String croquePower() { return croquePower; }
    public void croquePower(String id) { croquePower = id; }
    public List<String> acquiredPowers() {
        if (acquiredPowers == null) acquiredPowers = new ArrayList<>();
        return acquiredPowers;
    }
    public boolean secondWindUsed() { return secondWindUsed; }
    public void secondWindUsed(boolean b) { secondWindUsed = b; }
    public boolean isCroque(UUID u) { return croque != null && croque.equals(u); }

    public boolean endgameShrinking() { return endgameShrinking; }
    public void endgameShrinking(boolean b) { endgameShrinking = b; }
    public int shrinkDelayRemaining() { return shrinkDelayRemaining; }
    public void shrinkDelayRemaining(int s) { shrinkDelayRemaining = s; }

    public String world() { return world; }
    public void world(String w) { world = w; }
    public int centerX() { return centerX; }
    public int centerZ() { return centerZ; }
    public void center(int x, int z) { centerX = x; centerZ = z; }
    public int borderSize() { return borderSize; }
    public void borderSize(int s) { borderSize = s; }
    public int[] enchantingTable() { return enchantingTable; }
    public void enchantingTable(int[] pos) { enchantingTable = pos; }

    public Map<UUID, PlayerRecord> players() { return players; }
    public Collection<PlayerRecord> records() { return players.values(); }
    public PlayerRecord record(UUID u) { return players.get(u); }

    public boolean isActive() { return phase == Phase.RUNNING || phase == Phase.PAUSED; }

    public Category category(UUID u) {
        PlayerRecord r = players.get(u);
        return r == null ? null : r.category();
    }

    /** Remet l'état à zéro pour une nouvelle partie (les joueurs sont réinscrits par l'appelant). */
    public void reset() {
        phase = Phase.LOBBY;
        round = 0;
        roundRemaining = 0;
        roundTotal = 0;
        intermissionRemaining = 0;
        croque = null;
        croquePower = null;
        acquiredPowers = new ArrayList<>();
        secondWindUsed = false;
        endgameShrinking = false;
        shrinkDelayRemaining = -1;
        enchantingTable = null;
        players.clear();
    }
}
