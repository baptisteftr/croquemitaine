package fr.croquemitaine.game;

import fr.croquemitaine.config.GameConfig;
import fr.croquemitaine.config.Messages;
import fr.croquemitaine.core.GameState;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Contexte partagé injecté dans les services : état courant, config (rechargeable) et messages.
 * L'état et la config sont remplaçables (chargement / reload), d'où les accesseurs plutôt que des champs finaux.
 */
public final class GameContext {
    private final JavaPlugin plugin;
    private final Messages messages;
    private GameConfig config;
    private GameState state = new GameState();

    public GameContext(JavaPlugin plugin, GameConfig config, Messages messages) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
    }

    public JavaPlugin plugin() { return plugin; }
    public Messages messages() { return messages; }
    public GameConfig config() { return config; }
    public void config(GameConfig c) { config = c; }
    public GameState state() { return state; }
    public void state(GameState s) { state = s; }

    /** Monde de jeu : celui mémorisé dans la partie, sinon celui de la config, sinon le premier. */
    public World world() {
        String name = state.world() != null ? state.world() : config.world;
        World w = Bukkit.getWorld(name);
        return w != null ? w : Bukkit.getWorlds().getFirst();
    }
}
