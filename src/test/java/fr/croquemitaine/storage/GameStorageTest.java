package fr.croquemitaine.storage;

import fr.croquemitaine.core.DeathRecord;
import fr.croquemitaine.core.GameState;
import fr.croquemitaine.core.Phase;
import fr.croquemitaine.core.PlayerRecord;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Une partie doit survivre à un redémarrage : aller-retour JSON complet. */
class GameStorageTest {
    @Test
    void roundTrip() {
        GameState s = new GameState();
        s.phase(Phase.RUNNING);
        s.round(3);
        s.roundRemaining(123);
        UUID u = UUID.randomUUID();
        PlayerRecord r = new PlayerRecord(u, "Bob", 2);
        r.deaths().add(new DeathRecord(1L, "world", 1, 64, -3, "fall", "Alice"));
        s.players().put(u, r);
        s.croque(u);
        s.croquePower("shadow");
        s.acquiredPowers().add("tracker");
        s.acquiredPowers().add("shadow");
        s.enchantingTable(new int[]{0, 70, 0});

        GameState back = GameStorage.fromJson(GameStorage.toJson(s));
        assertEquals(Phase.RUNNING, back.phase());
        assertEquals(3, back.round());
        assertEquals(123, back.roundRemaining());
        assertEquals(2, back.record(u).lives());
        assertEquals("Alice", back.record(u).deaths().getFirst().killerName());
        assertTrue(back.isCroque(u));
        assertEquals("shadow", back.croquePower());
        assertEquals(java.util.List.of("tracker", "shadow"), back.acquiredPowers());
        assertArrayEquals(new int[]{0, 70, 0}, back.enchantingTable());
    }
}
