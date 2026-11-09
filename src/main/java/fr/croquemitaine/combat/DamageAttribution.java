package fr.croquemitaine.combat;

import fr.croquemitaine.game.Keys;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Remonte d'un dégât au joueur responsable « de sa propre main » (§1.5) :
 * coup direct, projectile, TNT allumée par lui, mob issu de son œuf, animal apprivoisé.
 * Retourne null si aucun joueur n'est identifiable (environnement, dispenser, mob sauvage…).
 */
public final class DamageAttribution {
    private final Keys keys;

    public DamageAttribution(Keys keys) {
        this.keys = keys;
    }

    public UUID responsible(Entity damager, DamageSource source) {
        UUID u = fromEntity(damager, 0);
        if (u != null) return u;
        if (source != null) {
            Entity causing = source.getCausingEntity();
            if (causing != null && causing != damager) u = fromEntity(causing, 0);
            if (u == null) {
                Entity direct = source.getDirectEntity();
                if (direct != null && direct != damager) u = fromEntity(direct, 0);
            }
        }
        return u;
    }

    /** depth évite les boucles (TNT allumée par une flèche tirée par…). */
    public UUID fromEntity(Entity e, int depth) {
        if (e == null || depth > 4) return null;
        if (e instanceof Player p) return p.getUniqueId();
        if (e instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            return fromEntity(shooter, depth + 1);
        }
        if (e instanceof TNTPrimed tnt) return fromEntity(tnt.getSource(), depth + 1);
        if (e instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Entity src) {
            return fromEntity(src, depth + 1);
        }
        String owner = e.getPersistentDataContainer().get(keys.eggOwner, PersistentDataType.STRING);
        if (owner != null) {
            try {
                return UUID.fromString(owner);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        if (e instanceof Tameable t && t.isTamed()) return t.getOwnerUniqueId();
        return null;
    }
}
