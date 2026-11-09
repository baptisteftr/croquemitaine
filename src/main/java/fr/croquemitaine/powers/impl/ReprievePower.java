package fr.croquemitaine.powers.impl;

import fr.croquemitaine.powers.Power;
import fr.croquemitaine.powers.PowerContext;
import org.bukkit.entity.Player;

/** Sursis : instantané, ajoute du temps au chrono une seule fois (pas à la reconnexion). */
public final class ReprievePower implements Power {
    @Override public String id() { return "reprieve"; }

    @Override
    public void onChosen(Player croque, PowerContext ctx) {
        ctx.addRoundSeconds(ctx.config().reprieveSeconds);
    }

    @Override public void onGrant(Player croque, PowerContext ctx) { }

    @Override public void onRevoke(Player croque, PowerContext ctx) { }
}
