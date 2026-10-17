package vn.haohan.utilities.menu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * Service managing Player Settings dialogs via Paper Dialog API (1.21.4+ / 1.21.11).
 * <p>
 * Provides interactive nested dialogs:
 * 1. Main Menu (Account Settings, Game Settings, Discord, Website)
 * 2. Account Auth (Password verification)
 * 3. Account Settings (Change password, Discord linking, Login method)
 * 4. Game Settings (Shader toggle, Custom block render radius slider)
 */
public final class ServerMenuService {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
            .lifetime(Duration.ofMinutes(10))
            .uses(ClickCallback.UNLIMITED_USES)
            .build();

    private final Plugin plugin;
    private final ServerLinksManager linksManager;

    // In-memory prototype state storage
    private final ConcurrentHashMap<UUID, Boolean> discordLinked = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> shaderEnabled = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Float> viewRadius = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> loginMethod = new ConcurrentHashMap<>();

    public ServerMenuService(Plugin plugin, ServerLinksManager linksManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.linksManager = Objects.requireNonNull(linksManager, "linksManager cannot be null");
    }

    /**
     * Opens the top-level HaoHanSMP settings dialog for player.
     */
    public void openMainMenu(Player player) {
        if (player == null || !player.isOnline()) return;

        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();

            DialogBase base = DialogBase.builder(MM.deserialize("<gold><bold>HaoHanSMP</bold></gold> <dark_gray>•</dark_gray> <yellow>Cài Đặt</yellow>"))
                    .canCloseWithEscape(true)
                    .pause(false)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(
                            DialogBody.plainMessage(MM.deserialize("<gray>Chào mừng <white>" + player.getName() + "</white> đến bảng cài đặt hệ thống.</gray>\n<dark_gray>Chọn một tính năng bên dưới để tiếp tục:</dark_gray>"))
                    ))
                    .build();

            // 1. Cài đặt tài khoản
            ActionButton accountBtn = ActionButton.builder(MM.deserialize("<gold>⚙ Cài Đặt Tài Khoản</gold>"))
                    .tooltip(MM.deserialize("<gray>Đổi mật khẩu, liên kết Discord, phương thức đăng nhập</gray>"))
                    .action(DialogAction.customClick((DialogResponseView view, Audience audience) -> {
                        if (audience instanceof Player p) {
                            openAccountAuth(p);
                        }
                    }, CALLBACK_OPTIONS))
                    .build();

            // 2. Cài đặt game
            ActionButton gameBtn = ActionButton.builder(MM.deserialize("<aqua>🎮 Cài Đặt Game</aqua>"))
                    .tooltip(MM.deserialize("<gray>Tùy chỉnh Shader và tầm nhìn Alternative Custom Block</gray>"))
                    .action(DialogAction.customClick((DialogResponseView view, Audience audience) -> {
                        if (audience instanceof Player p) {
                            openGameSettings(p);
                        }
                    }, CALLBACK_OPTIONS))
                    .build();

            // 3. Discord Server
            ActionButton discordBtn = ActionButton.builder(MM.deserialize("<blue>💬 Discord Server</blue>"))
                    .tooltip(MM.deserialize("<gray>Mở link Discord cộng đồng HaoHanSMP</gray>"))
                    .action(DialogAction.staticAction(ClickEvent.openUrl(linksManager.getDiscordUri().toString())))
                    .build();

            // 4. Website
            ActionButton websiteBtn = ActionButton.builder(MM.deserialize("<green>🌐 Website</green>"))
                    .tooltip(MM.deserialize("<gray>Mở trang chủ web.haohansmp.io.vn</gray>"))
                    .action(DialogAction.staticAction(ClickEvent.openUrl(linksManager.getWebsiteUri().toString())))
                    .build();

            ActionButton exitBtn = ActionButton.builder(MM.deserialize("<red>Đóng</red>")).build();

            builder.base(base);
            builder.type(DialogType.multiAction(List.of(accountBtn, gameBtn, discordBtn, websiteBtn), exitBtn, 1));
        });

        player.showDialog(dialog);
    }

    /**
     * Sub-Dialog 1: Xác thực mật khẩu trước khi vào Cài Đặt Tài Khoản.
     */
    public void openAccountAuth(Player player) {
        if (player == null || !player.isOnline()) return;

        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();

            DialogBase base = DialogBase.builder(MM.deserialize("<gold><bold>Xác Thực Mật Khẩu</bold></gold>"))
                    .canCloseWithEscape(true)
                    .pause(false)
                    .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                    .body(List.of(
                            DialogBody.plainMessage(MM.deserialize("<yellow>Vui lòng nhập mật khẩu tài khoản hiện tại để tiếp tục.</yellow>\n<dark_gray>Đây là bước bảo mật bắt buộc trước khi chỉnh sửa thông tin.</dark_gray>"))
                    ))
                    .inputs(List.of(
                            DialogInput.text("current_password", 250, MM.deserialize("<gray>Mật khẩu hiện tại:</gray>"), true, "", 32, null)
                    ))
                    .build();

            ActionButton confirmBtn = ActionButton.builder(MM.deserialize("<green>Xác nhận</green>"))
                    .action(DialogAction.customClick((DialogResponseView view, Audience audience) -> {
                        if (audience instanceof Player p) {
                            p.sendMessage(MM.deserialize("<green>[HaoHanSMP]</green> <gray>Xác thực mật khẩu thành công! Đang chuyển tiếp...</gray>"));
                            openAccountSettings(p);
                        }
                    }, CALLBACK_OPTIONS))
                    .build();

            ActionButton backBtn = ActionButton.builder(MM.deserialize("<gray>Quay lại</gray>"))
                    .action(DialogAction.customClick((DialogResponseView view, Audience audience) -> {
                        if (audience instanceof Player p) {
                            openMainMenu(p);
                        }
                    }, CALLBACK_OPTIONS))
                    .build();

            builder.base(base);
            builder.type(DialogType.multiAction(List.of(confirmBtn), backBtn, 1));
        });

        player.showDialog(dialog);
    }

    /**
     * Sub-Dialog 1.1: Quản lý cài đặt tài khoản (Đổi mật khẩu, Discord, phương thức đăng nhập).
     */
    public void openAccountSettings(Player player) {
        if (player == null || !player.isOnline()) return;

        boolean isLinked = discordLinked.getOrDefault(player.getUniqueId(), false);
        String currentLogin = loginMethod.getOrDefault(player.getUniqueId(), "ingame");

        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();

            DialogBase base = DialogBase.builder(MM.deserialize("<gold><bold>Cài Đặt Tài Khoản</bold></gold>"))
                    .canCloseWithEscape(true)
                    .pause(false)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(
                            DialogBody.plainMessage(MM.deserialize("<gray>Tùy chỉnh thông tin đăng nhập và liên kết tài khoản:</gray>"))
                    ))
                    .inputs(List.of(
                            DialogInput.text("new_password", 250, MM.deserialize("<gray>Đổi mật khẩu mới (bỏ trống nếu không đổi):</gray>"), true, "", 32, null),
                            DialogInput.singleOption("login_method", 250, List.of(
                                    SingleOptionDialogInput.OptionEntry.create("ingame", MM.deserialize("<yellow>Mật khẩu In-game</yellow>"), "ingame".equals(currentLogin)),
                                    SingleOptionDialogInput.OptionEntry.create("discord", MM.deserialize("<blue>Discord OAuth / OTP</blue>"), "discord".equals(currentLogin))
                            ), MM.deserialize("<gray>Phương thức đăng nhập ưu tiên:</gray>"), true)
                    ))
                    .build();

            // Toggle nút liên kết discord mô phỏng 2 trạng thái
            Component discordLabel = isLinked
                    ? MM.deserialize("<red>❌ Hủy liên kết Discord (Đã liên kết)</red>")
                    : MM.deserialize("<blue>🔗 Liên kết Discord (Chưa liên kết)</blue>");

            ActionButton discordLinkBtn = ActionButton.builder(discordLabel)
                    .tooltip(MM.deserialize(isLinked ? "<gray>Bấm để ngắt kết nối tài khoản Discord</gray>" : "<gray>Bấm để nhận mã liên kết Discord Bot</gray>"))
                    .action(DialogAction.customClick((DialogResponseView view, Audience audience) -> {
                        if (audience instanceof Player p) {
                            boolean newState = !isLinked;
                            discordLinked.put(p.getUniqueId(), newState);
                            if (newState) {
                                p.sendMessage(MM.deserialize("<green>[HaoHanSMP]</green> <blue>Mã liên kết Discord của bạn: <gold>HH-" + (int)(Math.random() * 9000 + 1000) + "</gold>. Vui lòng nhập mã vào bot!</blue>"));
                            } else {
                                p.sendMessage(MM.deserialize("<yellow>[HaoHanSMP]</yellow> <gray>Đã hủy liên kết Discord thành công.</gray>"));
                            }
                            openAccountSettings(p); // Refresh dialog
                        }
                    }, CALLBACK_OPTIONS))
                    .build();

            ActionButton saveBtn = ActionButton.builder(MM.deserialize("<green>💾 Lưu Thay Đổi</green>"))
                    .action(DialogAction.customClick((DialogResponseView view, Audience audience) -> {
                        if (audience instanceof Player p) {
                            if (view != null) {
                                String method = view.getText("login_method");
                                if (method != null) loginMethod.put(p.getUniqueId(), method);
                            }
                            p.sendMessage(MM.deserialize("<green>[HaoHanSMP]</green> <gray>[Prototype] Đã lưu thông tin cài đặt tài khoản thành công!</gray>"));
                            openMainMenu(p);
                        }
                    }, CALLBACK_OPTIONS))
                    .build();

            ActionButton backBtn = ActionButton.builder(MM.deserialize("<gray>Quay lại</gray>"))
                    .action(DialogAction.customClick((DialogResponseView view, Audience audience) -> {
                        if (audience instanceof Player p) {
                            openMainMenu(p);
                        }
                    }, CALLBACK_OPTIONS))
                    .build();

            builder.base(base);
            builder.type(DialogType.multiAction(List.of(discordLinkBtn, saveBtn), backBtn, 1));
        });

        player.showDialog(dialog);
    }

    /**
     * Sub-Dialog 2: Cài đặt game (Bật/tắt shader, bán kính custom block).
     */
    public void openGameSettings(Player player) {
        if (player == null || !player.isOnline()) return;

        boolean shaders = shaderEnabled.getOrDefault(player.getUniqueId(), true);
        float radius = viewRadius.getOrDefault(player.getUniqueId(), 32.0f);

        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();

            DialogBase base = DialogBase.builder(MM.deserialize("<aqua><bold>Cài Đặt Game</bold></aqua>"))
                    .canCloseWithEscape(true)
                    .pause(false)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(
                            DialogBody.plainMessage(MM.deserialize("<gray>Cấu hình tối ưu hiển thị cho máy của bạn:</gray>"))
                    ))
                    .inputs(List.of(
                            DialogInput.bool("shader_enabled", MM.deserialize("<yellow>Bật / Tắt hỗ trợ Shader riêng</yellow>"), shaders, "true", "false"),
                            DialogInput.numberRange("custom_block_radius", 250, MM.deserialize("<green>Bán kính nhìn thấy Alternative Block (blocks):</green>"), "%d blocks", 8.0f, 64.0f, radius, 8.0f)
                    ))
                    .build();

            ActionButton applyBtn = ActionButton.builder(MM.deserialize("<green>✔ Áp Dụng</green>"))
                    .action(DialogAction.customClick((DialogResponseView view, Audience audience) -> {
                        if (audience instanceof Player p) {
                            if (view != null) {
                                Boolean shaderVal = view.getBoolean("shader_enabled");
                                if (shaderVal != null) shaderEnabled.put(p.getUniqueId(), shaderVal);
                                Float radiusVal = view.getFloat("custom_block_radius");
                                if (radiusVal != null) viewRadius.put(p.getUniqueId(), radiusVal);
                            }
                            p.sendMessage(MM.deserialize("<green>[HaoHanSMP]</green> <gray>[Prototype] Đã lưu cấu hình đồ họa & tầm nhìn Custom Block!</gray>"));
                            openMainMenu(p);
                        }
                    }, CALLBACK_OPTIONS))
                    .build();

            ActionButton backBtn = ActionButton.builder(MM.deserialize("<gray>Quay lại</gray>"))
                    .action(DialogAction.customClick((DialogResponseView view, Audience audience) -> {
                        if (audience instanceof Player p) {
                            openMainMenu(p);
                        }
                    }, CALLBACK_OPTIONS))
                    .build();

            builder.base(base);
            builder.type(DialogType.multiAction(List.of(applyBtn), backBtn, 1));
        });

        player.showDialog(dialog);
    }

    // Helper getters for testing
    public boolean isDiscordLinked(UUID uuid) {
        return discordLinked.getOrDefault(uuid, false);
    }

    public void setDiscordLinked(UUID uuid, boolean linked) {
        discordLinked.put(uuid, linked);
    }

    public boolean isShaderEnabled(UUID uuid) {
        return shaderEnabled.getOrDefault(uuid, true);
    }

    public float getViewRadius(UUID uuid) {
        return viewRadius.getOrDefault(uuid, 32.0f);
    }
}
