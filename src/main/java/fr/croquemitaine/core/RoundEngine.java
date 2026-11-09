package fr.croquemitaine.core;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/** Calculs purs liés aux manches : durée et tirage du Croquemitaine. */
public final class RoundEngine {
    private final int initialSeconds;
    private final int decrementSeconds;
    private final int minSeconds;

    public RoundEngine(int initialSeconds, int decrementSeconds, int minSeconds) {
        this.initialSeconds = initialSeconds;
        this.decrementSeconds = decrementSeconds;
        this.minSeconds = minSeconds;
    }

    /** max(min, initial − decrement × (N − 1)). */
    public int durationSeconds(int round) {
        long d = (long) initialSeconds - (long) decrementSeconds * (Math.max(1, round) - 1);
        return (int) Math.max(minSeconds, d);
    }

    /** La manche 1 n'a jamais de Croquemitaine. */
    public boolean hasCroque(int round) {
        return round >= 2;
    }

    /**
     * Tire au sort parmi les candidats déjà filtrés (connectés, passifs ou hostiles).
     * Vide si personne n'est éligible.
     */
    public Optional<UUID> pickCroque(List<UUID> eligible, Random random) {
        if (eligible.isEmpty()) return Optional.empty();
        return Optional.of(eligible.get(random.nextInt(eligible.size())));
    }

    /** Une catégorie peut-elle être désignée Croque ? (assassins et éliminés exclus) */
    public static boolean isEligible(Category c) {
        return c == Category.PASSIVE || c == Category.HOSTILE;
    }
}
