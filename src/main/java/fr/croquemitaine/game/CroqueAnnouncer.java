package fr.croquemitaine.game;

import fr.croquemitaine.config.GameConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Annonce « roulette » du Croque. Tout le monde voit exactement la même séquence,
 * au même rythme ; seul le titre final diffère, pour ne rien trahir.
 */
public final class CroqueAnnouncer {
    private static final long FRAME_TICKS = 10L;
    private final GameContext ctx;
    private BukkitTask task;

    public CroqueAnnouncer(GameContext ctx) {
        this.ctx = ctx;
    }

    public void cancel() {
        if (task != null) task.cancel();
        task = null;
    }

    /** @param onDone appelé une fois le titre final affiché (lance la roulette de pouvoirs). */
    public void announce(UUID croque, Runnable onDone) {
        cancel();
        GameConfig c = ctx.config();
        int frames = (int) (c.announceSuspenseSeconds * 20L / FRAME_TICKS);
        Component title = ctx.messages().get("announce.suspense-title");
        List<Component> subs = ctx.messages().list("announce.suspense-subtitle-frames");
        Title.Times frameTimes = Title.Times.times(Duration.ZERO, Duration.ofMillis(FRAME_TICKS * 50 + 200), Duration.ZERO);
        Title.Times finalTimes = Title.Times.times(Duration.ZERO, Duration.ofSeconds(4), Duration.ofMillis(500));

        task = Bukkit.getScheduler().runTaskTimer(ctx.plugin(), new Runnable() {
            int frame = 0;

            @Override
            public void run() {
                if (frame < frames) {
                    Component sub = subs.get(frame % subs.size());
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showTitle(Title.title(title, sub, frameTimes));
                        p.playSound(p, Sound.UI_BUTTON_CLICK, 0.6f, 1.2f + (frame % 2) * 0.3f);
                    }
                    frame++;
                    return;
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    boolean isCroque = p.getUniqueId().equals(croque);
                    Component t = ctx.messages().get(isCroque ? "announce.you-are-title" : "announce.not-you-title");
                    Component s = ctx.messages().get(isCroque ? "announce.you-are-subtitle" : "announce.not-you-subtitle");
                    p.showTitle(Title.title(t, s, finalTimes));
                    p.playSound(p, Sound.BLOCK_BELL_USE, 0.8f, 0.6f);
                }
                cancel();
                onDone.run();
            }
        }, 0L, FRAME_TICKS);
    }
}
