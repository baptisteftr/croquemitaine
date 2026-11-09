package fr.croquemitaine.powers.impl;

import fr.croquemitaine.config.Messages;
import fr.croquemitaine.powers.Power;
import fr.croquemitaine.powers.PowerContext;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Base des pouvoirs actifs : item dédié + recharge (0 = usage unique). */
public abstract class CooldownPower implements Power {
    private final Material item;
    private long readyAt;
    private boolean used;

    protected CooldownPower(Material item) {
        this.item = item;
    }

    @Override public boolean active() { return true; }

    /** Recharge en secondes ; ≤ 0 signifie « une seule fois ». */
    protected abstract int cooldownSeconds(PowerContext ctx);

    /** Retourne false si l'utilisation n'a pas eu lieu (pas de cible…) : rien n'est consommé. */
    protected abstract boolean use(Player croque, PowerContext ctx);

    @Override
    public void onGrant(Player croque, PowerContext ctx) {
        ctx.giveItem(croque, item, id());
    }

    @Override
    public void onRevoke(Player croque, PowerContext ctx) {
        ctx.removeItems(croque);
    }

    @Override
    public final void onUse(Player croque, PowerContext ctx) {
        int cd = cooldownSeconds(ctx);
        if (cd <= 0 && used) {
            ctx.messages().send(croque, "powers.used-up");
            return;
        }
        long now = System.currentTimeMillis();
        if (now < readyAt) {
            ctx.messages().send(croque, "powers.cooldown", Messages.p("time", Messages.time((int) ((readyAt - now + 999) / 1000))));
            return;
        }
        if (!use(croque, ctx)) return;
        used = true;
        if (cd > 0) {
            readyAt = now + cd * 1000L;
            croque.setCooldown(item, cd * 20);
        } else {
            ctx.removeItems(croque);
        }
    }

    @Override
    public void onTick(Player croque, PowerContext ctx) {
        // L'item d'un pouvoir à usage unique déjà consommé ne doit pas revenir.
        if (!(cooldownSeconds(ctx) <= 0 && used)) ctx.giveItem(croque, item, id());
    }
}
