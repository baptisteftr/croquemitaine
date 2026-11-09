package fr.croquemitaine.voice;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import fr.croquemitaine.core.Category;
import fr.croquemitaine.game.GameContext;
import org.bukkit.Bukkit;

import java.util.UUID;

/**
 * Intégration Simple Voice Chat. Cette classe n'est chargée QUE si le plugin « voicechat »
 * est présent (voir VoiceChatLoader), sinon ses imports provoqueraient un NoClassDefFoundError.
 */
public final class VoiceChatBridge implements VoicechatPlugin {
    private final GameContext ctx;

    public VoiceChatBridge(GameContext ctx) {
        this.ctx = ctx;
    }

    public static boolean register(GameContext ctx) {
        BukkitVoicechatService service = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) return false;
        service.registerPlugin(new VoiceChatBridge(ctx));
        return true;
    }

    @Override
    public String getPluginId() {
        return "croquemitaine";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
    }

    /** Éliminés muets pour les vivants (appelé hors thread principal : lecture seule de l'état). */
    private void onMicrophone(MicrophonePacketEvent e) {
        if (!ctx.config().muteEliminated || !ctx.state().isActive()) return;
        VoicechatConnection sender = e.getSenderConnection();
        if (sender == null) return;
        UUID u = sender.getPlayer().getUuid();
        try {
            if (ctx.state().category(u) == Category.ELIMINATED) e.cancel();
        } catch (RuntimeException ignored) {
            // Lecture concurrente de la map pendant une écriture : on laisse passer ce paquet.
        }
    }
}
