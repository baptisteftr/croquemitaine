package fr.croquemitaine.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.croquemitaine.config.GameConfig;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.croquemitaine.config.Messages;
import fr.croquemitaine.core.GameState;
import fr.croquemitaine.core.PlayerRecord;
import fr.croquemitaine.game.GameContext;
import fr.croquemitaine.game.GameManager;
import fr.croquemitaine.players.LivesService;
import fr.croquemitaine.powers.PowerManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.function.Consumer;

/** /croque … : commandes d'administration (§6.11), toutes sous croque.admin. */
public final class CroqueCommand {
    public static final String PERMISSION = "croque.admin";
    private final GameContext ctx;
    private final GameManager game;
    private final LivesService lives;
    private final PowerManager powers;
    private final Runnable reload;

    public CroqueCommand(GameContext ctx, GameManager game, LivesService lives, PowerManager powers, Runnable reload) {
        this.ctx = ctx;
        this.game = game;
        this.lives = lives;
        this.powers = powers;
        this.reload = reload;
    }

    private Messages msg() {
        return ctx.messages();
    }

    /** Destinataire des réponses : l'exécutant d'un « /execute as <joueur> », sinon l'émetteur. */
    private static CommandSender sender(CommandContext<CommandSourceStack> c) {
        return c.getSource().getExecutor() instanceof Player p ? p : c.getSource().getSender();
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("croque")
                .requires(s -> s.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("start").executes(c -> {
                    if (ctx.state().isActive()) msg().send(sender(c), "game.already-running");
                    else if (!game.start()) msg().send(sender(c), "game.not-enough-players");
                    else echo(c, "game.started-admin");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("stop").executes(c -> requireActive(c, () -> {
                    game.stop();
                    echo(c, "game.stopped");
                })))
                .then(Commands.literal("pause").executes(c -> {
                    if (!game.pause()) msg().send(sender(c), "game.not-running");
                    else echo(c, "game.paused");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("resume").executes(c -> {
                    if (!game.resume()) msg().send(sender(c), "game.not-running");
                    else echo(c, "game.resumed");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("skip").executes(c -> requireActive(c, () -> {
                    game.skipRound();
                    msg().send(sender(c), "admin.skipped");
                })))
                .then(Commands.literal("force").then(Commands.argument("player", io.papermc.paper.command.brigadier.argument.ArgumentTypes.player())
                        .executes(c -> requireActive(c, () -> {
                            Player p;
                            try {
                                p = c.getArgument("player", io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver.class)
                                        .resolve(c.getSource()).getFirst();
                            } catch (Exception e) {
                                return;
                            }
                            if (game.forceCroque(p)) msg().send(sender(c), "croque.forced", Messages.p("player", p.getName()));
                            else msg().send(sender(c), "admin.force-not-eligible", Messages.p("player", p.getName()));
                        }))))
                .then(Commands.literal("lives").then(CommandSupport.playerArg(ctx, "player")
                        .then(livesOp("set", (r, n) -> lives.set(r.uuid(), n)))
                        .then(livesOp("add", (r, n) -> lives.add(r.uuid(), n)))
                        .then(livesOp("remove", (r, n) -> lives.add(r.uuid(), -n)))))
                .then(Commands.literal("revive").then(CommandSupport.playerArg(ctx, "player").executes(c -> withRecord(c, r -> {
                    if (r.isAlive()) {
                        msg().send(sender(c), "admin.not-eliminated", Messages.p("player", r.name()));
                        return;
                    }
                    lives.set(r.uuid(), 1);
                    msg().send(sender(c), "admin.revived", Messages.p("player", r.name()));
                }))))
                .then(Commands.literal("border").then(Commands.argument("size", IntegerArgumentType.integer(1))
                        .executes(c -> border(c, 0))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                .executes(c -> border(c, IntegerArgumentType.getInteger(c, "seconds"))))))
                .then(Commands.literal("givepower").then(Commands.argument("power", StringArgumentType.word())
                        .suggests((c, b) -> {
                            GameConfig.POWER_IDS.stream().filter(id -> id.startsWith(b.getRemainingLowerCase())).forEach(b::suggest);
                            return b.buildFuture();
                        })
                        .executes(c -> requireActive(c, () -> {
                            String id = StringArgumentType.getString(c, "power");
                            if (ctx.state().croque() == null || !GameConfig.POWER_IDS.contains(id)) {
                                msg().send(sender(c), "admin.givepower-failed", Messages.p("power", id));
                                return;
                            }
                            powers.choose(id);
                            msg().send(sender(c), "admin.givepower", Messages.p("power", id));
                        }))))
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.literal("reload").executes(c -> {
                    reload.run();
                    msg().send(sender(c), "admin.reloaded");
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> livesOp(
            String name, java.util.function.BiConsumer<PlayerRecord, Integer> op) {
        return Commands.literal(name).then(Commands.argument("n", IntegerArgumentType.integer(0)).executes(c -> withRecord(c, r -> {
            op.accept(r, IntegerArgumentType.getInteger(c, "n"));
            msg().send(sender(c), "admin.lives-set", Messages.p("player", r.name()), Messages.p("lives", r.lives()));
        })));
    }

    /** Les annonces vont aux joueurs ; un admin en console / RCON reçoit aussi une confirmation. */
    private void echo(CommandContext<CommandSourceStack> c, String key) {
        if (!(sender(c) instanceof Player)) msg().send(sender(c), key);
    }

    private int requireActive(CommandContext<CommandSourceStack> c, Runnable r) {
        if (!ctx.state().isActive()) msg().send(sender(c), "game.not-running");
        else r.run();
        return Command.SINGLE_SUCCESS;
    }

    private int withRecord(CommandContext<CommandSourceStack> c, Consumer<PlayerRecord> action) {
        PlayerRecord r = CommandSupport.findRecord(ctx, c, "player");
        if (r == null) msg().send(sender(c), "admin.unknown-player");
        else action.accept(r);
        return Command.SINGLE_SUCCESS;
    }

    private int border(CommandContext<CommandSourceStack> c, int seconds) {
        int size = IntegerArgumentType.getInteger(c, "size");
        game.setBorder(size, seconds);
        msg().send(sender(c), "admin.border-set", Messages.p("size", size));
        return Command.SINGLE_SUCCESS;
    }

    private int status(CommandContext<CommandSourceStack> c) {
        CommandSender s = sender(c);
        GameState st = ctx.state();
        int time = st.intermissionRemaining() > 0 ? st.intermissionRemaining() : st.roundRemaining();
        msg().send(s, "admin.status-header", Messages.p("phase", st.phase().name()), Messages.p("round", st.round()),
                Messages.p("time", Messages.time(time)));
        if (st.croque() != null) {
            String pw = powers.activeIds().isEmpty() ? "-" : String.join(", ", powers.activeIds());
            if (powers.roulettePending()) pw += " (+ choix en cours)";
            msg().send(s, "admin.status-croque", Messages.p("player", game.nameOf(st.croque())), Messages.p("power", pw));
        } else {
            msg().send(s, "admin.status-no-croque");
        }
        for (PlayerRecord r : st.records()) {
            boolean online = Bukkit.getPlayer(r.uuid()) != null;
            msg().send(s, "admin.status-line", Messages.p("player", r.name()), Messages.p("lives", r.lives()),
                    Messages.c("category", msg().get("category." + r.category().name().toLowerCase(Locale.ROOT))),
                    Messages.c("online", online ? net.kyori.adventure.text.Component.empty() : msg().get("admin.offline-tag")));
        }
        return Command.SINGLE_SUCCESS;
    }
}
