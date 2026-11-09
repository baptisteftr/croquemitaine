package fr.croquemitaine.combat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KillTrackerTest {
    @Test
    void creditWithinWindowOnly() {
        KillTracker t = new KillTracker(10);
        UUID a = UUID.randomUUID(), v = UUID.randomUUID();
        t.record(a, v, 1_000);
        assertEquals(a, t.killer(v, 10_999));
        assertNull(t.killer(v, 11_001));
    }

    @Test
    void selfDamageIgnoredAndLastHitWins() {
        KillTracker t = new KillTracker(10);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), v = UUID.randomUUID();
        t.record(v, v, 0);
        assertNull(t.killer(v, 1));
        t.record(a, v, 0);
        t.record(b, v, 500);
        assertEquals(b, t.killer(v, 600));
        t.clear(v);
        assertNull(t.killer(v, 600));
    }
}
