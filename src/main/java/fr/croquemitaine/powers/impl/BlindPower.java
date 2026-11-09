package fr.croquemitaine.powers.impl;

import fr.croquemitaine.powers.PowerContext;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Aveuglement : cécité aux joueurs vivants proches (le Croque exclu). */
public final class BlindPower extends CooldownPower {
    public BlindPower() {
        super(Material.INK_SAC);
    }

    @Override public String id() { return "blind"; }

    @Override
    protected int cooldownSeconds(PowerContext ctx) {
        return ctx.config().blindCooldown;
    }

    @Override
    protected boolean use(Player croque, PowerContext ctx) {
        double r = ctx.config().blindRadius;
        int ticks = ctx.config().blindDuration * 20;
        for (Player p : croque.getWorld().getPlayers()) {
            if (p == croque || p.getLocation().distance(croque.getLocation()) > r) continue;
            var cat = ctx.game().state().category(p.getUniqueId());
            if (cat == null || !cat.isAlive()) continue;
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0));
        }
        return true;
    }
}
