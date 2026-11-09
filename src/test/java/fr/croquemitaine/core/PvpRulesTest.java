package fr.croquemitaine.core;

import org.junit.jupiter.api.Test;

import static fr.croquemitaine.core.Category.*;
import static org.junit.jupiter.api.Assertions.*;

/** Matrice §1.4 testée case par case, à 39 et 41 blocs pour les cases conditionnelles. */
class PvpRulesTest {
    private final PvpRules rules = new PvpRules(40);
    private static final double NEAR = 39, FAR = 41;

    private boolean can(Combatant a, Combatant v, double d) {
        return rules.canAttack(a, v, d);
    }

    private static Combatant c(Category cat) { return Combatant.of(cat); }
    private static Combatant croque(Category base) { return new Combatant(base, true); }

    @Test
    void passiveRow() {
        assertFalse(can(c(PASSIVE), c(PASSIVE), NEAR));
        assertFalse(can(c(PASSIVE), c(HOSTILE), NEAR));
        assertTrue(can(c(PASSIVE), c(ASSASSIN), NEAR));
        assertFalse(can(c(PASSIVE), c(ASSASSIN), FAR));
        assertTrue(can(c(PASSIVE), croque(PASSIVE), NEAR));
        assertFalse(can(c(PASSIVE), croque(PASSIVE), FAR));
        assertTrue(can(c(PASSIVE), croque(HOSTILE), 40)); // borne incluse
    }

    @Test
    void hostileRow() {
        assertTrue(can(c(HOSTILE), c(PASSIVE), FAR));
        assertFalse(can(c(HOSTILE), c(HOSTILE), NEAR));
        assertTrue(can(c(HOSTILE), c(ASSASSIN), NEAR));
        assertFalse(can(c(HOSTILE), c(ASSASSIN), FAR));
        assertTrue(can(c(HOSTILE), croque(HOSTILE), NEAR));
        assertFalse(can(c(HOSTILE), croque(HOSTILE), FAR));
        // Le Croque garde sa catégorie de base : un hostile frappe toujours un Croque passif.
        assertTrue(can(c(HOSTILE), croque(PASSIVE), FAR));
    }

    @Test
    void assassinRow() {
        for (Category v : new Category[]{PASSIVE, HOSTILE, ASSASSIN}) {
            assertTrue(can(c(ASSASSIN), c(v), 1000));
        }
        assertTrue(can(c(ASSASSIN), croque(PASSIVE), 1000));
    }

    @Test
    void croqueRow() {
        assertTrue(can(croque(PASSIVE), c(PASSIVE), 1000));
        assertTrue(can(croque(HOSTILE), c(HOSTILE), 1000));
        assertFalse(can(croque(PASSIVE), c(ASSASSIN), 1));
        // Un Croque hostile n'a PAS les droits de la ligne hostile contre un assassin proche.
        assertFalse(can(croque(HOSTILE), c(ASSASSIN), 1));
    }

    @Test
    void eliminatedNeverInvolved() {
        assertFalse(can(c(ELIMINATED), c(PASSIVE), 1));
        assertFalse(can(c(ASSASSIN), c(ELIMINATED), 1));
    }
}
