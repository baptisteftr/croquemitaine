package fr.croquemitaine.game;

import fr.croquemitaine.config.Messages;
import fr.croquemitaine.core.GameState;
import org.bukkit.Bukkit;

import java.util.concurrent.TimeUnit;

/** Mode « assassins seuls » : après un délai, la bordure se referme progressivement. */
public final class BorderShrinker {
    private final GameContext ctx;

    public BorderShrinker(GameContext ctx) {
        this.ctx = ctx;
    }

    public void trigger() {
        GameState s = ctx.state();
        if (s.endgameShrinking()) return;
        s.endgameShrinking(true);
        s.shrinkDelayRemaining(ctx.config().shrinkDelay);
        Bukkit.getOnlinePlayers().forEach(p -> ctx.messages().send(p, "endgame.assassins-only",
                Messages.p("time", Messages.time(ctx.config().shrinkDelay))));
        if (ctx.config().shrinkDelay == 0) start();
    }

    /** Appelé chaque seconde (partie en cours). */
    public void tick() {
        GameState s = ctx.state();
        if (!s.endgameShrinking() || s.shrinkDelayRemaining() <= 0) return;
        s.shrinkDelayRemaining(s.shrinkDelayRemaining() - 1);
        if (s.shrinkDelayRemaining() == 0) start();
    }

    private void start() {
        ctx.state().shrinkDelayRemaining(-1);
        ctx.world().getWorldBorder().changeSize(ctx.config().shrinkTarget, TimeUnit.SECONDS.toMillis(ctx.config().shrinkDuration) / 50);
        Bukkit.getOnlinePlayers().forEach(p -> ctx.messages().send(p, "endgame.shrinking"));
    }
}
