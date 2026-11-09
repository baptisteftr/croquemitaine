package fr.croquemitaine.core;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Évaluation de la fin de partie (§1.9). */
public final class WinCondition {

    public enum Kind { NONE, WINNER, NO_SURVIVOR, ASSASSINS_ONLY }

    public record Result(Kind kind, UUID winner) {
        static final Result NONE = new Result(Kind.NONE, null);
    }

    private WinCondition() {
    }

    public static Result evaluate(Collection<PlayerRecord> records) {
        List<PlayerRecord> alive = records.stream().filter(PlayerRecord::isAlive).toList();
        if (alive.isEmpty()) return new Result(Kind.NO_SURVIVOR, null);
        if (alive.size() == 1) return new Result(Kind.WINNER, alive.getFirst().uuid());
        boolean allAssassins = alive.stream().allMatch(r -> r.category() == Category.ASSASSIN);
        return allAssassins ? new Result(Kind.ASSASSINS_ONLY, null) : Result.NONE;
    }
}
