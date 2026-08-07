package vn.haohan.utilities.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohan.utilities.carry.CarryActivationKey;
import vn.haohan.utilities.carry.CarryPreferences;
import vn.haohan.utilities.carry.CarryRecord;
import vn.haohan.utilities.carry.CarryService;
import vn.haohan.utilities.carry.CarryValidator;
import vn.haohan.utilities.config.MessageService;
import vn.haohan.utilities.heart.CrystalHeartItem;
import vn.haohan.utilities.heart.CrystalHeartListener;
import vn.haohan.utilities.phantom.PhantomSuppressionListener;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class HaoHanUtilitiesCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final CarryService carryService;
    private final CarryValidator validator;
    private final CarryPreferences preferences;
    private final MessageService messages;
    private final PhantomSuppressionListener phantomSuppression;
    private final CrystalHeartItem heartItem;
    private final CrystalHeartListener heartListener;

    public HaoHanUtilitiesCommand(JavaPlugin plugin, CarryService carryService, CarryValidator validator,
                                  CarryPreferences preferences, MessageService messages,
                                  PhantomSuppressionListener phantomSuppression,
                                  CrystalHeartItem heartItem, CrystalHeartListener heartListener) {
        this.plugin = plugin;
        this.carryService = carryService;
        this.validator = validator;
        this.preferences = preferences;
        this.messages = messages;
        this.phantomSuppression = phantomSuppression;
        this.heartItem = heartItem;
        this.heartListener = heartListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            sender.sendMessage("Hảo Hán Utilities v" + plugin.getPluginMeta().getVersion()
                    + " — Carry Blocks + Animals + Phantom Suppression + Crystal Heart");
            return true;
        }
        if (args[0].equalsIgnoreCase("toggle")) {
            return toggle(sender, args);
        }
        if (args[0].equalsIgnoreCase("bind")) {
            return bind(sender, args);
        }
        if (!sender.hasPermission("haohanutilities.admin")) {
            sender.sendMessage("Bạn không có quyền dùng lệnh này.");
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "status" -> status(sender, args);
            case "inspect" -> inspect(sender, args);
            case "recover" -> recover(sender, args);
            case "heart" -> heart(sender, args);
            default -> {
                sender.sendMessage("Dùng: /" + label + " <info|toggle|bind|reload|status|inspect|recover|heart>");
                yield true;
            }
        };
    }

    private boolean heart(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Dùng: /haohanutilities heart <give|setmax>");
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("give")) {
            Player target = null;
            if (args.length >= 3) {
                target = Bukkit.getPlayer(args[2]);
            } else if (sender instanceof Player p) {
                target = p;
            }
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Không tìm thấy người chơi.");
                return true;
            }
            int amount = 1;
            if (args.length >= 4) {
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException ignored) {}
            }
            if (heartItem != null) {
                ItemStack items = heartItem.createItem(amount);
                target.getInventory().addItem(items);
                sender.sendMessage(ChatColor.GREEN + "Đã trao " + amount + " Trái Tim Tinh Thể cho " + target.getName());
            } else {
                sender.sendMessage(ChatColor.RED + "Tính năng Trái Tim Tinh Thể chưa được khởi tạo.");
            }
            return true;
        } else if (sub.equals("setmax")) {
            if (args.length < 4) {
                sender.sendMessage(ChatColor.YELLOW + "Dùng: /haohanutilities heart setmax <player> <hp>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Không tìm thấy người chơi.");
                return true;
            }
            double hp;
            try {
                hp = Double.parseDouble(args[3]);
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Số HP không hợp lệ.");
                return true;
            }
            if (heartListener != null) {
                AttributeInstance maxAttr = heartListener.getMaxHealthAttribute(target);
                if (maxAttr != null) {
                    maxAttr.setBaseValue(hp);
                    sender.sendMessage(ChatColor.GREEN + "Đã đặt máu tối đa của " + target.getName() + " thành " + hp + " HP (" + (int)(hp / 2) + " Trái Tim)");
                } else {
                    sender.sendMessage(ChatColor.RED + "Không thể truy cập thuộc tính máu của người chơi.");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Tính năng Trái Tim Tinh Thể chưa được khởi tạo.");
            }
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Dùng: /haohanutilities heart <give|setmax>");
        return true;
    }

    private boolean toggle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Lệnh toggle cần chạy trong game.");
            return true;
        }
        boolean enabled;
        if (args.length < 2) {
            enabled = preferences.toggle(player);
        } else if (args[1].equalsIgnoreCase("on")) {
            preferences.setEnabled(player, true);
            enabled = true;
        } else if (args[1].equalsIgnoreCase("off")) {
            preferences.setEnabled(player, false);
            enabled = false;
        } else {
            messages.send(player, "carry-toggle-usage");
            return true;
        }
        if (enabled) {
            messages.send(player, "carry-enabled");
        } else {
            carryService.releaseIfCarried(player);
            if (carryService.isCarrying(player.getUniqueId())) {
                messages.send(player, "carry-disabled-active");
            } else {
                messages.send(player, "carry-disabled");
            }
        }
        return true;
    }

    private boolean bind(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Lệnh bind cần chạy trong game.");
            return true;
        }
        if (args.length < 2) {
            messages.send(player, "carry-bind-current", Map.of(
                    "key", preferences.activationKey(player).displayName()
            ));
            return true;
        }
        CarryActivationKey key = CarryActivationKey.parse(args[1]).orElse(null);
        if (key == null) {
            messages.send(player, "carry-bind-usage");
            return true;
        }
        preferences.setActivationKey(player, key);
        messages.send(player, "carry-bind-success", Map.of("key", key.displayName()));
        return true;
    }

    private boolean reload(CommandSender sender) {
        plugin.reloadConfig();
        validator.reload();
        messages.reload();
        if (heartItem != null) {
            heartItem.registerRecipe();
        }
        int removed = phantomSuppression.cleanupLoadedWorlds();
        sender.sendMessage("Đã reload cấu hình. Đã xóa " + removed + " Phantom đang tồn tại.");
        return true;
    }

    private boolean status(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Dùng: /haohanutilities status <player>");
            return true;
        }
        UUID playerUuid = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
        carryService.findActive(playerUuid).whenComplete((record, error) -> sync(() -> {
            if (error != null) {
                sender.sendMessage("Không thể đọc database: " + error.getMessage());
            } else if (record.isEmpty()) {
                sender.sendMessage("Người chơi không có carry transaction đang hoạt động.");
            } else {
                sendRecord(sender, record.get());
            }
        }));
        return true;
    }

    private boolean inspect(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Dùng: /haohanutilities inspect <carryId>");
            return true;
        }
        UUID carryId;
        try {
            carryId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("carryId không hợp lệ.");
            return true;
        }
        carryService.findById(carryId).whenComplete((record, error) -> sync(() -> {
            if (error != null) sender.sendMessage("Không thể đọc database: " + error.getMessage());
            else if (record.isEmpty()) sender.sendMessage("Không tìm thấy carryId.");
            else sendRecord(sender, record.get());
        }));
        return true;
    }

    private boolean recover(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage("Lệnh recover cần chạy trong game.");
            return true;
        }
        if (args.length < 3 || !(args[2].equalsIgnoreCase("original") || args[2].equalsIgnoreCase("here"))) {
            sender.sendMessage("Dùng: /haohanutilities recover <player> <original|here>");
            return true;
        }
        UUID playerUuid = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
        carryService.findActive(playerUuid).whenComplete((record, error) -> sync(() -> {
            if (error != null || record.isEmpty()) {
                sender.sendMessage(error == null ? "Không có transaction để recover." : "Không thể đọc database.");
                return;
            }
            CarryRecord value = record.get();
            Block destination;
            if (args[2].equalsIgnoreCase("original")) {
                World world = Bukkit.getWorld(value.originalPosition().worldUuid());
                if (world == null || !world.isChunkLoaded(value.originalPosition().x() >> 4, value.originalPosition().z() >> 4)) {
                    sender.sendMessage("World/chunk gốc chưa được load; không ghi đè cưỡng bức.");
                    return;
                }
                destination = value.originalPosition().block(world);
            } else {
                Block target = admin.getTargetBlockExact(5);
                if (target == null) {
                    sender.sendMessage("Không tìm thấy block đích trong tầm nhìn.");
                    return;
                }
                destination = target.isReplaceable() ? target : target.getRelative(0, 1, 0);
            }
            carryService.restoreHere(admin, value, destination);
        }));
        return true;
    }

    private static void sendRecord(CommandSender sender, CarryRecord record) {
        sender.sendMessage("carryId: " + record.carryId());
        sender.sendMessage("player: " + record.playerUuid());
        sender.sendMessage("status: " + record.status());
        sender.sendMessage("kind: " + record.payload().kind());
        sender.sendMessage("type: " + record.payload().typeKey());
        sender.sendMessage("original: " + record.originalPosition());
        sender.sendMessage("placement: " + record.placementPosition());
        sender.sendMessage("created: " + record.createdAt());
        sender.sendMessage("updated: " + record.updatedAt());
    }

    private void sync(Runnable task) {
        if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> commands = sender.hasPermission("haohanutilities.admin")
                    ? List.of("info", "toggle", "bind", "reload", "status", "inspect", "recover", "heart")
                    : List.of("info", "toggle", "bind");
            return filter(commands, args[0]);
        }
        if (args[0].equalsIgnoreCase("toggle") && args.length == 2) {
            return filter(List.of("on", "off"), args[1]);
        }
        if (args[0].equalsIgnoreCase("bind") && args.length == 2) {
            return filter(List.of("sprint", "sneak"), args[1]);
        }
        if (args[0].equalsIgnoreCase("heart") && sender.hasPermission("haohanutilities.admin")) {
            if (args.length == 2) {
                return filter(List.of("give", "setmax"), args[1]);
            }
            if (args.length == 3) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
            }
        }
        if ((args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("recover")) && args.length == 2) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args[0].equalsIgnoreCase("recover") && args.length == 3) {
            return filter(List.of("original", "here"), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }
}
