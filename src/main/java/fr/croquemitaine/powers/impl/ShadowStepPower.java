package fr.croquemitaine.powers.impl;

import fr.croquemitaine.powers.PowerContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Saut de l'ombre : une fois, TP derrière la cible autorisée la plus proche. */
public final class ShadowStepPower extends CooldownPower {
    public ShadowStepPower() {
        super(Material.ENDER_EYE);
    }

    @Override public String id() { return "shadow_step"; }

    @Override
    protected int cooldownSeconds(PowerContext ctx) {
        return 0;
    }

    @Override
    protected boolean use(Player croque, PowerContext ctx) {
        Player t = ctx.nearestTarget(croque, ctx.config().shadowStepRange);
        Location dest = t == null ? null : behind(t);
        if (dest == null) {
            ctx.messages().send(croque, "powers.no-target");
            return false;
        }
        croque.teleport(dest);
        return true;
    }

    /** Essaie 1,5 puis 1 bloc derrière la cible, en vérifiant 2 blocs libres et un sol. */
    private static Location behind(Player t) {
        Vector dir = t.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
        dir.normalize();
        for (double d : new double[]{1.5, 1.0}) {
            Location l = t.getLocation().clone().subtract(dir.clone().multiply(d));
            Block feet = l.getBlock();
            if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable()
                    && feet.getRelative(0, -1, 0).getType().isSolid()) {
                l.setDirection(t.getLocation().toVector().subtract(l.toVector()));
                return l;
            }
        }
        return null;
    }
}
