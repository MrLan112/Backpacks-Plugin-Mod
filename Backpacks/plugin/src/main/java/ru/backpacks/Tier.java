package ru.backpacks;

import org.bukkit.Material;
import org.bukkit.inventory.ItemRarity;

import java.util.List;

/**
 * 17 tiers. Cheap tiers are small, expensive tiers are large.
 * Every tier after the first one requires the previous tier backpack in the recipe.
 * Starting with {@link #T9} one cell can hold up to 27 stacks of the same item.
 *
 * <p>Ingredient amounts are also limited by the vanilla shapeless recipe cap of
 * nine ingredient slots (see {@code BackpackRecipes}). Together with the previous
 * backpack each tier must fit into a 3x3 grid.</p>
 */
public enum Tier {
    T1("linen_pouch", "Льняной мешочек", "Linen Pouch", 9, 0xB08A5A, 0xE8D3A9,
            null,
            List.of(new Ingredient(Material.STRING, 5), new Ingredient(Material.LEATHER, 2))),

    T2("leather_satchel", "Кожаный ранец", "Leather Satchel", 15, 0x8B5A2B, 0xD9A066,
            T1,
            List.of(new Ingredient(Material.LEATHER, 4), new Ingredient(Material.STRING, 2))),

    T3("copper_pack", "Медный рюкзак", "Copper Pack", 21, 0xC77B4E, 0xF0B27A,
            T2,
            List.of(new Ingredient(Material.COPPER_INGOT, 6), new Ingredient(Material.LEATHER, 2))),

    T4("iron_pack", "Железный рюкзак", "Iron Pack", 27, 0xB8BCC4, 0xE8ECF2,
            T3,
            List.of(new Ingredient(Material.IRON_INGOT, 6), new Ingredient(Material.LEATHER, 2))),

    T5("gold_pack", "Золотой рюкзак", "Golden Pack", 33, 0xE0B23C, 0xFFE38A,
            T4,
            List.of(new Ingredient(Material.GOLD_INGOT, 6), new Ingredient(Material.LEATHER, 2))),

    T6("redstone_pack", "Рюкзак редстоуна", "Redstone Pack", 39, 0xB83232, 0xFF6B6B,
            T5,
            List.of(new Ingredient(Material.REDSTONE, 6), new Ingredient(Material.GOLD_INGOT, 2))),

    T7("lapis_pack", "Лазурный рюкзак", "Lapis Pack", 45, 0x2D5BD1, 0x7FA7FF,
            T6,
            List.of(new Ingredient(Material.LAPIS_LAZULI, 6), new Ingredient(Material.GOLD_INGOT, 2))),

    T8("amethyst_pack", "Аметистовый рюкзак", "Amethyst Pack", 51, 0x8E5BC9, 0xD7B3FF,
            T7,
            List.of(new Ingredient(Material.AMETHYST_SHARD, 6), new Ingredient(Material.GOLD_INGOT, 2))),

    T9("emerald_pack", "Изумрудный рюкзак", "Emerald Pack", 57, 0x2AA65C, 0x7BEEA9,
            T8,
            List.of(new Ingredient(Material.EMERALD, 6), new Ingredient(Material.GOLD_INGOT, 2))),

    T10("quartz_pack", "Кварцевый рюкзак", "Quartz Pack", 63, 0xD9D2C4, 0xFFF8EC,
            T9,
            List.of(new Ingredient(Material.QUARTZ, 6), new Ingredient(Material.GOLD_INGOT, 2))),

    T11("obsidian_pack", "Обсидиановый рюкзак", "Obsidian Pack", 69, 0x3B2A55, 0x8A6BC4,
            T10,
            List.of(new Ingredient(Material.OBSIDIAN, 6), new Ingredient(Material.DIAMOND, 2))),

    T12("diamond_pack", "Алмазный рюкзак", "Diamond Pack", 75, 0x35C7C0, 0x9BFFF8,
            T11,
            List.of(new Ingredient(Material.DIAMOND, 6), new Ingredient(Material.GOLD_INGOT, 2))),

    T13("blaze_pack", "Пламенный рюкзак", "Blaze Pack", 81, 0xD9731C, 0xFFC24A,
            T12,
            List.of(new Ingredient(Material.BLAZE_ROD, 5), new Ingredient(Material.MAGMA_CREAM, 3))),

    T14("ender_pack", "Эндер-рюкзак", "Ender Pack", 90, 0x1F7A56, 0x5CF2B0,
            T13,
            List.of(new Ingredient(Material.ENDER_PEARL, 5), new Ingredient(Material.CHORUS_FRUIT, 3))),

    T15("netherite_pack", "Незеритовый рюкзак", "Netherite Pack", 99, 0x4A3F3A, 0xA89A90,
            T14,
            List.of(new Ingredient(Material.NETHERITE_INGOT, 4), new Ingredient(Material.ECHO_SHARD, 4))),

    T16("dragon_pack", "Драконий рюкзак", "Dragon Pack", 108, 0x7B2FBE, 0xE58BFF,
            T15,
            List.of(new Ingredient(Material.DRAGON_BREATH, 3), new Ingredient(Material.NETHERITE_INGOT, 3),
                    new Ingredient(Material.ENDER_PEARL, 2))),

    T17("ultimate_pack", "Абсолютный рюкзак", "Ultimate Backpack", 256, 0x1E1436, 0xF5C542,
            T16,
            List.of(new Ingredient(Material.NETHER_STAR, 2), new Ingredient(Material.END_CRYSTAL, 2),
                    new Ingredient(Material.NETHERITE_INGOT, 2), new Ingredient(Material.DRAGON_BREATH, 1),
                    new Ingredient(Material.ENDER_PEARL, 1)));

    private final String id;
    private final String ruName;
    private final String enName;
    private final int capacity;
    private final int bodyColor;
    private final int accentColor;
    private final Tier previous;
    private final List<Ingredient> ingredients;

    Tier(String id, String ruName, String enName, int capacity, int bodyColor, int accentColor,
         Tier previous, List<Ingredient> ingredients) {
        this.id = id;
        this.ruName = ruName;
        this.enName = enName;
        this.capacity = capacity;
        this.bodyColor = bodyColor;
        this.accentColor = accentColor;
        this.previous = previous;
        this.ingredients = ingredients;
    }

    public String id() {
        return id;
    }

    public String name(boolean russian) {
        return russian ? ruName : enName;
    }

    public int capacity() {
        return capacity;
    }

    /**
     * How many stacks of the same item fit into one cell.
     * Half of the chain (tiers 9-17) gets the packing bonus.
     */
    public int stacksPerCell() {
        return number() >= 9 ? 27 : 1;
    }

    public int bodyColor() {
        return bodyColor;
    }

    public int accentColor() {
        return accentColor;
    }

    public Tier previous() {
        return previous;
    }

    public List<Ingredient> ingredients() {
        return ingredients;
    }

    public int number() {
        return ordinal() + 1;
    }

    public String modelKey() {
        return "backpack_" + number();
    }

    public int modelData() {
        return 9000 + number();
    }

    public ItemRarity rarity() {
        if (ordinal() < 3) {
            return ItemRarity.COMMON;
        }
        if (ordinal() < 7) {
            return ItemRarity.UNCOMMON;
        }
        if (ordinal() < 11) {
            return ItemRarity.RARE;
        }
        return ItemRarity.EPIC;
    }

    public int pages() {
        return Math.max(1, (capacity + 44) / 45);
    }

    public static Tier byId(String id) {
        if (id == null) {
            return null;
        }
        for (Tier tier : values()) {
            if (tier.id.equalsIgnoreCase(id)) {
                return tier;
            }
        }
        return null;
    }

    public static Tier byNumber(int number) {
        if (number < 1 || number > values().length) {
            return null;
        }
        return values()[number - 1];
    }
}
