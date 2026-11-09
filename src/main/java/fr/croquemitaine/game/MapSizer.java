package fr.croquemitaine.game;

import fr.croquemitaine.config.GameConfig;
import fr.croquemitaine.core.MapSizing;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/** Applique la world border selon le nombre de joueurs inscrits. */
public final class MapSizer {
    private final GameContext ctx;

    public MapSizer(GameContext ctx) {
        this.ctx = ctx;
    }

    public int apply(World w, int players) {
        GameConfig c = ctx.config();
        int size = MapSizing.size(players, c.playersMin, c.playersMax, c.mapSizeMin, c.mapSizeMax);
        WorldBorder b = w.getWorldBorder();
        b.setCenter(c.centerX + 0.5, c.centerZ + 0.5);
        b.setSize(size);
        return size;
    }
}
