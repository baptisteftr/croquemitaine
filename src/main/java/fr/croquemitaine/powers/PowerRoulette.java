package fr.croquemitaine.powers;

import fr.croquemitaine.config.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Inventaire de choix : 9 cases, les options aux cases 2 et 6. */
public final class PowerRoulette implements InventoryHolder {
    public static final int[] SLOTS = {2, 6};
    private final List<String> options;
    private final Inventory inventory;

    public PowerRoulette(List<String> options, Messages messages, PowerRegistry registry) {
        this.options = List.copyOf(options);
        this.inventory = Bukkit.createInventory(this, 9, messages.get("powers.roulette-title"));
        for (int i = 0; i < options.size() && i < SLOTS.length; i++) {
            String id = options.get(i);
            ItemStack it = new ItemStack(registry.icon(id));
            ItemMeta meta = it.getItemMeta();
            meta.displayName(Component.text(messages.raw("powers.names." + id), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text(messages.raw("powers.descriptions." + id), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            it.setItemMeta(meta);
            inventory.setItem(SLOTS[i], it);
        }
    }

    public List<String> options() {
        return options;
    }

    /** Option correspondant à une case cliquée, ou null. */
    public String optionAt(int slot) {
        for (int i = 0; i < SLOTS.length && i < options.size(); i++) {
            if (SLOTS[i] == slot) return options.get(i);
        }
        return null;
    }

    public void open(Player p) {
        p.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
