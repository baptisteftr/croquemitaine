package fr.croquemitaine.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Tirage pondéré sans remise (roulette de pouvoirs). Les poids ≤ 0 sont exclus. */
public final class WeightedPicker {
    private WeightedPicker() {
    }

    public static List<String> pickDistinct(Map<String, Integer> weights, int count, Random random) {
        Map<String, Integer> pool = new LinkedHashMap<>();
        weights.forEach((k, w) -> {
            if (w != null && w > 0) pool.put(k, w);
        });
        List<String> out = new ArrayList<>();
        while (out.size() < count && !pool.isEmpty()) {
            int total = pool.values().stream().mapToInt(Integer::intValue).sum();
            int r = random.nextInt(total);
            String chosen = null;
            for (var e : pool.entrySet()) {
                r -= e.getValue();
                if (r < 0) {
                    chosen = e.getKey();
                    break;
                }
            }
            out.add(chosen);
            pool.remove(chosen);
        }
        return out;
    }
}
