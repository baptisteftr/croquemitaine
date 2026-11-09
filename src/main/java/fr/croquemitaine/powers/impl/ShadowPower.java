package fr.croquemitaine.powers.impl;

import fr.croquemitaine.powers.PowerContext;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Ombre : invisibilité temporaire. L'effet vanilla laisse voir l'armure et l'item en main,
 * on envoie donc aux autres joueurs un équipement vide tant que l'ombre dure.
 */
public final class ShadowPower extends CooldownPower {
    private static final EquipmentSlot[] HIDDEN = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND};
    private long hiddenUntil;

    public ShadowPower() {
        super(Material.ECHO_SHARD);
    }

    @Override public String id() { return "shadow"; }

    @Override
    protected int cooldownSeconds(PowerContext ctx) {
        return ctx.config().shadowCooldown;
    }

    @Override
    protected boolean use(Player croque, PowerContext ctx) {
        int d = ctx.config().shadowDuration;
        croque.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, d * 20, 0, false, false, true));
        hiddenUntil = System.currentTimeMillis() + d * 1000L;
        sendEquipment(croque, true);
        return true;
    }

    @Override
    public void onTick(Player croque, PowerContext ctx) {
        super.onTick(croque, ctx);
        if (hiddenUntil == 0) return;
        if (System.currentTimeMillis() >= hiddenUntil || !croque.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            hiddenUntil = 0;
            sendEquipment(croque, false);
        } else {
            // Ré-envoi régulier : les mises à jour d'équipement vanilla écraseraient le masquage.
            sendEquipment(croque, true);
        }
    }

    @Override
    public void onRevoke(Player croque, PowerContext ctx) {
        super.onRevoke(croque, ctx);
        if (hiddenUntil != 0) {
            hiddenUntil = 0;
            croque.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
        sendEquipment(croque, false);
    }

    private void sendEquipment(Player croque, boolean hide) {
        Map<EquipmentSlot, ItemStack> eq = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot s : HIDDEN) {
            eq.put(s, hide ? ItemStack.empty() : croque.getInventory().getItem(s));
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != croque) other.sendEquipmentChange(croque, eq);
        }
    }
}
