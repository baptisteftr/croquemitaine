package fr.croquemitaine.powers;

import fr.croquemitaine.config.GameConfig;
import fr.croquemitaine.config.Messages;
import fr.croquemitaine.game.GameContext;
import fr.croquemitaine.game.Keys;
import fr.croquemitaine.players.NameColorService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Ce dont un pouvoir a besoin pour agir sur le jeu, sans dépendre de GameManager. */
public final class PowerContext {
    private final GameContext game;
    private final Keys keys;
    private final NameColorService colors;
    private final Function<Player, List<Player>> targets;
    private final Consumer<Integer> addRoundSeconds;

    public PowerContext(GameContext game, Keys keys, NameColorService colors,
                        Function<Player, List<Player>> targets, Consumer<Integer> addRoundSeconds) {
        this.game = game;
        this.keys = keys;
        this.colors = colors;
        this.targets = targets;
        this.addRoundSeconds = addRoundSeconds;
    }

    public GameContext game() { return game; }
    public GameConfig config() { return game.config(); }
    public Messages messages() { return game.messages(); }
    public NameColorService colors() { return colors; }
    public Keys keys() { return keys; }

    /** Joueurs que le Croque a le droit de frapper (passifs/hostiles vivants, même monde), du plus proche au plus loin. */
    public List<Player> targetsByDistance(Player croque) {
        return targets.apply(croque);
    }

    public Player nearestTarget(Player croque, double maxDistance) {
        for (Player p : targetsByDistance(croque)) {
            return p.getLocation().distance(croque.getLocation()) <= maxDistance ? p : null;
        }
        return null;
    }

    public void addRoundSeconds(int s) {
        addRoundSeconds.accept(s);
    }

    /** Item de pouvoir marqué en PDC (non jetable, non déplaçable, retiré à la révocation). */
    public ItemStack powerItem(Material material, String powerId) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(messages().raw("powers.names." + powerId), NamedTextColor.DARK_RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(messages().get("powers.item-lore").decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(keys.powerItem, PersistentDataType.STRING, powerId);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPowerItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(keys.powerItem, PersistentDataType.STRING);
    }

    public String powerIdOf(ItemStack item) {
        if (!isPowerItem(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keys.powerItem, PersistentDataType.STRING);
    }

    public void giveItem(Player p, Material m, String id) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (id.equals(powerIdOf(it))) return; // déjà présent (reconnexion)
        }
        if (id.equals(powerIdOf(p.getItemOnCursor()))) return;
        p.getInventory().addItem(powerItem(m, id));
    }

    /** Retire tous les items de pouvoir de l'inventaire (et du curseur). */
    public void removeItems(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isPowerItem(contents[i])) p.getInventory().setItem(i, null);
        }
        if (isPowerItem(p.getItemOnCursor())) p.setItemOnCursor(null);
    }
}
