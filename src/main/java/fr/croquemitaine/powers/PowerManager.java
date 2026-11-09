package fr.croquemitaine.powers;

import fr.croquemitaine.config.GameConfig;
import fr.croquemitaine.config.Messages;
import fr.croquemitaine.core.GameState;
import fr.croquemitaine.core.WeightedPicker;
import fr.croquemitaine.game.GameContext;
import fr.croquemitaine.powers.impl.BitePower;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cycle de vie des pouvoirs du Croque : héritage, roulette / tirage, ticks, utilisation, révocation.
 * <p>
 * Le Croque possède tous les pouvoirs acquis par les Croques précédents (si powers.inherit)
 * + un nouveau, choisi (mode choice) ou tiré au hasard (mode random). Les instances sont recréées
 * à chaque mission : recharges et usages uniques repartent de zéro.
 * <p>
 * Les pouvoirs ne sont « appliqués » au joueur que lorsqu'il est connecté (révoqués à la déco,
 * réappliqués à la reconnexion) : aucun effet ne traîne après une mission terminée en son absence.
 */
public final class PowerManager implements Listener {
    private final GameContext ctx;
    private final PowerRegistry registry;
    private final PowerContext pctx;

    /** Pouvoirs actifs de la mission en cours, par id. */
    private final Map<String, Power> active = new LinkedHashMap<>();
    private PowerRoulette roulette;
    private long rouletteDeadline;

    public PowerManager(GameContext ctx, PowerRegistry registry, PowerContext pctx) {
        this.ctx = ctx;
        this.registry = registry;
        this.pctx = pctx;
    }

    public PowerContext context() {
        return pctx;
    }

    private GameState state() {
        return ctx.state();
    }

    private Player croquePlayer() {
        UUID u = state().croque();
        return u == null ? null : Bukkit.getPlayer(u);
    }

    public List<String> activeIds() {
        return List.copyOf(active.keySet());
    }

    public boolean has(String id) {
        return active.containsKey(id);
    }

    /**
     * Début de mission : applique les pouvoirs hérités puis lance le choix du nouveau.
     * Appelé à la fin de l'annonce.
     */
    public void beginMission() {
        roulette = null;
        Player p = croquePlayer();
        if (ctx.config().powerInherit) {
            for (String id : state().acquiredPowers()) {
                // Un pouvoir déjà actif (ex. donné par /croque givepower avant la roulette) est conservé tel quel.
                if (active.containsKey(id)) continue;
                Power pw = addPower(id, false);
                if (pw != null && p != null) pw.onGrant(p, pctx);
            }
        }
        // Le nouveau pouvoir de la mission a déjà pu être attribué par un admin.
        if (state().croquePower() == null) offerNewPower();
    }

    /** Candidats au nouveau pouvoir : poids > 0 et pas déjà possédés. */
    private Map<String, Integer> candidates() {
        Map<String, Integer> w = new LinkedHashMap<>(ctx.config().powerWeights);
        w.keySet().removeAll(active.keySet());
        if (ctx.config().powerInherit) w.keySet().removeAll(state().acquiredPowers());
        return w;
    }

    private void offerNewPower() {
        GameConfig c = ctx.config();
        int count = c.powerMode == GameConfig.PowerMode.CHOICE ? 2 : 1;
        List<String> options = WeightedPicker.pickDistinct(candidates(), count, ThreadLocalRandom.current());
        if (options.isEmpty()) return; // tous les pouvoirs sont déjà acquis
        if (options.size() == 1) {
            choose(options.getFirst());
            return;
        }
        roulette = new PowerRoulette(options, ctx.messages(), registry);
        rouletteDeadline = System.currentTimeMillis() + c.powerChoiceSeconds * 1000L;
        Player p = croquePlayer();
        if (p != null) roulette.open(p);
    }

    public boolean roulettePending() {
        return roulette != null;
    }

    private void chooseRandom() {
        if (roulette == null) return;
        List<String> o = roulette.options();
        choose(o.get(ThreadLocalRandom.current().nextInt(o.size())));
    }

    /** Ajoute le nouveau pouvoir de la mission (choisi ou tiré). */
    public void choose(String id) {
        PowerRoulette r = roulette;
        roulette = null;
        if (!registry.exists(id) || state().croque() == null || active.containsKey(id)) return;
        state().croquePower(id);
        if (!state().acquiredPowers().contains(id)) state().acquiredPowers().add(id);
        Player p = croquePlayer();
        if (r != null && p != null && p.getOpenInventory().getTopInventory().getHolder() == r) p.closeInventory();
        Power power = addPower(id, true);
        if (p != null) {
            power.onGrant(p, pctx);
            ctx.messages().send(p, "powers.chosen", Messages.p("power", ctx.messages().raw("powers.names." + id)));
        }
    }

    private Power addPower(String id, boolean chosenNow) {
        Power power = registry.create(id);
        if (power == null) return null;
        active.put(id, power);
        // onChosen (ex. Sursis : +temps) uniquement pour le Croque qui obtient le pouvoir comme NOUVEAU :
        // rejoué à chaque héritage, Sursis annulerait le raccourcissement des manches.
        if (chosenNow) power.onChosen(croquePlayer(), pctx);
        return power;
    }

    /** Appelé chaque seconde par le ticker principal. */
    public void tick() {
        if (roulette != null && System.currentTimeMillis() >= rouletteDeadline) chooseRandom();
        Player p = croquePlayer();
        if (p != null) for (Power pw : active.values()) pw.onTick(p, pctx);
    }

    /** Fin de mission (réussite, échec, mort, stop) : tout nettoyer. Idempotent. */
    public void revoke(UUID croque) {
        Player p = croque == null ? null : Bukkit.getPlayer(croque);
        PowerRoulette r = roulette;
        roulette = null;
        if (r != null && p != null && p.getOpenInventory().getTopInventory().getHolder() == r) p.closeInventory();
        if (p != null) {
            for (Power pw : active.values()) pw.onRevoke(p, pctx);
            pctx.removeItems(p);
        }
        if (croque != null) pctx.colors().setDisguised(croque, false);
        active.clear();
        state().croquePower(null);
    }

    /** Déconnexion du Croque : on retire les effets mais on garde les pouvoirs. */
    public void onCroqueQuit(Player p) {
        for (Power pw : active.values()) pw.onRevoke(p, pctx);
        // Le déguisement doit persister pendant l'absence (affichage tab/nametag).
        if (active.containsKey("disguise")) pctx.colors().setDisguised(p.getUniqueId(), true);
    }

    /**
     * Reconnexion du Croque, ou reprise après redémarrage serveur (les instances sont alors
     * reconstruites depuis GameState sans rejouer onChosen, pour ne pas redonner du temps).
     */
    public void onCroqueJoin(Player p) {
        if (active.isEmpty()) {
            List<String> ids = new ArrayList<>();
            if (ctx.config().powerInherit) ids.addAll(state().acquiredPowers());
            else if (state().croquePower() != null) ids.add(state().croquePower());
            for (String id : ids) {
                Power pw = registry.create(id);
                if (pw != null) active.put(id, pw);
            }
            if (state().croquePower() == null && roulette == null) {
                // Redémarrage pendant la roulette : on la relance.
                for (Power pw : active.values()) pw.onGrant(p, pctx);
                offerNewPower();
                return;
            }
        }
        for (Power pw : active.values()) pw.onGrant(p, pctx);
        if (roulette != null) roulette.open(p);
    }

    /** Coup autorisé du Croque sur un joueur (pouvoir Morsure). */
    public void onHit(UUID attacker, Player victim) {
        if (active.get("bite") instanceof BitePower bite && state().isCroque(attacker)) bite.onHit(victim, pctx);
    }

    // ---- Événements ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String id = pctx.powerIdOf(e.getItem());
        if (id == null) return;
        e.setCancelled(true);
        Power pw = active.get(id);
        if (pw != null && state().isCroque(e.getPlayer().getUniqueId())) pw.onUse(e.getPlayer(), pctx);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (pctx.isPowerItem(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof PowerRoulette r) {
            e.setCancelled(true);
            if (r == roulette && e.getRawSlot() < 9 && e.getWhoClicked() instanceof Player p
                    && state().isCroque(p.getUniqueId())) {
                String id = r.optionAt(e.getRawSlot());
                if (id != null) choose(id);
            }
            return;
        }
        // Item de pouvoir : interdit de le sortir de l'inventaire du joueur.
        if (e.getView().getTopInventory().getType() == InventoryType.CRAFTING) return;
        ItemStack hotbar = e.getHotbarButton() >= 0 ? e.getWhoClicked().getInventory().getItem(e.getHotbarButton()) : null;
        if (pctx.isPowerItem(e.getCurrentItem()) || pctx.isPowerItem(e.getCursor()) || pctx.isPowerItem(hotbar)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof PowerRoulette) {
            e.setCancelled(true);
            return;
        }
        if (pctx.isPowerItem(e.getOldCursor()) && e.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        // Fermer la roulette sans choisir = choix aléatoire (§6.7).
        if (roulette != null && e.getInventory().getHolder() == roulette
                && e.getReason() == InventoryCloseEvent.Reason.PLAYER) {
            chooseRandom();
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(pctx::isPowerItem);
    }
}
