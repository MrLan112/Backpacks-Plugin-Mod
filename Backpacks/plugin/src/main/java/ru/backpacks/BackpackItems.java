package ru.backpacks;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the backpack item. A real custom item id cannot be registered by a Paper
 * plugin, so the item is an inert base item with a PDC tier tag and a custom item
 * model provided by the client mod.
 *
 * <p>The base material is a music disc: it has no vanilla crafting recipes and no
 * villager trades, so a backpack can never be consumed as a recipe ingredient.
 * Right-clicking it on a jukebox is intercepted by {@code PlayerListener} and opens
 * the backpack instead. Older builds used leather horse armour; such stacks are still
 * recognised and migrated on sight.</p>
 */
public final class BackpackItems {
    /** Inert base item, see the class comment. */
    public static final Material BASE_MATERIAL = Material.MUSIC_DISC_11;

    private final BackpacksPlugin plugin;
    private final NamespacedKey idKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey equippedKey;

    public BackpackItems(BackpacksPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "backpack_id");
        this.tierKey = new NamespacedKey(plugin, "backpack_tier");
        this.equippedKey = new NamespacedKey(plugin, "equipped");
    }

    public ItemStack create(Tier tier) {
        return create(tier, UUID.randomUUID());
    }

    public ItemStack create(Tier tier, UUID id) {
        return create(tier, id, null);
    }

    public ItemStack create(Tier tier, UUID id, String openKey) {
        ItemStack item = new ItemStack(BASE_MATERIAL);
        apply(item, tier, id, openKey);
        return item;
    }

    /** Creates a backpack whose lore shows the key actually bound by {@code player}. */
    public ItemStack createFor(Player player, Tier tier) {
        return create(tier, UUID.randomUUID(), plugin.modBridge().displayKey(player));
    }

    public ItemStack preview(Tier tier) {
        return create(tier, null, null);
    }

    public ItemStack upgrade(ItemStack old, Tier next, String openKey) {
        UUID id = ensureId(old);
        ItemStack item = new ItemStack(BASE_MATERIAL);
        apply(item, next, id, openKey);
        return item;
    }

    /**
     * Rewrites the visuals and lore of a backpack stack. {@code id == null} removes
     * the storage UUID (used by previews); {@code openKey == null} hides the key name.
     */
    public void apply(ItemStack item, Tier tier, UUID id, String openKey) {
        item.editMeta(meta -> {
            meta.displayName(plugin.msg().component("item-name",
                    "name", tier.name(plugin.msg().russian()),
                    "body", hex(tier.bodyColor()),
                    "accent", hex(tier.accentColor())));

            List<Component> lore = new ArrayList<>();
            lore.add(plugin.msg().component("item.lore-tier",
                    "tier", tier.number(),
                    "total", Tier.values().length));
            lore.add(plugin.msg().component("item.lore-capacity", "size", tier.capacity()));
            if (tier.stacksPerCell() > 1) {
                lore.add(plugin.msg().component("item.lore-bulk", "stacks", tier.stacksPerCell()));
            }
            lore.add(Component.empty());
            lore.add(plugin.msg().component("item.lore-open"));
            lore.add(plugin.msg().component("item.lore-equip"));
            lore.add(plugin.msg().component("item.lore-slot"));
            if (openKey == null || openKey.isBlank()) {
                lore.add(plugin.msg().component("item.lore-keybind-nokey"));
            } else {
                lore.add(plugin.msg().component("item.lore-keybind", "key", openKey));
            }
            lore.add(plugin.msg().component("item.lore-unequip"));
            meta.lore(lore);

            meta.setItemModel(new NamespacedKey(plugin, tier.modelKey()));
            meta.setCustomModelData(tier.modelData());
            meta.setMaxStackSize(1);
            meta.setRarity(tier.rarity());
            meta.setEnchantmentGlintOverride(tier.ordinal() >= 13);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

            meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.id());
            if (id == null) {
                meta.getPersistentDataContainer().remove(idKey);
            } else {
                meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id.toString());
            }
        });
    }

    /**
     * Old backpacks were leather horse armour. Returns a copy made of the current base
     * item for such stacks; any other stack (including real horse armour) is returned
     * unchanged.
     */
    public ItemStack migrate(ItemStack item, String openKey) {
        if (item == null || item.getType().isAir() || item.getType() == BASE_MATERIAL) {
            return item;
        }
        Tier tier = tierOf(item);
        if (tier == null) {
            return item;
        }
        ItemStack migrated = new ItemStack(BASE_MATERIAL, item.getAmount());
        apply(migrated, tier, idOf(item), openKey);
        return migrated;
    }

    public UUID idOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public UUID ensureId(ItemStack item) {
        UUID id = idOf(item);
        if (id != null) {
            return id;
        }
        id = UUID.randomUUID();
        UUID value = id;
        item.editMeta(meta -> meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, value.toString()));
        return id;
    }

    public Tier tierOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        return Tier.byId(raw);
    }

    public boolean isBackpack(ItemStack item) {
        return tierOf(item) != null;
    }

    /** The backpack stored on the player's back, or {@code null}. */
    public ItemStack equipped(Player player) {
        byte[] data = player.getPersistentDataContainer().get(equippedKey, PersistentDataType.BYTE_ARRAY);
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            ItemStack item = ItemStack.deserializeBytes(data);
            if (!isBackpack(item)) {
                return null;
            }
            ItemStack migrated = migrate(item, null);
            if (migrated != item) {
                setEquipped(player, migrated);
            }
            return migrated;
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not read equipped backpack of " + player.getName());
            return null;
        }
    }

    public void setEquipped(Player player, ItemStack item) {
        player.getPersistentDataContainer().set(equippedKey, PersistentDataType.BYTE_ARRAY, item.serializeAsBytes());
    }

    public void clearEquipped(Player player) {
        player.getPersistentDataContainer().remove(equippedKey);
    }

    /**
     * Migrates legacy stacks and rewrites the key line of every backpack the player
     * owns (inventory and the equipped one), so a rebound key is shown immediately.
     */
    public void refreshKeys(Player player) {
        String key = plugin.modBridge().displayKey(player);
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            Tier tier = tierOf(item);
            if (tier == null) {
                continue;
            }
            ItemStack updated = migrate(item, key);
            apply(updated, tier, idOf(updated), key);
            if (updated.getAmount() > 1) {
                // Legacy backpacks could stack and therefore shared one storage UUID.
                // Keep one item per slot/ID so separate backpacks never share contents.
                int amount = updated.getAmount();
                inventory.setItem(slot, updated.asQuantity(1));
                for (int copy = 1; copy < amount; copy++) {
                    plugin.gui().give(player, create(tier, UUID.randomUUID(), key));
                }
            } else {
                inventory.setItem(slot, updated);
            }
        }

        ItemStack worn = equipped(player);
        if (worn != null) {
            Tier tier = tierOf(worn);
            if (tier != null) {
                apply(worn, tier, idOf(worn), key);
                setEquipped(player, worn);
            }
        }
        player.updateInventory();
    }

    public static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }
}
