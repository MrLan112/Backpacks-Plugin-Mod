package ru.backpacks;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers one shapeless recipe per tier so they appear in the vanilla recipe book.
 * Crafting itself is still handled manually by {@link CraftListener}: it preserves the
 * backpack UUID (and therefore the contents) and allows extra materials in the grid.
 *
 * <p>The previous-tier ingredient is a predicate choice: the recipe book shows its
 * preview stack, while the server accepts any backpack of that tier.</p>
 */
public final class BackpackRecipes {
    private final BackpacksPlugin plugin;
    private final List<NamespacedKey> keys = new ArrayList<>();

    public BackpackRecipes(BackpacksPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        for (Tier tier : Tier.values()) {
            NamespacedKey key = new NamespacedKey(plugin, "craft_" + tier.id());
            ShapelessRecipe recipe = new ShapelessRecipe(key, plugin.items().preview(tier));
            recipe.setCategory(CraftingBookCategory.EQUIPMENT);
            recipe.setGroup("backpacks");

            for (Ingredient ingredient : tier.ingredients()) {
                recipe.addIngredient(ingredient.amount(), ingredient.material());
            }

            Tier previous = tier.previous();
            if (previous != null) {
                ItemStack icon = plugin.items().preview(previous);
                recipe.addIngredient(RecipeChoice.predicateChoice(
                        stack -> plugin.items().tierOf(stack) == previous, icon));
            }

            if (Bukkit.addRecipe(recipe)) {
                keys.add(key);
            } else {
                plugin.getLogger().warning("Could not register recipe " + key);
            }
        }
    }

    public void unregister() {
        for (NamespacedKey key : keys) {
            Bukkit.removeRecipe(key);
        }
        keys.clear();
    }

    public List<NamespacedKey> keys() {
        return List.copyOf(keys);
    }

    /** Shows every backpack recipe in the player's recipe book. */
    public void discover(Player player) {
        for (NamespacedKey key : keys) {
            player.discoverRecipe(key);
        }
    }
}
