package fr.croquemitaine.config;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Accès aux textes de messages.yml. Les clés absentes du fichier utilisateur
 * retombent sur la version embarquée dans le jar (pas de message vide après une mise à jour).
 */
public final class Messages {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final JavaPlugin plugin;
    private YamlConfiguration yaml;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        yaml = YamlConfiguration.loadConfiguration(file);
        var stream = plugin.getResource("messages.yml");
        if (stream != null) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
    }

    public String raw(String key) {
        String s = yaml.getString(key);
        return s == null ? "<red>[message manquant: " + key + "]" : s;
    }

    public List<String> rawList(String key) {
        List<String> l = yaml.getStringList(key);
        return l.isEmpty() ? List.of(raw(key)) : l;
    }

    public Component get(String key, TagResolver... resolvers) {
        return MM.deserialize(raw(key), resolvers);
    }

    public List<Component> list(String key, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>();
        for (String s : rawList(key)) out.add(MM.deserialize(s, resolvers));
        return out;
    }

    /** Message préfixé, pour le chat. */
    public void send(Audience to, String key, TagResolver... resolvers) {
        to.sendMessage(MM.deserialize(raw("prefix")).append(get(key, resolvers)));
    }

    public static TagResolver p(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    public static TagResolver c(String name, Component value) {
        return Placeholder.component(name, value);
    }

    /** Format mm:ss (ou h:mm:ss) d'une durée en secondes. */
    public static String time(int seconds) {
        seconds = Math.max(0, seconds);
        int h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }
}
