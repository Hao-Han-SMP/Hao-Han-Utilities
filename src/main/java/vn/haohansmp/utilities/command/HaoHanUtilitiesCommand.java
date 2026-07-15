package vn.haohansmp.utilities.command;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohansmp.utilities.carry.CarryRecord;
import vn.haohansmp.utilities.carry.CarryService;
import vn.haohansmp.utilities.carry.CarryValidator;
import vn.haohansmp.utilities.config.MessageService;
import vn.haohansmp.utilities.phantom.PhantomSuppressionListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class HaoHanUtilitiesCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final CarryService carryService;
    private final CarryValidator validator;
    private final MessageService messages;
    private final PhantomSuppressionListener phantomSuppression;

    public HaoHanUtilitiesCommand(JavaPlugin plugin, CarryService carryService, CarryValidator validator,
                                  MessageService messages, PhantomSuppressionListener phantomSuppression) {
        this.plugin = plugin;
        this.carryService = carryService;
        this.validator = validator;
        this.messages = messages;
        this.phantomSuppression = phantomSuppression;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            sender.sendMessage("Hảo Hán Utilities v" + plugin.getPluginMeta().getVersion()
                    + " — Carry Blocks + Phantom Suppression");
            return true;
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
            default -> {
                sender.sendMessage("Dùng: /" + label + " <info|reload|status|inspect|recover>");
                yield true;
            }
        };
    }

    private boolean reload(CommandSender sender) {
        plugin.reloadConfig();
        validator.reload();
        messages.reload();
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
        sender.sendMessage("material: " + record.payload().material());
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
        if (args.length == 1) return filter(List.of("info", "reload", "status", "inspect", "recover"), args[0]);
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
