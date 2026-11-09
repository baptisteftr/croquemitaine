package fr.croquemitaine.players;

import fr.croquemitaine.config.GameConfig;
import fr.croquemitaine.game.GameContext;
import fr.croquemitaine.core.PlayerRecord;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sort des éliminés. Deux modes (config elimination.mode) :
 * - spectator : mode spectateur vanilla, dont le menu (touche 1) permet de se téléporter aux joueurs ;
 * - box : boîte en barrières au-dessus de la carte.
 */
public final class EliminationBox {
    private final GameContext ctx;

    public EliminationBox(GameContext ctx) {
        this.ctx = ctx;
    }

    /** Y du sol de la boîte, borné pour que la boîte tienne sous la hauteur max du monde. */
    private int floorY(World w) {
        GameConfig c = ctx.config();
        int maxFloor = w.getMaxHeight() - c.boxInnerHeight - 2;
        return Math.min(c.boxY, maxFloor);
    }

    public void build() {
        World w = ctx.world();
        GameConfig c = ctx.config();
        int half = c.boxInnerSize / 2 + 1;
        int cx = ctx.state().centerX(), cz = ctx.state().centerZ();
        int y0 = floorY(w), y1 = y0 + c.boxInnerHeight + 1;
        for (int x = cx - half; x <= cx + half; x++) {
            for (int z = cz - half; z <= cz + half; z++) {
                for (int y = y0; y <= y1; y++) {
                    boolean shell = x == cx - half || x == cx + half || z == cz - half || z == cz + half || y == y0 || y == y1;
                    w.getBlockAt(x, y, z).setType(shell ? Material.BARRIER : Material.AIR, false);
                }
            }
        }
    }

    public Location inside() {
        World w = ctx.world();
        return new Location(w, ctx.state().centerX() + 0.5, floorY(w) + 1, ctx.state().centerZ() + 0.5);
    }

    /**
     * Vrai si la position est au niveau de la boîte ou au-dessus. Le point de spawn du monde est
     * souvent sous la boîte : le spawn vanilla (bloc le plus haut) poserait alors le joueur sur son toit.
     */
    public boolean isAtBoxLevel(Location l) {
        return l.getWorld() == ctx.world() && l.getY() >= floorY(l.getWorld()) - 1;
    }

    /** Sol réel sous une position, en ignorant la boîte (barrières). */
    public Location groundBelow(Location l) {
        World w = l.getWorld();
        int x = l.getBlockX(), z = l.getBlockZ();
        return new Location(w, x + 0.5, RespawnService.surface(w, x, z).getY() + 1, z + 0.5, l.getYaw(), 0f);
    }

    /** Point de départ d'un spectateur : sur un joueur vivant au hasard, sinon au centre. */
    public Location spectatorSpot() {
        List<Player> alive = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            PlayerRecord r = ctx.state().record(o.getUniqueId());
            if (r != null && r.isAlive() && o.getGameMode() != GameMode.SPECTATOR) alive.add(o);
        }
        if (!alive.isEmpty()) return alive.get(ThreadLocalRandom.current().nextInt(alive.size())).getLocation().add(0, 2, 0);
        World w = ctx.world();
        int x = ctx.state().centerX(), z = ctx.state().centerZ();
        return new Location(w, x + 0.5, RespawnService.surface(w, x, z).getY() + 10, z + 0.5);
    }

    /** Où réapparaît un éliminé selon le mode. */
    public Location eliminatedRespawn() {
        return ctx.config().eliminatedSpectate ? spectatorSpot() : inside();
    }

    /** Applique le sort d'éliminé (spectateur ou boîte). */
    public void imprison(Player p) {
        p.setFoodLevel(20);
        p.setFireTicks(0);
        if (ctx.config().eliminatedSpectate) {
            if (p.getGameMode() != GameMode.SPECTATOR) {
                p.setGameMode(GameMode.SPECTATOR);
                p.teleport(spectatorSpot());
            }
            return;
        }
        p.setGameMode(GameMode.ADVENTURE);
        p.setInvulnerable(true);
        p.setFoodLevel(20);
        p.setFireTicks(0);
        p.teleport(inside());
    }

    /** Libère un joueur ressuscité. */
    public void release(Player p) {
        p.setInvulnerable(false);
        p.setGameMode(GameMode.SURVIVAL);
    }
}
