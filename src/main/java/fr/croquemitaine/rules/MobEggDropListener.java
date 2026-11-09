package fr.croquemitaine.rules;

import fr.croquemitaine.game.GameContext;
import fr.croquemitaine.game.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Œufs de mobs (§1.10 / §6.8) : drop aléatoire sur mob hostile tué par un joueur,
 * et marquage du propriétaire sur les mobs issus d'un œuf utilisé par un joueur.
 */
public final class MobEggDropListener implements Listener {
    private record PendingEgg(UUID player, Location at, int tick) {
    }

    private final GameContext ctx;
    private final Keys keys;
    private final Map<UUID, PendingEgg> pending = new HashMap<>();
    private List<EntityType> eggPool;

    public MobEggDropListener(GameContext ctx, Keys keys) {
        this.ctx = ctx;
        this.keys = keys;
    }

    /** Tous les types ayant un œuf, moins les exclus de la config (calcul paresseux). */
    private List<EntityType> pool() {
        if (eggPool == null) {
            List<EntityType> list = new ArrayList<>();
            for (EntityType t : Registry.ENTITY_TYPE) {
                if (!t.isAlive() || !t.isSpawnable()) continue;
                if (ctx.config().mobEggExcluded.contains(t.name())) continue;
                if (t == EntityType.PHANTOM && !ctx.config().phantoms) continue;
                Material egg = Bukkit.getItemFactory().getSpawnEgg(t);
                if (egg != null) list.add(t);
            }
            eggPool = list;
        }
        return eggPool;
    }

    public void invalidatePool() {
        eggPool = null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        if (!ctx.state().isActive()) return;
        LivingEntity dead = e.getEntity();
        if (!(dead instanceof Enemy) || dead.getKiller() == null) return;
        if (ctx.config().mobEggExcluded.contains(dead.getType().name())) return;
        if (ThreadLocalRandom.current().nextDouble() >= ctx.config().mobEggChance) return;
        List<EntityType> candidates = new ArrayList<>(pool());
        candidates.remove(dead.getType()); // l'œuf d'un AUTRE mob
        if (candidates.isEmpty()) return;
        EntityType t = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        e.getDrops().add(new ItemStack(Bukkit.getItemFactory().getSpawnEgg(t)));
    }

    // L'API n'expose pas l'utilisateur d'un œuf dans CreatureSpawnEvent : on mémorise
    // l'interaction du joueur et on l'associe au spawn SPAWNER_EGG qui suit (même tick, à proximité).

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUseEgg(PlayerInteractEvent e) {
        ItemStack it = e.getItem();
        if (it != null && isEgg(it.getType())) remember(e.getPlayer().getUniqueId(), e.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUseEggOnEntity(PlayerInteractEntityEvent e) {
        ItemStack it = e.getPlayer().getInventory().getItem(e.getHand());
        if (isEgg(it.getType())) remember(e.getPlayer().getUniqueId(), e.getPlayer().getLocation());
    }

    private static boolean isEgg(Material m) {
        return m.name().endsWith("_SPAWN_EGG");
    }

    private void remember(UUID player, Location at) {
        pending.put(player, new PendingEgg(player, at, Bukkit.getCurrentTick()));
    }

    /** Phantoms désactivés : la gamerule couvre l'insomnie, ceci bloque toutes les autres sources. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhantom(CreatureSpawnEvent e) {
        if (e.getEntityType() == EntityType.PHANTOM && !ctx.config().phantoms && ctx.state().isActive()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) return;
        int now = Bukkit.getCurrentTick();
        pending.values().removeIf(p -> now - p.tick() > 1);
        PendingEgg best = null;
        double bestDist = 12 * 12;
        for (PendingEgg p : pending.values()) {
            if (p.at().getWorld() != e.getLocation().getWorld()) continue;
            double d = p.at().distanceSquared(e.getLocation());
            if (d <= bestDist) {
                bestDist = d;
                best = p;
            }
        }
        if (best == null) return;
        e.getEntity().getPersistentDataContainer().set(keys.eggOwner, PersistentDataType.STRING, best.player().toString());
    }
}
