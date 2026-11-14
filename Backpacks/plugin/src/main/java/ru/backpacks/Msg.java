package ru.backpacks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public final class Msg {
    /** Bump when bundled messages change; stale files are backed up and replaced. */
    private static final int MESSAGES_VERSION = 6;

    private final BackpacksPlugin plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private YamlConfiguration config;
    private boolean russian;

    public Msg(BackpacksPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (file.exists() && messagesVersion(file) < bundledVersion()) {
            backup(file);
            if (!file.delete()) {
                plugin.getLogger().warning("Could not replace the outdated messages.yml");
            }
        }
        if (!file.exists()) {
            plugin.saveResource("messages.yml", true);
        }
        config = YamlConfiguration.loadConfiguration(file);
        // Keys added by a plugin update still resolve when an old messages.yml is installed.
        try (InputStream stream = plugin.getResource("messages.yml")) {
            if (stream != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)));
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not read bundled messages.yml: " + exception.getMessage());
        }
        russian = !"en".equalsIgnoreCase(plugin.getConfig().getString("language", "ru"));
    }

    private int bundledVersion() {
        try (InputStream stream = plugin.getResource("messages.yml")) {
            if (stream == null) {
                return 0;
            }
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getInt("messages-version", 0);
        } catch (IOException exception) {
            return 0;
        }
    }

    private static int messagesVersion(File file) {
        return YamlConfiguration.loadConfiguration(file).getInt("messages-version", 0);
    }

    private void backup(File file) {
        File backup = new File(file.getParentFile(),
                "messages.yml." + System.currentTimeMillis() + ".bak");
        try {
            Files.copy(file.toPath(), backup.toPath());
            plugin.getLogger().info("Outdated messages.yml backed up to " + backup.getName());
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not back up messages.yml: " + exception.getMessage());
        }
    }

    public boolean russian() {
        return russian;
    }

    public String raw(String key, Object... placeholders) {
        String value = config.getString((russian ? "ru." : "en.") + key);
        if (value == null) {
            value = config.getString("en." + key);
        }
        if (value == null) {
            value = key;
        }
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            value = value.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return value;
    }

    public List<String> rawList(String key) {
        List<String> list = config.getStringList((russian ? "ru." : "en.") + key);
        if (list.isEmpty()) {
            list = config.getStringList("en." + key);
        }
        return list;
    }

    public Component component(String key, Object... placeholders) {
        return mini.deserialize(raw(key, placeholders));
    }

    public Component prefixed(String key, Object... placeholders) {
        return component("prefix").append(component(key, placeholders));
    }

    public void send(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(prefixed(key, placeholders));
    }

    public void sendRaw(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(component(key, placeholders));
    }

    public void sendList(CommandSender sender, String key) {
        for (String line : rawList(key)) {
            sender.sendMessage(mini.deserialize(line));
        }
    }
}
