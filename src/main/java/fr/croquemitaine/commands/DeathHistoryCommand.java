package fr.croquemitaine.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.croquemitaine.config.Messages;
import fr.croquemitaine.core.DeathRecord;
import fr.croquemitaine.core.PlayerRecord;
import fr.croquemitaine.game.GameContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** /death-history [joueur] : historique de ses morts ; celui d'un autre réservé aux admins. */
public final class DeathHistoryCommand {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault());
    private final GameContext ctx;

    public DeathHistoryCommand(GameContext ctx) {
        this.ctx = ctx;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("death-history")
                .executes(c -> {
                    // « /execute as <joueur> run death-history » : l'exécutant compte comme le joueur.
                    Player p = c.getSource().getExecutor() instanceof Player e ? e
                            : c.getSource().getSender() instanceof Player s ? s : null;
                    if (p == null) return 0;
                    show(p, ctx.state().record(p.getUniqueId()));
                    return Command.SINGLE_SUCCESS;
                })
                .then(CommandSupport.playerArg(ctx, "player")
                        .requires(s -> s.getSender().hasPermission(CroqueCommand.PERMISSION))
                        .executes(this::other))
                .build();
    }

    private int other(CommandContext<CommandSourceStack> c) {
        show(c.getSource().getSender(), CommandSupport.findRecord(ctx, c, "player"));
        return Command.SINGLE_SUCCESS;
    }

    private void show(CommandSender to, PlayerRecord r) {
        Messages m = ctx.messages();
        if (r == null) {
            m.send(to, "death.unknown-player");
            return;
        }
        m.send(to, "death.history-header", Messages.p("player", r.name()));
        List<DeathRecord> deaths = r.deaths();
        if (deaths.isEmpty()) {
            to.sendMessage(m.get("death.history-empty"));
            return;
        }
        for (int i = 0; i < deaths.size(); i++) {
            DeathRecord d = deaths.get(i);
            Component killer = d.killerName() == null ? Component.empty()
                    : m.get("death.history-killer", Messages.p("killer", d.killerName()));
            to.sendMessage(m.get("death.history-line", Messages.p("index", i + 1),
                    Messages.p("date", FMT.format(Instant.ofEpochMilli(d.timestampMillis()))),
                    Messages.p("x", d.x()), Messages.p("y", d.y()), Messages.p("z", d.z()),
                    Messages.p("cause", d.cause()), Messages.c("killer", killer)));
        }
    }
}
