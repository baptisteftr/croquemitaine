package fr.croquemitaine.core;

/**
 * Vue d'un joueur pour la matrice PvP : sa catégorie de base et s'il est
 * actuellement le Croquemitaine (mission en cours).
 */
public record Combatant(Category category, boolean croque) {
    public static Combatant of(Category category) {
        return new Combatant(category, false);
    }
}
