package fr.croquemitaine.powers.impl;

import fr.croquemitaine.powers.Power;
import fr.croquemitaine.powers.PowerContext;
import org.bukkit.entity.Player;

/** Déguisement : affiché vert foncé avec le maximum de vies pour tout le monde. */
public final class DisguisePower implements Power {
    @Override public String id() { return "disguise"; }

    @Override
    public void onGrant(Player croque, PowerContext ctx) {
        ctx.colors().setDisguised(croque.getUniqueId(), true);
    }

    @Override
    public void onRevoke(Player croque, PowerContext ctx) {
        ctx.colors().setDisguised(croque.getUniqueId(), false);
    }
}
