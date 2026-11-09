package fr.croquemitaine.players;

import fr.croquemitaine.core.PlayerRecord;
import fr.croquemitaine.game.GameContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Unique point de modification des vies. Notifie les autres services (couleurs, élimination,
 * voice, condition de victoire) via LivesListener, y compris pour les joueurs déconnectés.
 */
public final class LivesService {

    @FunctionalInterface
    public interface LivesListener {
        void onLivesChanged(UUID player, int oldLives, int newLives);
    }

    private final GameContext ctx;
    private final List<LivesListener> listeners = new ArrayList<>();

    public LivesService(GameContext ctx) {
        this.ctx = ctx;
    }

    public void addListener(LivesListener l) {
        listeners.add(l);
    }

    public int lives(UUID u) {
        PlayerRecord r = ctx.state().record(u);
        return r == null ? 0 : r.lives();
    }

    public void set(UUID u, int lives) {
        PlayerRecord r = ctx.state().record(u);
        if (r == null) return;
        int old = r.setLives(lives);
        if (old != r.lives()) {
            for (LivesListener l : List.copyOf(listeners)) l.onLivesChanged(u, old, r.lives());
        }
    }

    public void add(UUID u, int delta) {
        set(u, lives(u) + delta);
    }

    public void removeOne(UUID u) {
        add(u, -1);
    }
}
