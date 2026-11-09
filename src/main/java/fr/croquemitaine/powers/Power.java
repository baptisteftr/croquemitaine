package fr.croquemitaine.powers;

import org.bukkit.entity.Player;

/**
 * Pouvoir du Croquemitaine. Une nouvelle instance est créée à chaque mission,
 * donc les recharges / usages uniques sont naturellement remis à zéro.
 */
public interface Power {
    String id();

    /** Appliqué une seule fois au moment du choix (ex. Sursis ajoute du temps). */
    default void onChosen(Player croque, PowerContext ctx) {
    }

    /** Appliquer effets / donner l'item. Rappelé à chaque reconnexion du Croque. */
    void onGrant(Player croque, PowerContext ctx);

    /** TOUT nettoyer. Doit être idempotent (appelé en fin de mission, déconnexion, arrêt…). */
    void onRevoke(Player croque, PowerContext ctx);

    /** Pouvoirs actifs : clic droit avec l'item dédié. */
    default void onUse(Player croque, PowerContext ctx) {
    }

    /** Appelé chaque seconde tant que le Croque est connecté. */
    default void onTick(Player croque, PowerContext ctx) {
    }

    /** Un pouvoir actif donne un item dédié. */
    default boolean active() {
        return false;
    }
}
