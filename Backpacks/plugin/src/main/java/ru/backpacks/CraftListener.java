package ru.backpacks;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The upgrade chain is implemented manually: no recipes are registered, the result slot
 * is a preview and the actual crafting is done on {@link CraftItemEvent}.
 * The backpack UUID is preserved, so contents survive the upgrade.
 */
public final class CraftListener implements Listener {
    private final BackpacksPlugin plugin;

    public CraftListener(BackpacksPlugin plugin) {
        this.plugin = plugin;
    }

    private record Match(Tier tier, ItemStack backpack) {
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        Match match = match(inventory.getMatrix());
        if (match != null) {
            inventory.setResult(plugin.items().preview(match.tier()));
            return;
        }

        ItemStack result = inventory.getResult();
        if (result != null && plugin.items().isBackpack(result) && plugin.items().idOf(result) == null) {
            inventory.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        CraftingInventory inventory = event.getInventory();
        Match match = match(inventory.getMatrix());
        if (match == null) {
            return;
        }

        event.setCancelled(true);

        ItemStack result = match.backpack() == null
                ? plugin.items().createFor(player, match.tier())
                : plugin.items().upgrade(match.backpack(), match.tier(),
                        plugin.modBridge().displayKey(player));

        consume(inventory, match);
        deliver(player, result, event.getClick().isShiftClick());
        inventory.setResult(null);
        player.updateInventory();

        plugin.msg().send(player, "upgrade",
                "tier", match.tier().name(plugin.msg().russian()),
                "size", match.tier().capacity());
        player.playSound(player.getLocation(), "block.anvil.use", 0.8f, 1.2f);
    }

    /** Backpacks must not be auto-crafted by a Crafter block: UUIDs and contents are lost. */
    @EventHandler(ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (plugin.items().isBackpack(event.getResult())) {
            event.setCancelled(true);
        }
    }

    private void deliver(Player player, ItemStack result, boolean shift) {
        ItemStack cursor = player.getItemOnCursor();
        if (!shift && (cursor == null || cursor.getType().isAir())) {
            player.setItemOnCursor(result);
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(result);
        if (!leftover.isEmpty()) {
            plugin.msg().send(player, "craft-no-space");
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    private void consume(CraftingInventory inventory, Match match) {
        Map<Material, Integer> need = new EnumMap<>(Material.class);
        for (Ingredient ingredient : match.tier().ingredients()) {
            need.merge(ingredient.material(), ingredient.amount(), Integer::sum);
        }

        UUID backpackId = match.backpack() == null ? null : plugin.items().idOf(match.backpack());
        boolean backpackRemoved = match.backpack() == null;

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            if (!backpackRemoved && plugin.items().isBackpack(stack)
                    && Objects.equals(plugin.items().idOf(stack), backpackId)) {
                inventory.setItem(slot, null);
                backpackRemoved = true;
                continue;
            }

            int required = need.getOrDefault(stack.getType(), 0);
            if (required <= 0) {
                continue;
            }
            int take = Math.min(required, stack.getAmount());
            int left = stack.getAmount() - take;
            inventory.setItem(slot, left <= 0 ? null : stack.asQuantity(left));
            int remaining = required - take;
            if (remaining <= 0) {
                need.remove(stack.getType());
            } else {
                need.put(stack.getType(), remaining);
            }
        }
    }

    /**
     * The recipe is shapeless: exactly the tier ingredients (or more of the same materials,
     * the leftovers stay in the grid) plus, for every tier after the first, a single backpack
     * of the previous tier. Any unrelated item makes the match fail.
     *
     * @return matching upgrade, or {@code null}.
     */
    private Match match(ItemStack[] matrix) {
        ItemStack backpack = null;
        Map<Material, Integer> counts = new EnumMap<>(Material.class);

        for (ItemStack stack : matrix) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (plugin.items().isBackpack(stack)) {
                if (backpack != null) {
                    return null;
                }
                backpack = stack;
                continue;
            }
            counts.merge(stack.getType(), stack.getAmount(), Integer::sum);
        }

        Tier tier;
        if (backpack == null) {
            tier = Tier.values()[0];
        } else {
            Tier current = plugin.items().tierOf(backpack);
            // The grid receives the previous tier, the result is the next one.
            if (current == null || current.ordinal() + 1 >= Tier.values().length) {
                return null;
            }
            tier = Tier.values()[current.ordinal() + 1];
        }

        for (Material material : counts.keySet()) {
            boolean wanted = false;
            for (Ingredient ingredient : tier.ingredients()) {
                if (ingredient.material() == material) {
                    wanted = true;
                    break;
                }
            }
            if (!wanted) {
                return null;
            }
        }

        for (Ingredient ingredient : tier.ingredients()) {
            if (counts.getOrDefault(ingredient.material(), 0) < ingredient.amount()) {
                return null;
            }
        }
        return new Match(tier, backpack);
    }
}
