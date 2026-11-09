package fr.croquemitaine.game;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/** Clés PersistentDataContainer / recettes du plugin. */
public final class Keys {
    /** UUID du joueur ayant utilisé l'œuf dont est issu un mob. */
    public final NamespacedKey eggOwner;
    /** Marque un item de pouvoir (valeur = id du pouvoir). */
    public final NamespacedKey powerItem;
    public final NamespacedKey simpleTnt;

    public Keys(JavaPlugin plugin) {
        eggOwner = new NamespacedKey(plugin, "egg_owner");
        powerItem = new NamespacedKey(plugin, "power_item");
        simpleTnt = new NamespacedKey("croquemitaine", "simple_tnt");
    }
}
