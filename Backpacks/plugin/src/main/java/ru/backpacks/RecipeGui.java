package ru.backpacks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class RecipeGui implements Listener {
    private final BackpacksPlugin plugin;

    public RecipeGui(BackpacksPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        RecipeHolder holder = new RecipeHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.msg().component("recipes-title"));
        holder.inventory = inventory;

        Tier[] tiers = Tier.values();
        int perRow = 9;
        int rows = (tiers.length + perRow - 1) / perRow;
        int startRow = Math.max(0, (3 - rows) / 2);
        for (int i = 0; i < tiers.length; i++) {
            int row = i / perRow;
            int column = i % perRow;
            int inRow = Math.min(perRow, tiers.length - row * perRow);
            int offset = (perRow - inRow + 1) / 2;
            inventory.setItem((startRow + row) * perRow + offset + column, icon(tiers[i]));
        }

        player.openInventory(inventory);
    }

    private ItemStack icon(Tier tier) {
        ItemStack stack = plugin.items().preview(tier);
        stack.editMeta(meta -> {
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.msg().component("recipe-tier",
                    "tier", tier.number(),
                    "total", Tier.values().length));
            lore.add(plugin.msg().component("recipe-capacity", "size", tier.capacity()));
            if (tier.stacksPerCell() > 1) {
                lore.add(plugin.msg().component("recipe-bulk", "stacks", tier.stacksPerCell()));
            }
            lore.add(Component.empty());
            if (tier.previous() == null) {
                lore.add(plugin.msg().component("recipe-none"));
            } else {
                lore.add(plugin.msg().component("recipe-previous",
                        "tier", tier.previous().name(plugin.msg().russian())));
            }
            lore.add(Component.empty());
            lore.add(plugin.msg().component("recipe-materials"));
            for (Ingredient ingredient : tier.ingredients()) {
                lore.add(line(ingredient));
            }
            meta.lore(lore);
        });
        return stack;
    }

    private Component line(Ingredient ingredient) {
        Material material = ingredient.material();
        String key = "item." + material.getKey().getNamespace() + "." + material.getKey().getKey();
        return Component.text("• ", NamedTextColor.DARK_GRAY)
                .append(Component.text(ingredient.amount() + "× ", NamedTextColor.WHITE))
                .append(Component.translatable(key));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof RecipeHolder) {
            event.setCancelled(true);
        }
    }

    private static final class RecipeHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
