package fr.croquemitaine.players;

import fr.croquemitaine.game.GameContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/** Positions d'apparition aléatoires sûres + boussole de récupération. */
public final class RespawnService {
    private final GameContext ctx;

    public RespawnService(GameContext ctx) {
        this.ctx = ctx;
    }

    public Location randomSafeLocation() {
        World w = ctx.world();
        WorldBorder border = w.getWorldBorder();
        double half = border.getSize() / 2.0 - 2;
        Location c = border.getCenter();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < ctx.config().spawnAttempts; i++) {
            int x = (int) Math.floor(c.getX() + rnd.nextDouble(-half, half));
            int z = (int) Math.floor(c.getZ() + rnd.nextDouble(-half, half));
            Location l = safeAt(w, x, z);
            if (l != null) return l;
        }
        // Repli : centre de la carte, au plus haut bloc. Fréquent si la carte est surtout de l'eau :
        // on prévient l'admin pour qu'il choisisse un autre centre (map.center).
        ctx.plugin().getLogger().warning("Aucune position d'apparition sûre trouvée en " + ctx.config().spawnAttempts
                + " essais (carte trop aquatique ?) : repli au centre.");
        int cx = (int) Math.floor(c.getX()), cz = (int) Math.floor(c.getZ());
        return new Location(w, cx + 0.5, surface(w, cx, cz).getY() + 1, cz + 0.5);
    }

    /**
     * Bloc de surface en ignorant les barrières : la boîte d'élimination est au-dessus
     * du centre et fausserait sinon le « plus haut bloc ».
     */
    public static Block surface(World w, int x, int z) {
        Block b = w.getHighestBlockAt(x, z);
        while (b.getY() > w.getMinHeight() && (b.getType() == Material.BARRIER || b.getType().isAir())) {
            b = b.getRelative(0, -1, 0);
        }
        return b;
    }

    /** Bloc solide, ni liquide ni feuillage, avec 2 blocs d'air au-dessus. */
    private Location safeAt(World w, int x, int z) {
        Block ground = surface(w, x, z);
        Material m = ground.getType();
        if (!m.isSolid() || ground.isLiquid() || Tag.LEAVES.isTagged(m)) return null;
        if (m == Material.MAGMA_BLOCK || m == Material.CACTUS || m == Material.POWDER_SNOW || m == Material.BARRIER) return null;
        Block a1 = ground.getRelative(0, 1, 0), a2 = ground.getRelative(0, 2, 0);
        if (!a1.getType().isAir() || !a2.getType().isAir()) return null;
        if (!w.getWorldBorder().isInside(a1.getLocation())) return null;
        return new Location(w, x + 0.5, ground.getY() + 1, z + 0.5, ThreadLocalRandom.current().nextFloat() * 360f, 0f);
    }

    /** La recovery compass vanilla pointe déjà vers le dernier lieu de mort : on la donne une seule fois. */
    public void giveRecoveryCompass(Player p) {
        if (p.getInventory().contains(Material.RECOVERY_COMPASS)) return;
        p.getInventory().addItem(new ItemStack(Material.RECOVERY_COMPASS));
    }
}
