package dev.fosk.additems;

import dev.fosk.additems.item.ItemDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** /additems login | give <id> [amount] [player] | list | reload | pack build|send */
public final class AddItemsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = List.of("login", "give", "place", "list", "dump", "reload", "pack");

    private final AddItemsPlugin plugin;

    public AddItemsCommand(AddItemsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /additems <login|give|list|reload|pack>", NamedTextColor.YELLOW));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "login" -> login(sender);
            case "give" -> give(sender, args);
            case "place" -> place(sender, args);
            case "dump" -> {
                ItemDefinition def = args.length > 1 ? plugin.registry().get(args[1]) : null;
                if (def == null) {
                    sender.sendMessage(Component.text("Usage: /additems dump <item id>", NamedTextColor.YELLOW));
                } else {
                    sender.sendMessage(Component.text("Components of " + def.id + ":", NamedTextColor.GREEN));
                    for (String line : plugin.factory().describe(plugin.factory().create(def, 1))) {
                        sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
                    }
                }
            }
            case "list" -> sender.sendMessage(Component.text("Items: " + String.join(", ",
                    plugin.registry().all().stream().map(d -> d.id).toList()), NamedTextColor.GRAY));
            case "reload" -> {
                try {
                    plugin.reloadAll();
                    sender.sendMessage(Component.text("Reloaded " + plugin.registry().all().size() + " items.",
                            NamedTextColor.GREEN));
                } catch (Exception e) {
                    sender.sendMessage(Component.text("Reload failed: " + e.getMessage(), NamedTextColor.RED));
                }
            }
            case "pack" -> pack(sender, args);
            default -> sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
        }
        return true;
    }

    private void login(CommandSender sender) {
        String token = plugin.auth().createLoginToken(sender.getName());
        String url = plugin.dashboardUrl() + "/login?token=" + token;
        sender.sendMessage(Component.text("Click to open the dashboard (valid 10 minutes, single use):",
                NamedTextColor.GREEN));
        sender.sendMessage(Component.text(url, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url)));
        String hint = plugin.dashboardAccessHint();
        if (hint != null) sender.sendMessage(Component.text(hint, NamedTextColor.YELLOW));
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /additems give <id> [amount] [player]", NamedTextColor.YELLOW));
            return;
        }
        ItemDefinition def = plugin.registry().get(args[1]);
        if (def == null) {
            sender.sendMessage(Component.text("Unknown item: " + args[1], NamedTextColor.RED));
            return;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Amount must be a number.", NamedTextColor.RED));
                return;
            }
        }
        Player target = args.length >= 4 ? Bukkit.getPlayerExact(args[3])
                : (sender instanceof Player p ? p : null);
        if (target == null) {
            sender.sendMessage(Component.text("Specify an online player.", NamedTextColor.RED));
            return;
        }
        target.getInventory().addItem(plugin.factory().create(def, amount))
                .values().forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
        sender.sendMessage(Component.text("Gave " + amount + "x " + def.id + " to " + target.getName(),
                NamedTextColor.GREEN));
    }

    /** /additems place <block id> [x y z] [world]: places a custom block directly (no item needed). */
    private void place(CommandSender sender, String[] args) {
        ItemDefinition def = args.length > 1 ? plugin.registry().get(args[1]) : null;
        if (def == null || !def.block.placeable || def.block.state == null) {
            sender.sendMessage(Component.text("Usage: /additems place <custom block id> [x y z] [world]", NamedTextColor.YELLOW));
            return;
        }
        org.bukkit.World world = sender instanceof Player p ? p.getWorld() : Bukkit.getWorlds().get(0);
        int x, y, z;
        try {
            if (args.length >= 5) {
                x = Integer.parseInt(args[2]);
                y = Integer.parseInt(args[3]);
                z = Integer.parseInt(args[4]);
                if (args.length >= 6) world = Bukkit.getWorld(args[5]);
            } else if (sender instanceof Player p) {
                x = p.getLocation().getBlockX();
                y = p.getLocation().getBlockY();
                z = p.getLocation().getBlockZ();
            } else {
                sender.sendMessage(Component.text("Console needs coordinates: /additems place <id> <x> <y> <z> [world]", NamedTextColor.RED));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Coordinates must be whole numbers.", NamedTextColor.RED));
            return;
        }
        if (world == null) {
            sender.sendMessage(Component.text("Unknown world.", NamedTextColor.RED));
            return;
        }
        plugin.customBlocks().place(world.getBlockAt(x, y, z), def);
        sender.sendMessage(Component.text("Placed " + def.id + " at " + x + " " + y + " " + z, NamedTextColor.GREEN));
    }

    private void pack(CommandSender sender, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase() : "";
        if (sub.equals("build")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    var result = plugin.buildPack();
                    sender.sendMessage(Component.text("Pack built: " + result.sha1() + " (" + result.size()
                            + " bytes)", NamedTextColor.GREEN));
                    String problem = plugin.packProblem();
                    if (problem != null) {
                        sender.sendMessage(Component.text("Not sent to players: " + problem, NamedTextColor.YELLOW));
                    }
                } catch (Exception e) {
                    sender.sendMessage(Component.text("Build failed: " + e.getMessage(), NamedTextColor.RED));
                }
            });
        } else if (sub.equals("send")) {
            String problem = plugin.packProblem();
            if (problem != null) {
                sender.sendMessage(Component.text("Cannot send pack: " + problem, NamedTextColor.RED));
                return;
            }
            Bukkit.getOnlinePlayers().forEach(plugin::sendPack);
            sender.sendMessage(Component.text("Pack sent to online players.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Usage: /additems pack <build|send>", NamedTextColor.YELLOW));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label,
                                      @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            SUB.stream().filter(s -> s.startsWith(args[0].toLowerCase())).forEach(out::add);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("dump") || args[0].equalsIgnoreCase("place"))) {
            plugin.registry().all().stream().map(d -> d.id).filter(s -> s.startsWith(args[1])).forEach(out::add);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("pack")) {
            List.of("build", "send").stream().filter(s -> s.startsWith(args[1])).forEach(out::add);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.startsWith(args[3])).forEach(out::add);
        }
        return out;
    }
}
