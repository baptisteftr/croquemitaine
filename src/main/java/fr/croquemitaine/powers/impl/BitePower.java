package fr.croquemitaine.powers.impl;

import fr.croquemitaine.powers.Power;
import fr.croquemitaine.powers.PowerContext;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Morsure : chaque coup attribué au Croque inflige Lenteur II (appelé par PowerManager). */
public final class BitePower implements Power {
    @Override public String id() { return "bite"; }

    @Override public void onGrant(Player croque, PowerContext ctx) { }

    @Override public void onRevoke(Player croque, PowerContext ctx) { }

    public void onHit(Player victim, PowerContext ctx) {
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ctx.config().biteSlownessSeconds * 20, 1));
    }
}
