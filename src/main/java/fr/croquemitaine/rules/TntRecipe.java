package fr.croquemitaine.rules;

import fr.croquemitaine.game.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;

/** TNT simplifiée : 1 poudre à canon + 4 sable, en plus de la recette vanilla. */
public final class TntRecipe {
    private final Keys keys;

    public TntRecipe(Keys keys) {
        this.keys = keys;
    }

    public void register(boolean enabled) {
        Bukkit.removeRecipe(keys.simpleTnt);
        if (!enabled) return;
        ShapelessRecipe r = new ShapelessRecipe(keys.simpleTnt, new ItemStack(Material.TNT));
        r.addIngredient(1, Material.GUNPOWDER);
        r.addIngredient(4, Material.SAND);
        Bukkit.addRecipe(r);
    }

    public void unregister() {
        Bukkit.removeRecipe(keys.simpleTnt);
    }
}
