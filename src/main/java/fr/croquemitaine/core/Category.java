package fr.croquemitaine.core;

/**
 * Catégorie d'un joueur, toujours DÉRIVÉE du nombre de vies (jamais stockée),
 * pour qu'il n'existe qu'une seule source de vérité : les vies.
 */
public enum Category {
    PASSIVE,
    HOSTILE,
    ASSASSIN,
    ELIMINATED;

    public static Category fromLives(int lives) {
        if (lives <= 0) return ELIMINATED;
        if (lives == 1) return ASSASSIN;
        if (lives <= 3) return HOSTILE;
        return PASSIVE;
    }

    public boolean isAlive() {
        return this != ELIMINATED;
    }
}
