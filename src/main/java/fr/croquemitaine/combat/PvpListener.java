package fr.croquemitaine.combat;

import fr.croquemitaine.core.Category;
import fr.croquemitaine.core.Combatant;
import fr.croquemitaine.core.GameState;
import fr.croquemitaine.core.PvpRules;
import fr.croquemitaine.game.GameContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Applique la matrice PvP sur tous les dégâts d'origine joueur visant un joueur. */
public final class PvpListener implements Listener {
    private final GameContext ctx;
    private final DamageAttribution attribution;
    private final KillTracker tracker;
    /** Appelé après un coup autorisé et effectif (sert au pouvoir « Morsure »). */
    private final BiConsumer<UUID, Player> onAllowedHit;
    private PvpRules rules;

    public PvpListener(GameContext ctx, DamageAttribution attribution, KillTracker tracker, BiConsumer<UUID, Player> onAllowedHit) {
        this.ctx = ctx;
        this.attribution = attribution;
        this.tracker = tracker;
        this.onAllowedHit = onAllowedHit;
        reloadRules();
    }

    public void reloadRules() {
        rules = new PvpRules(ctx.config().defenseRadius);
        tracker.setWindowSeconds(ctx.config().killCreditSeconds);
    }

    private Combatant combatant(UUID u) {
        GameState s = ctx.state();
        Category c = s.category(u);
        return c == null ? null : new Combatant(c, s.isCroque(u));
    }

    /**
     * Décision centrale. attackerPos sert au calcul de distance (position du joueur responsable
     * s'il est connecté, sinon celle de l'entité qui frappe).
     */
    public boolean allowed(UUID attacker, Player victim, Location fallbackPos) {
        Combatant a = combatant(attacker);
        Combatant v = combatant(victim.getUniqueId());
        if (a == null || v == null) return a == null && v == null; // non-inscrits entre eux : vanilla
        Player ap = Bukkit.getPlayer(attacker);
        Location from = ap != null ? ap.getLocation() : fallbackPos;
        double dist = from != null && from.getWorld() == victim.getWorld()
                ? from.distance(victim.getLocation()) : Double.MAX_VALUE;
        return rules.canAttack(a, v, dist);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        // Sans application technique, la matrice est une règle d'honneur : on ne bloque rien.
        if (!ctx.config().pvpEnforce) return;
        if (!ctx.state().isActive() || !(e.getEntity() instanceof Player victim)) return;
        UUID attacker = attribution.responsible(e.getDamager(), e.getDamageSource());
        if (attacker == null || attacker.equals(victim.getUniqueId())) return;
        if (!allowed(attacker, victim, e.getDamager().getLocation())) e.setCancelled(true);
    }

    /** Enregistrement après coup, une fois sûr que le dégât passe. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageMonitor(EntityDamageByEntityEvent e) {
        if (!ctx.state().isActive() || !(e.getEntity() instanceof Player victim)) return;
        UUID attacker = attribution.responsible(e.getDamager(), e.getDamageSource());
        if (attacker == null || attacker.equals(victim.getUniqueId())) return;
        tracker.record(attacker, victim.getUniqueId(), System.currentTimeMillis());
        onAllowedHit.accept(attacker, victim);
    }

    /** Un éliminé ne subit plus aucun dégât (il est dans la boîte). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent e) {
        if (!ctx.state().isActive() || !(e.getEntity() instanceof Player p)) return;
        if (ctx.state().category(p.getUniqueId()) == Category.ELIMINATED) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent e) {
        if (!ctx.config().pvpEnforce || !ctx.state().isActive()) return;
        ThrownPotion potion = e.getPotion();
        if (!hasHarmful(potion.getEffects())) return;
        UUID attacker = attribution.fromEntity(potion, 0);
        if (attacker == null) return;
        for (LivingEntity le : e.getAffectedEntities()) {
            if (le instanceof Player victim && !victim.getUniqueId().equals(attacker)
                    && !allowed(attacker, victim, potion.getLocation())) {
                e.setIntensity(le, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent e) {
        if (!ctx.config().pvpEnforce || !ctx.state().isActive()) return;
        AreaEffectCloud cloud = e.getEntity();
        List<PotionEffect> effects = new ArrayList<>(cloud.getCustomEffects());
        if (cloud.getBasePotionType() != null) effects.addAll(cloud.getBasePotionType().getPotionEffects());
        if (!hasHarmful(effects)) return;
        UUID attacker = attribution.fromEntity(cloud, 0);
        if (attacker == null) return;
        e.getAffectedEntities().removeIf(le -> le instanceof Player victim
                && !victim.getUniqueId().equals(attacker)
                && !allowed(attacker, victim, cloud.getLocation()));
    }

    private static boolean hasHarmful(Collection<PotionEffect> effects) {
        for (PotionEffect pe : effects) {
            if (pe.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL) return true;
        }
        return false;
    }

    /** Pour les autres services : l'entité est-elle un attaquant attribuable ? */
    public UUID responsible(Entity e) {
        return attribution.fromEntity(e, 0);
    }
}
