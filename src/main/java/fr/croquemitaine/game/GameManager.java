package fr.croquemitaine.game;

import fr.croquemitaine.combat.KillTracker;
import fr.croquemitaine.config.GameConfig;
import fr.croquemitaine.config.Messages;
import fr.croquemitaine.core.Category;
import fr.croquemitaine.core.DeathRecord;
import fr.croquemitaine.core.GameState;
import fr.croquemitaine.core.Phase;
import fr.croquemitaine.core.PlayerRecord;
import fr.croquemitaine.core.RoundEngine;
import fr.croquemitaine.core.WinCondition;
import fr.croquemitaine.players.EliminationBox;
import fr.croquemitaine.players.LivesService;
import fr.croquemitaine.players.NameColorService;
import fr.croquemitaine.players.RespawnService;
import fr.croquemitaine.powers.PowerManager;
import fr.croquemitaine.storage.GameStorage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Orchestration de la partie : machine à états (§6.6), unique ticker 1 Hz,
 * traitement des morts (§6.2), connexions, fin de partie et persistance.
 */
public final class GameManager implements Listener {
    private final GameContext ctx;
    private final GameStorage storage;
    private final LivesService lives;
    private final NameColorService colors;
    private final EliminationBox box;
    private final RespawnService respawn;
    private final KillTracker killTracker;
    private final RoundTimer timer;
    private final CroqueAnnouncer announcer;
    private final MapSizer mapSizer;
    private final BorderShrinker shrinker;
    private final List<Runnable> perSecond = new ArrayList<>();
    private final List<Runnable> onStart = new ArrayList<>();
    private PowerManager powers;

    private BukkitTask ticker;
    private int autosaveCounter;
    /** Le Croque a-t-il vu son annonce ? (sinon on ne lui montre pas encore le chrono) */
    private boolean croqueRevealed;

    public GameManager(GameContext ctx, GameStorage storage, LivesService lives, NameColorService colors,
                       EliminationBox box, RespawnService respawn, KillTracker killTracker) {
        this.ctx = ctx;
        this.storage = storage;
        this.lives = lives;
        this.colors = colors;
        this.box = box;
        this.respawn = respawn;
        this.killTracker = killTracker;
        this.timer = new RoundTimer(ctx);
        this.announcer = new CroqueAnnouncer(ctx);
        this.mapSizer = new MapSizer(ctx);
        this.shrinker = new BorderShrinker(ctx);
        lives.addListener(this::onLivesChanged);
    }

    public void setPowers(PowerManager powers) {
        this.powers = powers;
    }

    /** Tâches exécutées chaque seconde pendant une partie (ex. balayage des casques). */
    public void everySecond(Runnable r) {
        perSecond.add(r);
    }

    private final List<Runnable> onStop = new ArrayList<>();

    /** Tâches exécutées au /croque stop (ex. retrait de la table d'enchantement). */
    public void onGameStop(Runnable r) {
        onStop.add(r);
    }

    /** Tâches exécutées au /croque start (ex. pose de la table d'enchantement). */
    public void onGameStart(Runnable r) {
        onStart.add(r);
    }

    private GameState state() {
        return ctx.state();
    }

    private RoundEngine engine() {
        GameConfig c = ctx.config();
        return new RoundEngine(c.roundInitialSeconds, c.roundDecrementSeconds, c.roundMinSeconds);
    }

    private void broadcast(String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... r) {
        for (Player p : Bukkit.getOnlinePlayers()) ctx.messages().send(p, key, r);
        Bukkit.getConsoleSender().sendMessage(ctx.messages().get(key, r));
    }

    // ------------------------------------------------------------------ cycle de vie plugin

    public void enable() {
        ctx.state(storage.load());
        if (state().isActive() || state().phase() == Phase.ENDED) {
            colors.setup();
            colors.updateAll();
            croqueRevealed = true;
            for (Player p : Bukkit.getOnlinePlayers()) handleJoin(p);
            ctx.plugin().getLogger().info("Partie restaurée : manche " + state().round() + ", " + state().roundRemaining() + " s restantes.");
        }
        ticker = Bukkit.getScheduler().runTaskTimer(ctx.plugin(), this::tick, 20L, 20L);
    }

    public void disable() {
        if (ticker != null) ticker.cancel();
        announcer.cancel();
        Player c = croquePlayer();
        if (c != null && powers != null) powers.onCroqueQuit(c);
        timer.hide();
        storage.save(state());
    }

    public void save() {
        storage.save(state());
    }

    // ------------------------------------------------------------------ commandes admin

    public boolean start() {
        if (state().isActive()) return false;
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) return false;
        GameConfig c = ctx.config();
        GameState s = state();
        s.reset();
        killTracker.clearAll();
        s.world(c.world);
        s.center(c.centerX, c.centerZ);
        World w = ctx.world();
        s.world(w.getName());

        int size = mapSizer.apply(w, online.size());
        s.borderSize(size);
        applyGameRules(w);
        for (Player p : online) s.players().put(p.getUniqueId(), new PlayerRecord(p.getUniqueId(), p.getName(), c.startLives));

        if (!c.eliminatedSpectate) box.build();
        onStart.forEach(Runnable::run);
        colors.setup();
        colors.updateAll();

        for (Player p : online) {
            p.setGameMode(GameMode.SURVIVAL);
            p.setInvulnerable(false);
            p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
            p.setFoodLevel(20);
            p.teleport(respawn.randomSafeLocation());
        }
        s.phase(Phase.RUNNING);
        broadcast("game.started", Messages.p("size", size));
        startRound(1);
        return true;
    }

    private void applyGameRules(World w) {
        w.setGameRule(GameRules.KEEP_INVENTORY, true);
        w.setGameRule(GameRules.LOCATOR_BAR, false);
        w.setGameRule(GameRules.IMMEDIATE_RESPAWN, ctx.config().immediateRespawn);
        w.setGameRule(GameRules.RESPAWN_RADIUS, 0);
        w.setGameRule(GameRules.SPAWN_PHANTOMS, ctx.config().phantoms);
    }

    public void stop() {
        endMission();
        announcer.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (state().category(p.getUniqueId()) == Category.ELIMINATED || p.getGameMode() == GameMode.SPECTATOR) {
                box.release(p);
                p.teleport(respawn.randomSafeLocation());
            }
        }
        colors.teardown();
        onStop.forEach(Runnable::run);
        state().reset();
        killTracker.clearAll();
        storage.save(state());
        broadcast("game.stopped");
    }

    public boolean pause() {
        if (state().phase() != Phase.RUNNING) return false;
        state().phase(Phase.PAUSED);
        storage.save(state());
        broadcast("game.paused");
        return true;
    }

    public boolean resume() {
        if (state().phase() != Phase.PAUSED) return false;
        state().phase(Phase.RUNNING);
        storage.save(state());
        broadcast("game.resumed");
        return true;
    }

    /** Termine la manche sans pénalité. */
    public void skipRound() {
        endMission();
        nextRound();
    }

    /** Force le Croque de la manche en cours (sans pénalité pour l'ancien). */
    public boolean forceCroque(Player p) {
        PlayerRecord r = state().record(p.getUniqueId());
        if (r == null || !RoundEngine.isEligible(r.category()) || !state().isActive()) return false;
        endMission();
        // Pendant un entracte, la fin de l'entracte effacerait ce Croque : on démarre la manche maintenant.
        if (state().intermissionRemaining() > 0) startRound(state().round() + 1, p.getUniqueId());
        else designate(p.getUniqueId());
        return true;
    }

    public void setBorder(int size, int seconds) {
        state().borderSize(size);
        if (seconds > 0) ctx.world().getWorldBorder().changeSize(size, seconds * 20L);
        else ctx.world().getWorldBorder().setSize(size);
    }

    public void addRoundSeconds(int s) {
        state().roundRemaining(state().roundRemaining() + s);
        state().roundTotal(Math.max(state().roundTotal(), state().roundRemaining()));
        timer.update();
    }

    public Optional<PlayerRecord> record(UUID u) {
        return Optional.ofNullable(state().record(u));
    }

    // ------------------------------------------------------------------ manches

    private void tick() {
        GameState s = state();
        if (s.phase() == Phase.RUNNING) {
            if (s.intermissionRemaining() > 0) {
                s.intermissionRemaining(s.intermissionRemaining() - 1);
                if (s.intermissionRemaining() == 0) startRound(s.round() + 1);
            } else {
                s.roundRemaining(s.roundRemaining() - 1);
                if (s.croque() != null) timer.update();
                if (s.roundRemaining() <= 0) onRoundExpired();
            }
            if (state().phase() == Phase.RUNNING) {
                shrinker.tick();
                if (powers != null) powers.tick();
            }
        }
        if (s.isActive()) perSecond.forEach(Runnable::run);
        if (++autosaveCounter >= ctx.config().autosaveSeconds) {
            autosaveCounter = 0;
            if (s.phase() != Phase.LOBBY) storage.save(s);
        }
    }

    private void startRound(int n) {
        startRound(n, null);
    }

    /** @param forced Croque imposé par un admin (pas de tirage), ou null. */
    private void startRound(int n, UUID forced) {
        GameState s = state();
        s.round(n);
        int total = engine().durationSeconds(n);
        s.roundTotal(total);
        s.roundRemaining(total);
        s.intermissionRemaining(0);
        s.croque(null);
        s.croquePower(null);
        s.secondWindUsed(false);
        broadcast("game.round-start", Messages.p("round", n), Messages.p("time", Messages.time(total)));

        if (forced != null) {
            designate(forced);
        } else if (!engine().hasCroque(n)) {
            broadcast("game.round-start-no-croque");
        } else {
            List<UUID> eligible = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                Category c = s.category(p.getUniqueId());
                if (c != null && RoundEngine.isEligible(c)) eligible.add(p.getUniqueId());
            }
            engine().pickCroque(eligible, ThreadLocalRandom.current()).ifPresentOrElse(
                    this::designate, () -> broadcast("game.round-start-no-croque"));
        }
        storage.save(s);
    }

    private void designate(UUID u) {
        GameState s = state();
        s.croque(u);
        s.croquePower(null);
        s.secondWindUsed(false);
        croqueRevealed = false;
        int round = s.round();
        announcer.announce(u, () -> {
            // La mission a pu se terminer pendant l'annonce (mort, skip…).
            if (!s.isCroque(u) || s.round() != round) return;
            croqueRevealed = true;
            Player p = Bukkit.getPlayer(u);
            if (p != null) timer.show(p);
            // La roulette (un inventaire) masquerait le titre « Tu es le Croquemitaine » : on attend un peu.
            Bukkit.getScheduler().runTaskLater(ctx.plugin(), () -> {
                if (s.isCroque(u) && s.round() == round && powers != null) powers.beginMission();
            }, ctx.config().rouletteDelaySeconds * 20L);
        });
        ctx.plugin().getLogger().info("Croquemitaine de la manche " + round + " : " + nameOf(u));
        storage.save(s);
    }

    /** Fin de la mission en cours (quel qu'en soit le motif) : nettoyage, sans pénalité. */
    private void endMission() {
        UUID c = state().croque();
        if (c == null) return;
        announcer.cancel();
        if (powers != null) powers.revoke(c);
        timer.hide();
        state().croque(null);
        state().croquePower(null);
        croqueRevealed = false;
    }

    private void onRoundExpired() {
        UUID c = state().croque();
        if (c != null) {
            Player p = Bukkit.getPlayer(c);
            if (p != null) ctx.messages().send(p, "croque.failed-timeout");
            endMission();
            // Appliqué tout de suite, même si le Croque est déconnecté.
            lives.removeOne(c);
        }
        if (state().phase() == Phase.RUNNING || state().phase() == Phase.PAUSED) nextRound();
    }

    private void nextRound() {
        int inter = ctx.config().intermissionSeconds;
        if (inter > 0) {
            state().intermissionRemaining(inter);
            state().roundRemaining(0);
            broadcast("game.intermission", Messages.p("time", Messages.time(inter)));
            storage.save(state());
        } else {
            startRound(state().round() + 1);
        }
    }

    // ------------------------------------------------------------------ vies

    private void onLivesChanged(UUID u, int oldLives, int newLives) {
        colors.update(u);
        Player p = Bukkit.getPlayer(u);
        Category oldCat = Category.fromLives(oldLives), newCat = Category.fromLives(newLives);
        if (p != null && newLives > 0 && newLives < oldLives) {
            ctx.messages().send(p, "lives.lost", Messages.p("lives", newLives));
        }
        if (p != null && oldCat != newCat && newCat.isAlive()) {
            ctx.messages().send(p, "lives.category-change",
                    Messages.c("category", ctx.messages().get("category." + newCat.name().toLowerCase())));
        }
        if (newLives == 0 && oldLives > 0) {
            if (state().isCroque(u)) endMission();
            broadcast("lives.eliminated-broadcast", Messages.p("player", nameOf(u)));
            if (p != null) {
                ctx.messages().send(p, ctx.config().eliminatedSpectate ? "lives.eliminated-self-spectator" : "lives.eliminated-self");
                if (!p.isDead()) box.imprison(p);
            }
        } else if (oldLives == 0 && newLives > 0 && p != null) {
            box.release(p);
            p.teleport(respawn.randomSafeLocation());
        }
        checkWin();
        storage.save(state());
    }

    private void checkWin() {
        if (!state().isActive()) return;
        WinCondition.Result r = WinCondition.evaluate(state().records());
        switch (r.kind()) {
            case WINNER -> endGame(r.winner());
            case NO_SURVIVOR -> endGame(null);
            case ASSASSINS_ONLY -> shrinker.trigger();
            case NONE -> { }
        }
    }

    private void endGame(UUID winner) {
        endMission();
        announcer.cancel();
        state().phase(Phase.ENDED);
        if (winner == null) {
            broadcast("endgame.no-survivor");
        } else {
            String name = nameOf(winner);
            broadcast("endgame.victory-broadcast", Messages.p("player", name));
            Title t = Title.title(ctx.messages().get("endgame.victory-title", Messages.p("player", name)),
                    ctx.messages().get("endgame.victory-subtitle"));
            Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(t));
            Player wp = Bukkit.getPlayer(winner);
            if (wp != null) fireworks(wp.getLocation());
        }
        storage.save(state());
    }

    private void fireworks(Location l) {
        for (int i = 0; i < 5; i++) {
            Bukkit.getScheduler().runTaskLater(ctx.plugin(), () -> l.getWorld().spawn(l.clone().add(0, 1, 0), Firework.class, fw -> {
                FireworkMeta m = fw.getFireworkMeta();
                m.addEffect(FireworkEffect.builder().withColor(Color.RED, Color.ORANGE).withFade(Color.YELLOW)
                        .with(FireworkEffect.Type.BALL_LARGE).trail(true).build());
                m.setPower(1);
                fw.setFireworkMeta(m);
            }), i * 15L);
        }
    }

    // ------------------------------------------------------------------ événements joueurs

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        GameState s = state();
        if (!s.isActive()) return;
        Player victim = e.getPlayer();
        UUID vu = victim.getUniqueId();
        PlayerRecord vr = s.record(vu);
        if (vr == null || !vr.isAlive()) return;
        Category victimCat = vr.category();

        // 1. Tueur attribué (y compris indirect : œuf, TNT, chute après un coup…).
        UUID killer = killTracker.killer(vu, System.currentTimeMillis());
        killTracker.clear(vu);

        // 2. Historique.
        Location l = victim.getLocation();
        String cause = e.getDamageSource().getDamageType().getKey().getKey();
        vr.deaths().add(new DeathRecord(System.currentTimeMillis(), l.getWorld().getName(),
                l.getBlockX(), l.getBlockY(), l.getBlockZ(), cause, killer == null ? null : nameOf(killer)));

        // 3. Le Croque meurt : échec (sauf Second souffle non consommé), sans vie supplémentaire.
        boolean croqueFailed = false;
        if (s.isCroque(vu)) {
            if (powers != null && powers.has("second_wind") && !s.secondWindUsed()) {
                s.secondWindUsed(true);
                ctx.messages().send(victim, "croque.second-wind");
            } else {
                ctx.messages().send(victim, "croque.failed-death");
                endMission();
                croqueFailed = true;
            }
        }

        // 4. Le Croque tue un passif/hostile de sa propre main : réussite. La manche CONTINUE jusqu'au
        //    bout du chrono, sans nouveau Croque : rien ne trahit le moment de la réussite (arbitrage 2026-11-09).
        if (killer != null && s.isCroque(killer) && (victimCat == Category.PASSIVE || victimCat == Category.HOSTILE)) {
            Player kp = Bukkit.getPlayer(killer);
            if (kp != null) ctx.messages().send(kp, "croque.success");
            endMission();
        }

        // 5. Toute mort coûte une vie.
        lives.removeOne(vu);

        // 6. (victoire vérifiée par onLivesChanged) puis nouvelle manche si le Croque a échoué en mourant.
        if (croqueFailed && s.isActive()) nextRound();
        storage.save(s);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        PlayerRecord r = state().record(p.getUniqueId());
        if ((!state().isActive() && state().phase() != Phase.ENDED) || r == null) {
            // Hors partie : jamais sur le toit de la boîte d'élimination.
            if (box.isAtBoxLevel(e.getRespawnLocation())) e.setRespawnLocation(box.groundBelow(e.getRespawnLocation()));
            return;
        }
        if (!r.isAlive()) {
            e.setRespawnLocation(box.eliminatedRespawn());
            Bukkit.getScheduler().runTask(ctx.plugin(), () -> box.imprison(p));
            return;
        }
        if (state().phase() == Phase.ENDED) return;
        e.setRespawnLocation(respawn.randomSafeLocation());
        if (ctx.config().recoveryCompass) Bukkit.getScheduler().runTask(ctx.plugin(), () -> respawn.giveRecoveryCompass(p));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        handleJoin(e.getPlayer());
    }

    private void handleJoin(Player p) {
        GameState s = state();
        PlayerRecord known = s.record(p.getUniqueId());
        if ((known == null || known.isAlive() || !s.isActive()) && box.isAtBoxLevel(p.getLocation())
                && p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE) {
            p.teleport(box.groundBelow(p.getLocation()));
        }
        if (s.phase() == Phase.LOBBY) {
            if (powers != null) powers.context().removeItems(p);
            return;
        }
        PlayerRecord r = s.record(p.getUniqueId());
        if (r == null) {
            if (!s.isActive()) return;
            // Arrivée en cours de partie : inscrit avec les vies de départ (cf. docs/Backlog.md).
            r = new PlayerRecord(p.getUniqueId(), p.getName(), ctx.config().startLives);
            s.players().put(p.getUniqueId(), r);
            p.setGameMode(GameMode.SURVIVAL);
            p.teleport(respawn.randomSafeLocation());
            ctx.messages().send(p, "game.late-join", Messages.p("lives", r.lives()));
        }
        r.name(p.getName());
        colors.update(p.getUniqueId());
        if (!r.isAlive()) {
            box.imprison(p);
        } else if ((p.isMarkedInvulnerable() && p.getGameMode() == GameMode.ADVENTURE) || p.getGameMode() == GameMode.SPECTATOR) {
            // Ressuscité pendant son absence : on le remet en jeu.
            box.release(p);
            p.teleport(respawn.randomSafeLocation());
        }
        if (s.isCroque(p.getUniqueId())) {
            if (croqueRevealed) timer.show(p);
            if (powers != null) powers.onCroqueJoin(p);
        } else if (powers != null) {
            powers.context().removeItems(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (state().isCroque(p.getUniqueId()) && powers != null) powers.onCroqueQuit(p);
    }

    // ------------------------------------------------------------------ utilitaires

    public Player croquePlayer() {
        UUID u = state().croque();
        return u == null ? null : Bukkit.getPlayer(u);
    }

    public String nameOf(UUID u) {
        PlayerRecord r = state().record(u);
        if (r != null) return r.name();
        var op = Bukkit.getOfflinePlayer(u);
        return op.getName() != null ? op.getName() : u.toString();
    }

    /** Cibles autorisées du Croque, triées par distance (pouvoirs Traqueur, Saut, Révélation). */
    public List<Player> croqueTargets(Player croque) {
        List<Player> out = new ArrayList<>();
        for (Player p : croque.getWorld().getPlayers()) {
            if (p == croque) continue;
            Category c = state().category(p.getUniqueId());
            if (c == Category.PASSIVE || c == Category.HOSTILE) out.add(p);
        }
        out.sort((a, b) -> Double.compare(a.getLocation().distanceSquared(croque.getLocation()),
                b.getLocation().distanceSquared(croque.getLocation())));
        return out;
    }
}
