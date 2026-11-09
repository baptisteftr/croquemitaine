package fr.croquemitaine.core;

/**
 * Matrice PvP (§1.4). Règle de base : tout coup est refusé sauf autorisation explicite.
 */
public final class PvpRules {
    private final double defenseRadius;

    public PvpRules(double defenseRadius) {
        this.defenseRadius = defenseRadius;
    }

    public boolean canAttack(Combatant attacker, Combatant victim, double distance) {
        Category a = attacker.category();
        Category v = victim.category();
        if (!a.isAlive() || !v.isAlive()) return false;

        // Les droits d'attaque du Croque sont exclusivement ceux de la ligne « Croquemitaine ».
        if (attacker.croque()) {
            return v == Category.PASSIVE || v == Category.HOSTILE;
        }

        // Le Croque garde sa catégorie de base pour être attaqué : on autorise si
        // la colonne de sa catégorie OU la colonne « Croquemitaine » l'autorise.
        boolean byCategory = allowedAgainstCategory(a, v, distance);
        if (victim.croque()) {
            return byCategory || allowedAgainstCroque(a, distance);
        }
        return byCategory;
    }

    private boolean allowedAgainstCategory(Category a, Category v, double distance) {
        return switch (a) {
            case ASSASSIN -> true;
            case HOSTILE -> v == Category.PASSIVE || (v == Category.ASSASSIN && distance <= defenseRadius);
            case PASSIVE -> v == Category.ASSASSIN && distance <= defenseRadius;
            case ELIMINATED -> false;
        };
    }

    private boolean allowedAgainstCroque(Category a, double distance) {
        return switch (a) {
            case ASSASSIN -> true;
            case PASSIVE, HOSTILE -> distance <= defenseRadius;
            case ELIMINATED -> false;
        };
    }
}
