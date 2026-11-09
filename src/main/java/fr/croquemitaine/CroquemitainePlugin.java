package fr.croquemitaine;

import fr.croquemitaine.combat.DamageAttribution;
import fr.croquemitaine.combat.KillTracker;
import fr.croquemitaine.combat.PvpListener;
import fr.croquemitaine.commands.CroqueCommand;
import fr.croquemitaine.commands.DeathHistoryCommand;
import fr.croquemitaine.config.GameConfig;
import fr.croquemitaine.config.Messages;
import fr.croquemitaine.game.GameContext;
import fr.croquemitaine.game.GameManager;
import fr.croquemitaine.game.Keys;
import fr.croquemitaine.players.EliminationBox;
import fr.croquemitaine.players.LivesService;
import fr.croquemitaine.players.NameColorService;
import fr.croquemitaine.players.RespawnService;
import fr.croquemitaine.powers.PowerContext;
import fr.croquemitaine.powers.PowerManager;
import fr.croquemitaine.powers.PowerRegistry;
import fr.croquemitaine.rules.EnchantingTableRules;
import fr.croquemitaine.rules.HelmetBanListener;
import fr.croquemitaine.rules.MobEggDropListener;
import fr.croquemitaine.rules.TntRecipe;
import fr.croquemitaine.storage.GameStorage;
import fr.croquemitaine.voice.VoiceChatLoader;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Point d'entrée : câblage des services (injection par constructeur, aucun singleton statique). */
public final class CroquemitainePlugin extends JavaPlugin {
    private GameContext ctx;
    private GameManager game;
    private PvpListener pvp;
    private TntRecipe tnt;
    private MobEggDropListener eggs;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages messages = new Messages(this);
        ctx = new GameContext(this, new GameConfig(getConfig(), getLogger()), messages);
        Keys keys = new Keys(this);

        LivesService lives = new LivesService(ctx);
        NameColorService colors = new NameColorService(ctx);
        EliminationBox box = new EliminationBox(ctx);
        RespawnService respawn = new RespawnService(ctx);
        KillTracker killTracker = new KillTracker(ctx.config().killCreditSeconds);
        GameStorage storage = new GameStorage(getDataFolder().toPath(), getLogger());

        game = new GameManager(ctx, storage, lives, colors, box, respawn, killTracker);
        PowerContext pctx = new PowerContext(ctx, keys, colors, game::croqueTargets, game::addRoundSeconds);
        PowerManager powers = new PowerManager(ctx, new PowerRegistry(), pctx);
        game.setPowers(powers);

        pvp = new PvpListener(ctx, new DamageAttribution(keys), killTracker, powers::onHit);
        eggs = new MobEggDropListener(ctx, keys);
        HelmetBanListener helmets = new HelmetBanListener(ctx);
        EnchantingTableRules table = new EnchantingTableRules(ctx);
        tnt = new TntRecipe(keys);

        tnt.register(ctx.config().tntSimplified);
        table.removeRecipe();
        game.everySecond(helmets::sweep);
        game.onGameStart(table::place);
        game.onGameStop(table::remove);

        for (Listener l : List.of(game, colors, pvp, powers, eggs, helmets, table)) {
            getServer().getPluginManager().registerEvents(l, this);
        }

        CroqueCommand croque = new CroqueCommand(ctx, game, lives, powers, this::reloadAll);
        DeathHistoryCommand history = new DeathHistoryCommand(ctx);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(croque.build(), "Administration du Croquemitaine");
            event.registrar().register(history.build(), "Historique de tes morts");
        });

        game.enable();
        VoiceChatLoader.tryLoad(ctx);
        getLogger().info("Croquemitaine activé.");
    }

    private void reloadAll() {
        reloadConfig();
        ctx.config(new GameConfig(getConfig(), getLogger()));
        ctx.messages().reload();
        pvp.reloadRules();
        tnt.register(ctx.config().tntSimplified);
        eggs.invalidatePool();
    }

    @Override
    public void onDisable() {
        if (game != null) game.disable();
        if (tnt != null) tnt.unregister();
    }
}
