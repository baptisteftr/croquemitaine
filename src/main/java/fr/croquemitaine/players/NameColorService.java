package fr.croquemitaine.players;

import fr.croquemitaine.core.PlayerRecord;
import fr.croquemitaine.game.GameContext;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Couleur du pseudo (nametag, tab, chat) et vies dans la tab list.
 * Une équipe de scoreboard par palier de vies : changer de palier = changer d'équipe.
 * Le pouvoir « Déguisement » force l'affichage du palier 5 sans toucher aux vraies vies.
 */
public final class NameColorService implements Listener {
    private static final String TEAM_PREFIX = "cm_";
    private static final String OBJECTIVE = "cm_lives";
    private static final NamedTextColor[] COLORS = {
            NamedTextColor.DARK_GRAY,  // 0 : éliminé
            NamedTextColor.RED,        // 1 : assassin
            NamedTextColor.GOLD,       // 2 : hostile (orange)
            NamedTextColor.YELLOW,     // 3 : hostile
            NamedTextColor.GREEN,      // 4 : passif
            NamedTextColor.DARK_GREEN  // 5 : passif
    };

    private final GameContext ctx;
    private final Set<UUID> disguised = new HashSet<>();

    public NameColorService(GameContext ctx) {
        this.ctx = ctx;
    }

    private Scoreboard board() {
        return Bukkit.getScoreboardManager().getMainScoreboard();
    }

    /** Palier affiché : vraies vies, sauf déguisement ; au-delà de 5 on reste sur la couleur 5. */
    public int displayedLives(UUID u) {
        PlayerRecord r = ctx.state().record(u);
        if (r == null) return -1;
        if (disguised.contains(u) && r.isAlive()) return ctx.config().startLives;
        return r.lives();
    }

    public static NamedTextColor colorFor(int lives) {
        return COLORS[Math.clamp(lives, 0, COLORS.length - 1)];
    }

    public void setup() {
        Scoreboard b = board();
        for (int i = 0; i < COLORS.length; i++) {
            Team t = b.getTeam(TEAM_PREFIX + i);
            if (t == null) t = b.registerNewTeam(TEAM_PREFIX + i);
            t.color(COLORS[i]);
        }
        Objective o = b.getObjective(OBJECTIVE);
        if (o == null) o = b.registerNewObjective(OBJECTIVE, Criteria.DUMMY, Component.text("Vies"));
        o.setRenderType(RenderType.INTEGER);
        o.setDisplaySlot(DisplaySlot.PLAYER_LIST);
    }

    public void update(UUID u) {
        PlayerRecord r = ctx.state().record(u);
        if (r == null) return;
        int shown = displayedLives(u);
        Scoreboard b = board();
        String entry = r.name();
        Team target = b.getTeam(TEAM_PREFIX + Math.clamp(shown, 0, COLORS.length - 1));
        if (target != null && !target.hasEntry(entry)) target.addEntry(entry);
        Objective o = b.getObjective(OBJECTIVE);
        if (o != null) o.getScore(entry).setScore(shown);
    }

    public void updateAll() {
        for (PlayerRecord r : ctx.state().records()) update(r.uuid());
    }

    public void setDisguised(UUID u, boolean on) {
        if (on) disguised.add(u); else disguised.remove(u);
        update(u);
    }

    /** Retire toutes les équipes/objectif du plugin (fin de partie). */
    public void teardown() {
        disguised.clear();
        Scoreboard b = board();
        for (int i = 0; i < COLORS.length; i++) {
            Team t = b.getTeam(TEAM_PREFIX + i);
            if (t != null) t.unregister();
        }
        Objective o = b.getObjective(OBJECTIVE);
        if (o != null) o.unregister();
    }

    /** Chat : pseudo coloré selon le palier affiché (déguisement compris). */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onChat(AsyncChatEvent e) {
        if (!ctx.state().isActive()) return;
        Player p = e.getPlayer();
        int shown = displayedLives(p.getUniqueId());
        if (shown < 0) return;
        // Un éliminé voit tout (spectateur) : son chat ne doit pas renseigner les vivants.
        if (ctx.config().eliminatedPrivateChat && shown == 0) {
            e.viewers().removeIf(v -> v instanceof Player vp && ctx.state().record(vp.getUniqueId()) != null
                    && ctx.state().record(vp.getUniqueId()).isAlive());
        }
        NamedTextColor color = colorFor(shown);
        e.renderer((source, name, message, viewer) -> Component.text("<")
                .append(Component.text(source.getName(), color))
                .append(Component.text("> "))
                .append(message));
    }
}
