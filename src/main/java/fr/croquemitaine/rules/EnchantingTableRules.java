package fr.croquemitaine.rules;

import fr.croquemitaine.game.GameContext;
import fr.croquemitaine.players.RespawnService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;

/** Table d'enchantement non craftable ; une unique table, incassable, au centre de la carte. */
public final class EnchantingTableRules implements Listener {
    private static final NamespacedKey VANILLA_RECIPE = NamespacedKey.minecraft("enchanting_table");
    private final GameContext ctx;

    public EnchantingTableRules(GameContext ctx) {
        this.ctx = ctx;
    }

    public void removeRecipe() {
        Bukkit.removeRecipe(VANILLA_RECIPE);
    }

    /** Pose la table au sol, au centre (à côté si la colonne du centre est bloquée). */
    public void place() {
        if (!ctx.config().enchantingTableAtCenter) return;
        World w = ctx.world();
        int x = ctx.state().centerX(), z = ctx.state().centerZ();
        Block ground = RespawnService.surface(w, x, z);
        // Une table déjà là (partie précédente) est réutilisée : sinon elles s'empileraient à chaque start.
        Block table = ground.getType() == Material.ENCHANTING_TABLE ? ground : ground.getRelative(0, 1, 0);
        table.setType(Material.ENCHANTING_TABLE);
        ctx.state().enchantingTable(new int[]{table.getX(), table.getY(), table.getZ()});
    }

    /** Fin de partie : la table disparaît avec la partie. */
    public void remove() {
        int[] p = ctx.state().enchantingTable();
        if (p == null) return;
        Block b = ctx.world().getBlockAt(p[0], p[1], p[2]);
        if (b.getType() == Material.ENCHANTING_TABLE) b.setType(Material.AIR);
        ctx.state().enchantingTable(null);
    }

    private boolean isTable(Block b) {
        int[] p = ctx.state().enchantingTable();
        return p != null && b.getX() == p[0] && b.getY() == p[1] && b.getZ() == p[2]
                && b.getWorld().getName().equals(ctx.state().world());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (isTable(e.getBlock())) {
            e.setCancelled(true);
            ctx.messages().send(e.getPlayer(), "rules.table-protected");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::isTable);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::isTable);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (anyTable(e.getBlocks())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (anyTable(e.getBlocks())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        if (isTable(e.getBlock())) e.setCancelled(true); // endermen, wither…
    }

    private boolean anyTable(List<Block> blocks) {
        for (Block b : blocks) if (isTable(b)) return true;
        return false;
    }
}
