package fr.croquemitaine.game;

import fr.croquemitaine.config.Messages;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Chrono secret du Croque : une bossbar Adventure montrée uniquement à lui
 * (les autres joueurs ne reçoivent jamais le paquet).
 */
public final class RoundTimer {
    private final GameContext ctx;
    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
    private UUID shownTo;

    public RoundTimer(GameContext ctx) {
        this.ctx = ctx;
    }

    public void update() {
        var s = ctx.state();
        bar.name(ctx.messages().get("croque.bossbar", Messages.p("time", Messages.time(s.roundRemaining()))));
        float progress = s.roundTotal() <= 0 ? 0f : (float) s.roundRemaining() / s.roundTotal();
        bar.progress(Math.clamp(progress, 0f, 1f));
    }

    public void show(Player p) {
        hide();
        update();
        p.showBossBar(bar);
        shownTo = p.getUniqueId();
    }

    public void hide() {
        if (shownTo != null) {
            Player p = Bukkit.getPlayer(shownTo);
            if (p != null) p.hideBossBar(bar);
        }
        shownTo = null;
        // Sécurité : aucun autre joueur ne doit la voir.
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
    }
}
