package fr.croquemitaine.powers.impl;

import fr.croquemitaine.powers.Power;
import fr.croquemitaine.powers.PowerContext;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Traqueur : boussole pointant vers la cible autorisée la plus proche, mise à jour périodiquement. */
public final class TrackerPower implements Power {
    private int counter;

    @Override public String id() { return "tracker"; }

    @Override
    public void onGrant(Player croque, PowerContext ctx) {
        ctx.giveItem(croque, Material.COMPASS, id());
        update(croque, ctx);
    }

    @Override
    public void onRevoke(Player croque, PowerContext ctx) {
        ctx.removeItems(croque);
        croque.setCompassTarget(croque.getWorld().getSpawnLocation());
    }

    @Override
    public void onTick(Player croque, PowerContext ctx) {
        ctx.giveItem(croque, Material.COMPASS, id());
        if (++counter % ctx.config().trackerUpdateSeconds == 0) update(croque, ctx);
    }

    private void update(Player croque, PowerContext ctx) {
        Player t = ctx.nearestTarget(croque, Double.MAX_VALUE);
        if (t != null) croque.setCompassTarget(t.getLocation());
    }
}
