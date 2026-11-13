package ru.backpacks;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class BackpacksPlugin extends JavaPlugin {
    private Msg msg;
    private BackpackItems items;
    private BackpackStore store;
    private BackpackGuiManager gui;
    private RecipeGui recipes;
    private BackpackRecipes recipeBook;
    private ModBridge modBridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        msg = new Msg(this);
        msg.load();
        items = new BackpackItems(this);
        modBridge = new ModBridge(this);
        modBridge.register();

        store = new BackpackStore(this);
        if (!store.init()) {
            getLogger().severe("Backpack contents will NOT be saved. Check the SQLite driver and folder permissions.");
        }

        gui = new BackpackGuiManager(this);
        gui.start();
        recipes = new RecipeGui(this);
        recipeBook = new BackpackRecipes(this);
        recipeBook.register();

        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(recipes, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new CraftListener(this), this);

        PluginCommand command = getCommand("backpack");
        if (command != null) {
            BpCommand executor = new BpCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("Enabled with " + Tier.values().length + " tiers.");
    }

    @Override
    public void onDisable() {
        if (recipeBook != null) {
            recipeBook.unregister();
        }
        if (modBridge != null) {
            modBridge.unregister();
        }
        if (gui != null) {
            gui.saveAllSync();
        }
        if (store != null) {
            store.close();
        }
    }

    public void reloadAll() {
        reloadConfig();
        msg.load();
        if (gui != null) {
            gui.reload();
        }
        // Rewrites item lore so a language or key change is visible immediately.
        if (items != null) {
            for (Player player : getServer().getOnlinePlayers()) {
                items.refreshKeys(player);
            }
        }
    }

    public Msg msg() {
        return msg;
    }

    public BackpackItems items() {
        return items;
    }

    public BackpackStore store() {
        return store;
    }

    public BackpackGuiManager gui() {
        return gui;
    }

    public RecipeGui recipes() {
        return recipes;
    }

    public BackpackRecipes recipeBook() {
        return recipeBook;
    }

    public ModBridge modBridge() {
        return modBridge;
    }
}
