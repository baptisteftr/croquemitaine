package fr.croquemitaine.powers.impl;

import fr.croquemitaine.powers.PowerContext;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Révélation : une fois, particules au-dessus de la cible visibles par le Croque seul. */
public final class RevealPower extends CooldownPower {
    private BukkitTask task;

    public RevealPower() {
        super(Material.SPYGLASS);
    }

    @Override public String id() { return "reveal"; }

    @Override
    protected int cooldownSeconds(PowerContext ctx) {
        return 0;
    }

    @Override
    protected boolean use(Player croque, PowerContext ctx) {
        Player target = ctx.nearestTarget(croque, Double.MAX_VALUE);
        if (target == null) {
            ctx.messages().send(croque, "powers.no-target");
            return false;
        }
        int periods = ctx.config().revealDuration * 4; // toutes les 5 ticks
        task = ctx.game().plugin().getServer().getScheduler().runTaskTimer(ctx.game().plugin(), new Runnable() {
            int left = periods;

            @Override
            public void run() {
                if (left-- <= 0 || !croque.isOnline() || !target.isOnline()) {
                    cancelTask();
                    return;
                }
                // spawnParticle sur le joueur : seul lui reçoit le paquet.
                croque.spawnParticle(Particle.END_ROD, target.getLocation().add(0, 2.4, 0), 14, 0.15, 0.6, 0.15, 0.0);
                croque.spawnParticle(Particle.GLOW, target.getLocation().add(0, 1.0, 0), 10, 0.3, 0.6, 0.3, 0.0);
            }
        }, 0L, 5L);
        return true;
    }

    private void cancelTask() {
        if (task != null) task.cancel();
        task = null;
    }

    @Override
    public void onRevoke(Player croque, PowerContext ctx) {
        super.onRevoke(croque, ctx);
        cancelTask();
    }
}
