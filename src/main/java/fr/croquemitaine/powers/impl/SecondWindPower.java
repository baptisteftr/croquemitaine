package fr.croquemitaine.powers.impl;

import fr.croquemitaine.powers.Power;
import fr.croquemitaine.powers.PowerContext;
import org.bukkit.entity.Player;

/**
 * Second souffle : purement logique, géré par GameManager à la mort du Croque
 * (flag secondWindUsed dans GameState pour survivre à un redémarrage).
 */
public final class SecondWindPower implements Power {
    @Override public String id() { return "second_wind"; }

    @Override public void onGrant(Player croque, PowerContext ctx) { }

    @Override public void onRevoke(Player croque, PowerContext ctx) { }
}
