package fr.croquemitaine.rules;

import fr.croquemitaine.game.GameContext;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Casque interdit : on bloque les voies connues de pose dans l'emplacement tête, et un
 * balayage chaque seconde rattrape toutes les autres (nouvelles mécaniques, commandes…).
 */
public final class HelmetBanListener implements Listener {
    /** Slot brut de la tête dans la vue inventaire du joueur (vue CRAFTING). */
    private static final int HEAD_RAW_SLOT = 5;
    private final GameContext ctx;

    public HelmetBanListener(GameContext ctx) {
        this.ctx = ctx;
    }

    /** Joueurs actuellement empoisonnés par le plugin à cause de leur casque. */
    private final Set<UUID> poisoned = new HashSet<>();

    /** Mode « block » : on empêche l'équipement. En mode « poison », les événements ne bloquent rien. */
    private boolean on() {
        return ctx.config().helmetBan && !ctx.config().helmetPoison && ctx.state().isActive();
    }

    private static boolean headItem(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        Material m = it.getType();
        return m.getEquipmentSlot() == EquipmentSlot.HEAD;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!on() || !(e.getWhoClicked() instanceof Player p)) return;
        boolean ownInventory = e.getView().getTopInventory().getType() == InventoryType.CRAFTING;
        if (ownInventory && e.getRawSlot() == HEAD_RAW_SLOT && e.getAction() != InventoryAction.PICKUP_ALL
                && e.getAction() != InventoryAction.PICKUP_HALF && e.getAction() != InventoryAction.DROP_ALL_SLOT
                && e.getAction() != InventoryAction.DROP_ONE_SLOT) {
            deny(e, p);
            return;
        }
        // Shift-clic d'un item de tête : il irait directement sur la tête.
        if (ownInventory && e.isShiftClick() && headItem(e.getCurrentItem()) && e.getRawSlot() != HEAD_RAW_SLOT) {
            deny(e, p);
        }
    }

    private void deny(InventoryClickEvent e, Player p) {
        e.setCancelled(true);
        ctx.messages().send(p, "rules.helmet-banned");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!on()) return;
        if (e.getView().getTopInventory().getType() == InventoryType.CRAFTING && e.getRawSlots().contains(HEAD_RAW_SLOT)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent e) {
        if (!on() || !headItem(e.getItem())) return;
        // Clic droit = équipement direct. On n'empêche que l'équipement (poser une citrouille/tête
        // sur un bloc reste possible car l'item est consommé en bloc, pas porté).
        boolean placeableBlock = e.getItem().getType().isBlock() && e.hasBlock();
        if (e.getAction().isRightClick() && !placeableBlock) {
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            ctx.messages().send(e.getPlayer(), "rules.helmet-banned");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseArmorEvent e) {
        if (on() && e.getTargetEntity() instanceof Player && headItem(e.getItem())) e.setCancelled(true);
    }

    /**
     * Mode « poison » : le casque reste porté, mais Poison II est entretenu tant qu'il l'est
     * (durée courte renouvelée chaque seconde → l'effet cesse vite une fois le casque retiré).
     */
    private void poisonSweep() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack head = p.getInventory().getHelmet();
            boolean wearing = head != null && !head.getType().isAir() && p.getGameMode() != org.bukkit.GameMode.SPECTATOR;
            if (wearing) {
                if (poisoned.add(p.getUniqueId())) ctx.messages().send(p, "rules.helmet-poison");
                p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 50, 1, false, true, true));
            } else if (poisoned.remove(p.getUniqueId())) {
                removeOurPoison(p);
            }
        }
    }

    private void clearPoison() {
        for (UUID u : poisoned) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) removeOurPoison(p);
        }
        poisoned.clear();
    }

    /** Ne retire que NOTRE poison (niveau II, courte durée), pas celui d'une potion ennemie. */
    private static void removeOurPoison(Player p) {
        PotionEffect pe = p.getPotionEffect(PotionEffectType.POISON);
        if (pe != null && pe.getAmplifier() == 1 && pe.getDuration() <= 50) p.removePotionEffect(PotionEffectType.POISON);
    }

    /** Balayage périodique : tout ce qui est sur la tête retourne dans l'inventaire (ou au sol). */
    public void sweep() {
        if (ctx.config().helmetBan && ctx.config().helmetPoison && ctx.state().isActive()) {
            poisonSweep();
            return;
        }
        clearPoison();
        if (!on()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack head = p.getInventory().getHelmet();
            if (head == null || head.getType().isAir()) continue;
            p.getInventory().setHelmet(null);
            HashMap<Integer, ItemStack> left = p.getInventory().addItem(head);
            left.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
            ctx.messages().send(p, "rules.helmet-banned");
        }
    }
}
