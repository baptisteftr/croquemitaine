package fr.croquemitaine.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.croquemitaine.core.GameState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Sauvegarde JSON de GameState (écriture atomique pour ne jamais laisser un fichier tronqué). */
public final class GameStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final Logger log;

    public GameStorage(Path dataFolder, Logger log) {
        this.file = dataFolder.resolve("game.json");
        this.log = log;
    }

    public void save(GameState state) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("game.json.tmp");
            Files.writeString(tmp, GSON.toJson(state), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Impossible de sauvegarder game.json", e);
        }
    }

    public GameState load() {
        if (!Files.exists(file)) return new GameState();
        try {
            GameState s = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), GameState.class);
            return s == null ? new GameState() : s;
        } catch (Exception e) {
            log.log(Level.SEVERE, "game.json illisible, partie ignorée", e);
            return new GameState();
        }
    }

    /** Sérialisation aller-retour (tests). */
    public static String toJson(GameState s) {
        return GSON.toJson(s);
    }

    public static GameState fromJson(String json) {
        return GSON.fromJson(json, GameState.class);
    }
}
