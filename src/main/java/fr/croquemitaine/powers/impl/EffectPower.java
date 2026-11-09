package fr.croquemitaine.powers.impl;

import fr.croquemitaine.powers.Power;
import fr.croquemitaine.powers.PowerContext;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Pouvoir passif = un effet de potion infini (Prédateur, Brute, Carapace). */
public final class EffectPower implements Power {
    private final String id;
    private final PotionEffectType type;
    private final int amplifier;

    public EffectPower(String id, PotionEffectType type, int amplifier) {
        this.id = id;
        this.type = type;
        this.amplifier = amplifier;
    }

    @Override public String id() { return id; }

    @Override
    public void onGrant(Player croque, PowerContext ctx) {
        croque.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, false, false, true));
    }

    @Override
    public void onRevoke(Player croque, PowerContext ctx) {
        // On ne retire que NOTRE effet infini, pas une potion bue normalement.
        PotionEffect cur = croque.getPotionEffect(type);
        if (cur != null && cur.isInfinite()) croque.removePotionEffect(type);
    }

    /** Même après la mort (qui purge les effets), on réapplique tant que la mission dure. */
    @Override
    public void onTick(Player croque, PowerContext ctx) {
        if (!croque.hasPotionEffect(type) && !croque.isDead()) onGrant(croque, ctx);
    }
}
