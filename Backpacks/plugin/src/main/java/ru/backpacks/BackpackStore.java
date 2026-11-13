package ru.backpacks;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * SQLite storage. Reads happen on the server thread (a local SQLite read is fast enough
 * for opening a GUI), writes are queued on a single background thread.
 */
public final class BackpackStore {
    private final BackpacksPlugin plugin;
    private final Object lock = new Object();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Backpacks-SQLite");
        thread.setDaemon(true);
        return thread;
    });

    private Connection connection;

    public BackpackStore(BackpacksPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        try {
            Class.forName("org.sqlite.JDBC");
            String fileName = plugin.getConfig().getString("storage.file", "backpacks.db");
            File file = new File(plugin.getDataFolder(), fileName);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create directory " + parent.getAbsolutePath());
            }

            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            synchronized (lock) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA journal_mode=WAL");
                    // FULL makes each commit durable even across a power loss; saves are
                    // debounced and asynchronous, so the cost is negligible.
                    statement.execute("PRAGMA synchronous=FULL");
                    statement.execute("""
                            CREATE TABLE IF NOT EXISTS backpacks (
                                id TEXT PRIMARY KEY,
                                tier TEXT NOT NULL,
                                owner TEXT,
                                data BLOB NOT NULL,
                                updated INTEGER NOT NULL
                            )
                            """);
                }
            }
            return true;
        } catch (Exception exception) {
            plugin.getLogger().severe("Could not open backpack database: " + exception.getMessage());
            exception.printStackTrace();
            return false;
        }
    }

    public boolean ready() {
        return connection != null;
    }

    /**
     * Outcome of a contents lookup. A read failure must never be mistaken for an empty
     * backpack: opening the GUI empty and then saving would overwrite stored items.
     */
    public record Contents(byte[] data, boolean failed) {
        public static final Contents MISSING = new Contents(null, false);
        public static final Contents FAILED = new Contents(null, true);
    }

    /** @return stored contents, {@link Contents#MISSING} or {@link Contents#FAILED}. */
    public Contents loadContents(UUID id) {
        if (connection == null) {
            return Contents.FAILED;
        }
        synchronized (lock) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT data FROM backpacks WHERE id = ?")) {
                statement.setString(1, id.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        byte[] data = result.getBytes("data");
                        return data == null ? Contents.MISSING : new Contents(data, false);
                    }
                    return Contents.MISSING;
                }
            } catch (SQLException exception) {
                plugin.getLogger().severe("Could not read backpack " + id + ": " + exception.getMessage());
                return Contents.FAILED;
            }
        }
    }

    public void save(UUID id, Tier tier, UUID owner, byte[] data) {
        if (connection == null) {
            return;
        }
        byte[] copy = data.clone();
        writer.execute(() -> saveNow(id, tier, owner, copy));
    }

    public void saveNow(UUID id, Tier tier, UUID owner, byte[] data) {
        if (connection == null) {
            return;
        }
        synchronized (lock) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO backpacks (id, tier, owner, data, updated) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET tier = excluded.tier, owner = excluded.owner,
                                                  data = excluded.data, updated = excluded.updated
                    """)) {
                statement.setString(1, id.toString());
                statement.setString(2, tier.id());
                statement.setString(3, owner == null ? null : owner.toString());
                statement.setBytes(4, data);
                statement.setLong(5, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().severe("Could not save backpack " + id + ": " + exception.getMessage());
            }
        }
    }

    public void delete(UUID id) {
        if (connection == null) {
            return;
        }
        writer.execute(() -> deleteNow(id));
    }

    public void deleteNow(UUID id) {
        if (connection == null) {
            return;
        }
        synchronized (lock) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM backpacks WHERE id = ?")) {
                statement.setString(1, id.toString());
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().severe("Could not delete backpack " + id + ": " + exception.getMessage());
            }
        }
    }

    /**
     * Waits until every queued write is done.
     *
     * @return {@code false} when the queue could not be drained in time.
     */
    public boolean flush(long timeoutMillis) {
        if (connection == null) {
            return false;
        }
        try {
            writer.submit(() -> {
            }).get(timeoutMillis, TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    /** Waits until every queued write is done. */
    public void flush() {
        if (connection == null) {
            return;
        }
        try {
            writer.submit(() -> {
            }).get(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // shutting down anyway
        }
    }

    public void close() {
        flush();
        writer.shutdown();
        synchronized (lock) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException exception) {
                    plugin.getLogger().warning("Could not close backpack database: " + exception.getMessage());
                }
                connection = null;
            }
        }
    }
}
