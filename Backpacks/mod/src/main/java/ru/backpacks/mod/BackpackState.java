package ru.backpacks.mod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative view of which entity wears which backpack tier.
 * Filled by the {@code backpacks:sync} payload, keyed by entity id.
 */
public final class BackpackState {
    /** A worn backpack: tier number plus the two tier colours sent by the server. */
    public record Wear(int tier, int bodyColor, int accentColor) {
        public static final int WHITE = 0xFFFFFF;
    }

    private static final Map<Integer, Wear> WORN = new ConcurrentHashMap<>();

    private BackpackState() {
    }

    public static void set(int entityId, int tier, int bodyColor, int accentColor) {
        if (tier <= 0) {
            WORN.remove(entityId);
        } else {
            WORN.put(entityId, new Wear(tier,
                    bodyColor & Wear.WHITE,
                    accentColor & Wear.WHITE));
        }
    }

    public static Wear wearOf(int entityId) {
        return WORN.get(entityId);
    }

    public static int tierOf(int entityId) {
        Wear wear = WORN.get(entityId);
        return wear == null ? 0 : wear.tier();
    }

    public static void clear() {
        WORN.clear();
    }

    /**
     * Client-side icon for a tier: the mod resource pack ships
     * {@code backpacks:item/backpack_N} models, referenced by the item model component.
     */
    public static ItemStack icon(int tier) {
        if (tier <= 0) {
            return new ItemStack(Items.AIR);
        }
        ItemStack stack = new ItemStack(Items.MUSIC_DISC_11);
        stack.set(DataComponents.ITEM_MODEL,
                Identifier.fromNamespaceAndPath("backpacks", "backpack_" + tier));
        return stack;
    }
}
