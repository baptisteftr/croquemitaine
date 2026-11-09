package fr.croquemitaine.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.croquemitaine.core.PlayerRecord;
import fr.croquemitaine.game.GameContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import java.util.Locale;

/** Argument « joueur de la partie » (connecté ou non), avec auto-complétion sur les inscrits. */
final class CommandSupport {
    private CommandSupport() {
    }

    static RequiredArgumentBuilder<CommandSourceStack, String> playerArg(GameContext ctx, String name) {
        return Commands.argument(name, StringArgumentType.word()).suggests((c, b) -> {
            String rem = b.getRemainingLowerCase();
            for (PlayerRecord r : ctx.state().records()) {
                if (r.name().toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(r.name());
            }
            return b.buildFuture();
        });
    }

    static PlayerRecord findRecord(GameContext ctx, CommandContext<CommandSourceStack> c, String arg) {
        String n = StringArgumentType.getString(c, arg);
        for (PlayerRecord r : ctx.state().records()) {
            if (r.name().equalsIgnoreCase(n)) return r;
        }
        return null;
    }
}
