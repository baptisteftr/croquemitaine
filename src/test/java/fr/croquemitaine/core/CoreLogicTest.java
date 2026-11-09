package fr.croquemitaine.core;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CoreLogicTest {

    @Test
    void categoryFromLives() {
        assertEquals(Category.PASSIVE, Category.fromLives(5));
        assertEquals(Category.PASSIVE, Category.fromLives(4));
        assertEquals(Category.HOSTILE, Category.fromLives(3));
        assertEquals(Category.HOSTILE, Category.fromLives(2));
        assertEquals(Category.ASSASSIN, Category.fromLives(1));
        assertEquals(Category.ELIMINATED, Category.fromLives(0));
    }

    @Test
    void roundDurationsDecreaseWithFloor() {
        RoundEngine e = new RoundEngine(20 * 60, 2 * 60, 6 * 60);
        assertEquals(20 * 60, e.durationSeconds(1));
        assertEquals(18 * 60, e.durationSeconds(2));
        assertEquals(6 * 60, e.durationSeconds(8));
        assertEquals(6 * 60, e.durationSeconds(50));
        assertFalse(e.hasCroque(1));
        assertTrue(e.hasCroque(2));
    }

    @Test
    void eligibility() {
        assertTrue(RoundEngine.isEligible(Category.PASSIVE));
        assertTrue(RoundEngine.isEligible(Category.HOSTILE));
        assertFalse(RoundEngine.isEligible(Category.ASSASSIN));
        assertFalse(RoundEngine.isEligible(Category.ELIMINATED));
        assertTrue(new RoundEngine(1, 1, 1).pickCroque(List.of(), new Random()).isEmpty());
    }

    @Test
    void mapSizing() {
        assertEquals(300, MapSizing.size(5, 5, 25, 300, 500));
        assertEquals(300, MapSizing.size(2, 5, 25, 300, 500));
        assertEquals(400, MapSizing.size(15, 5, 25, 300, 500));
        assertEquals(500, MapSizing.size(40, 5, 25, 300, 500));
    }

    private static PlayerRecord rec(int lives) {
        return new PlayerRecord(UUID.randomUUID(), "p", lives);
    }

    @Test
    void winCondition() {
        PlayerRecord a = rec(3), b = rec(0), c = rec(0);
        var r = WinCondition.evaluate(List.of(a, b, c));
        assertEquals(WinCondition.Kind.WINNER, r.kind());
        assertEquals(a.uuid(), r.winner());
        assertEquals(WinCondition.Kind.ASSASSINS_ONLY, WinCondition.evaluate(List.of(rec(1), rec(1), rec(0))).kind());
        assertEquals(WinCondition.Kind.NONE, WinCondition.evaluate(List.of(rec(1), rec(2))).kind());
        assertEquals(WinCondition.Kind.NO_SURVIVOR, WinCondition.evaluate(List.of(rec(0))).kind());
    }

    @Test
    void livesNeverNegative() {
        PlayerRecord r = rec(1);
        r.setLives(-3);
        assertEquals(0, r.lives());
    }

    @RepeatedTest(50)
    void weightedPickerDistinctAndSkipsZero() {
        Map<String, Integer> w = Map.of("a", 1, "b", 5, "c", 0, "d", 3);
        List<String> picked = WeightedPicker.pickDistinct(w, 2, new Random());
        assertEquals(2, picked.size());
        assertNotEquals(picked.get(0), picked.get(1));
        assertFalse(picked.contains("c"));
    }

    @Test
    void weightedPickerRespectsWeights() {
        Map<String, Integer> w = Map.of("rare", 1, "common", 99);
        Map<String, Integer> counts = new HashMap<>();
        Random rnd = new Random(42);
        for (int i = 0; i < 10_000; i++) counts.merge(WeightedPicker.pickDistinct(w, 1, rnd).getFirst(), 1, Integer::sum);
        assertTrue(counts.get("common") > 9_500);
        assertTrue(WeightedPicker.pickDistinct(Map.of("x", 0), 2, rnd).isEmpty());
    }
}
