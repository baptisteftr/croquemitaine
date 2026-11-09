package fr.croquemitaine.powers;

import fr.croquemitaine.powers.impl.BitePower;
import fr.croquemitaine.powers.impl.BlindPower;
import fr.croquemitaine.powers.impl.DisguisePower;
import fr.croquemitaine.powers.impl.EffectPower;
import fr.croquemitaine.powers.impl.ReprievePower;
import fr.croquemitaine.powers.impl.RevealPower;
import fr.croquemitaine.powers.impl.SecondWindPower;
import fr.croquemitaine.powers.impl.ShadowPower;
import fr.croquemitaine.powers.impl.ShadowStepPower;
import fr.croquemitaine.powers.impl.TrackerPower;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Fabrique de pouvoirs. Ajouter un pouvoir = une classe + une ligne ici + une entrée en config
 * et dans messages.yml.
 */
public final class PowerRegistry {
    private record Entry(Supplier<Power> factory, Material icon) {
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public PowerRegistry() {
        register("tracker", TrackerPower::new, Material.COMPASS);
        register("shadow", ShadowPower::new, Material.ECHO_SHARD);
        register("predator", () -> new EffectPower("predator", PotionEffectType.SPEED, 1), Material.SUGAR);
        register("brute", () -> new EffectPower("brute", PotionEffectType.STRENGTH, 0), Material.BLAZE_POWDER);
        register("shell", () -> new EffectPower("shell", PotionEffectType.RESISTANCE, 0), Material.TURTLE_SCUTE);
        register("reprieve", ReprievePower::new, Material.CLOCK);
        register("disguise", DisguisePower::new, Material.LIME_DYE);
        register("second_wind", SecondWindPower::new, Material.TOTEM_OF_UNDYING);
        register("blind", BlindPower::new, Material.INK_SAC);
        register("shadow_step", ShadowStepPower::new, Material.ENDER_EYE);
        register("bite", BitePower::new, Material.SPIDER_EYE);
        register("reveal", RevealPower::new, Material.SPYGLASS);
    }

    private void register(String id, Supplier<Power> f, Material icon) {
        entries.put(id, new Entry(f, icon));
    }

    public boolean exists(String id) {
        return entries.containsKey(id);
    }

    public Power create(String id) {
        Entry e = entries.get(id);
        return e == null ? null : e.factory().get();
    }

    public Material icon(String id) {
        Entry e = entries.get(id);
        return e == null ? Material.PAPER : e.icon();
    }
}
