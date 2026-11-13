package ru.backpacks;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BpCommand implements CommandExecutor, TabCompleter {
    private final BackpacksPlugin plugin;

    public BpCommand(BackpacksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                plugin.msg().send(sender, "player-only");
                return true;
            }
            if (!allowed(player)) {
                return true;
            }
            openBest(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "hello" -> {
                if (sender instanceof Player player) {
                    plugin.modBridge().mark(player);
                }
            }
            case "recipes", "recipe" -> {
                if (sender instanceof Player player) {
                    if (allowed(player)) {
                        plugin.recipes().open(player);
                    }
                } else {
                    plugin.msg().send(sender, "player-only");
                }
            }
            case "equip" -> {
                if (sender instanceof Player player) {
                    if (allowed(player)) {
                        plugin.gui().equipFromHand(player);
                    }
                } else {
                    plugin.msg().send(sender, "player-only");
                }
            }
            case "unequip" -> {
                if (sender instanceof Player player) {
                    if (allowed(player)) {
                        plugin.gui().unequip(player);
                    }
                } else {
                    plugin.msg().send(sender, "player-only");
                }
            }
            case "remove" -> {
                if (sender instanceof Player player) {
                    if (admin(sender)) {
                        plugin.gui().removeHeld(player);
                    }
                } else {
                    plugin.msg().send(sender, "player-only");
                }
            }
            case "reload" -> {
                if (admin(sender)) {
                    plugin.reloadAll();
                    plugin.msg().send(sender, "reloaded");
                }
            }
            case "give" -> give(sender, args);
            case "help", "?" -> plugin.msg().sendList(sender, "help");
            default -> plugin.msg().sendList(sender, "help");
        }
        return true;
    }

    private void openBest(Player player) {
        if (plugin.items().equipped(player) != null) {
            plugin.gui().openEquipped(player);
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (plugin.items().isBackpack(hand)) {
            plugin.gui().open(player, hand, false);
            return;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (plugin.items().isBackpack(offhand)) {
            plugin.gui().open(player, offhand, false, org.bukkit.inventory.EquipmentSlot.OFF_HAND);
            return;
        }
        plugin.msg().send(player, "no-backpack");
    }

    private void give(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 3) {
            plugin.msg().send(sender, "usage-give");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.msg().send(sender, "player-not-found");
            return;
        }

        Tier tier = parseTier(args[2]);
        if (tier == null) {
            plugin.msg().send(sender, "unknown-tier", "total", Tier.values().length);
            return;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }

        for (int i = 0; i < amount; i++) {
            plugin.gui().give(target, plugin.items().createFor(target, tier));
        }
        plugin.msg().send(sender, "give-success",
                "tier", tier.name(plugin.msg().russian()),
                "player", target.getName());
        if (sender != target) {
            plugin.msg().send(target, "give-received", "tier", tier.name(plugin.msg().russian()));
        }
    }

    private Tier parseTier(String value) {
        try {
            Tier byNumber = Tier.byNumber(Integer.parseInt(value));
            if (byNumber != null) {
                return byNumber;
            }
        } catch (NumberFormatException ignored) {
            // not a number, try id
        }
        return Tier.byId(value);
    }

    private boolean allowed(Player player) {
        if (player.hasPermission("backpacks.use")) {
            return true;
        }
        plugin.msg().send(player, "no-permission");
        return false;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("backpacks.admin")) {
            return true;
        }
        plugin.msg().send(sender, "no-permission");
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("recipes", "equip", "unequip", "help"));
            if (sender.hasPermission("backpacks.admin")) {
                options.addAll(List.of("give", "remove", "reload"));
            }
            return filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> tiers = new ArrayList<>();
            for (Tier tier : Tier.values()) {
                tiers.add(String.valueOf(tier.number()));
            }
            for (Tier tier : Tier.values()) {
                tiers.add(tier.id());
            }
            return filter(tiers, args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }
}
