package fr.croquemitaine.voice;

import fr.croquemitaine.game.GameContext;
import org.bukkit.Bukkit;

/** Point d'entrée sans dépendance à l'API voicechat : vérifie la présence du plugin avant de charger le bridge. */
public final class VoiceChatLoader {
    private VoiceChatLoader() {
    }

    public static void tryLoad(GameContext ctx) {
        if (Bukkit.getPluginManager().getPlugin("voicechat") == null) {
            ctx.plugin().getLogger().info("Simple Voice Chat absent : intégration vocale désactivée.");
            return;
        }
        try {
            boolean ok = VoiceChatBridge.register(ctx);
            ctx.plugin().getLogger().info(ok ? "Intégration Simple Voice Chat active."
                    : "Service Simple Voice Chat introuvable : intégration vocale désactivée.");
        } catch (LinkageError e) {
            ctx.plugin().getLogger().warning("API Simple Voice Chat incompatible : " + e);
        }
    }
}
