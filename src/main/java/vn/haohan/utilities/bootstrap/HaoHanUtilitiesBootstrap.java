package vn.haohan.utilities.bootstrap;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import io.papermc.paper.registry.set.RegistryValueSetBuilder;
import io.papermc.paper.tag.PreFlattenTagRegistrar;
import io.papermc.paper.tag.TagEntry;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;

/**
 * Bootstrap entry for HaoHanUtilities.
 * Registers native Dialogs into minecraft:pause_screen_additions so that clicking ESC
 * displays the "HaoHanSMP" native settings dialog.
 */
public final class HaoHanUtilitiesBootstrap implements PluginBootstrap {

    public static final TypedKey<Dialog> HAOHAN_MENU_KEY = TypedKey.create(RegistryKey.DIALOG, Key.key("haohan", "settings"));
    private static final MiniMessage MM = MiniMessage.miniMessage();

    @Override
    public void bootstrap(BootstrapContext context) {
        // Register Dialog entry
        context.getLifecycleManager().registerEventHandler(RegistryEvents.DIALOG.compose(), event -> {
            event.registry().register(HAOHAN_MENU_KEY, builder -> {
                RegistryValueSetBuilder<Dialog, DialogRegistryEntry.Builder> subDialogs = builder.registryValueSet();

                // 1. Cài đặt tài khoản
                subDialogs.add(subFactory -> {
                    DialogRegistryEntry.Builder sub = subFactory.empty();
                    sub.base(DialogBase.builder(MM.deserialize("<gold><bold>Cài Đặt Tài Khoản</bold></gold>"))
                            .externalTitle(MM.deserialize("<gold>⚙ Cài Đặt Tài Khoản</gold>"))
                            .canCloseWithEscape(true)
                            .body(List.of(
                                    DialogBody.plainMessage(MM.deserialize("<yellow>Bảo mật & Cài đặt thông tin tài khoản:</yellow>"))
                            ))
                            .inputs(List.of(
                                    DialogInput.text("password_verify", 250, MM.deserialize("<gray>Xác thực mật khẩu hiện tại:</gray>"), true, "", 32, null),
                                    DialogInput.text("new_password", 250, MM.deserialize("<gray>Đổi mật khẩu mới (nếu cần):</gray>"), true, "", 32, null),
                                    DialogInput.singleOption("login_method", 250, List.of(
                                            SingleOptionDialogInput.OptionEntry.create("ingame", MM.deserialize("<yellow>Mật khẩu In-game</yellow>"), true),
                                            SingleOptionDialogInput.OptionEntry.create("discord", MM.deserialize("<blue>Discord OAuth / OTP</blue>"), false)
                                    ), MM.deserialize("<gray>Phương thức đăng nhập ưu tiên:</gray>"), true)
                            ))
                            .build());
                    sub.type(DialogType.notice());
                });

                // 2. Cài đặt game
                subDialogs.add(subFactory -> {
                    DialogRegistryEntry.Builder sub = subFactory.empty();
                    sub.base(DialogBase.builder(MM.deserialize("<aqua><bold>Cài Đặt Game</bold></aqua>"))
                            .externalTitle(MM.deserialize("<aqua>🎮 Cài Đặt Game</aqua>"))
                            .canCloseWithEscape(true)
                            .body(List.of(
                                    DialogBody.plainMessage(MM.deserialize("<gray>Cấu hình tối ưu đồ họa và tầm nhìn:</gray>"))
                            ))
                            .inputs(List.of(
                                    DialogInput.bool("shader_enabled", MM.deserialize("<yellow>Bật / Tắt hỗ trợ Shader riêng</yellow>"), true, "true", "false"),
                                    DialogInput.numberRange("custom_block_radius", 250, MM.deserialize("<green>Bán kính nhìn thấy Alternative Block (blocks):</green>"), "%d blocks", 8.0f, 64.0f, 32.0f, 8.0f)
                            ))
                            .build());
                    sub.type(DialogType.notice());
                });

                // 3. Discord Server (Open link)
                subDialogs.add(subFactory -> {
                    DialogRegistryEntry.Builder sub = subFactory.empty();
                    sub.base(DialogBase.builder(MM.deserialize("<blue><bold>HaoHanSMP Discord</bold></blue>"))
                            .externalTitle(MM.deserialize("<blue>💬 Discord Server</blue>"))
                            .canCloseWithEscape(true)
                            .body(List.of(
                                    DialogBody.plainMessage(MM.deserialize("<gray>Tham gia cộng đồng Discord chính thức của HaoHanSMP để nhận thông báo và hỗ trợ.</gray>"))
                            ))
                            .build());
                    sub.type(DialogType.notice(ActionButton.builder(MM.deserialize("<blue>🔗 Mở Discord (discord.gg/NwTJ6Ezey4)</blue>"))
                            .action(DialogAction.staticAction(ClickEvent.openUrl("https://discord.gg/NwTJ6Ezey4")))
                            .build()));
                });

                // 4. Website (Open link)
                subDialogs.add(subFactory -> {
                    DialogRegistryEntry.Builder sub = subFactory.empty();
                    sub.base(DialogBase.builder(MM.deserialize("<green><bold>HaoHanSMP Website</bold></green>"))
                            .externalTitle(MM.deserialize("<green>🌐 Website</green>"))
                            .canCloseWithEscape(true)
                            .body(List.of(
                                    DialogBody.plainMessage(MM.deserialize("<gray>Truy cập trang chủ máy chủ HaoHanSMP để xem thông tin, wiki và bảng xếp hạng.</gray>"))
                            ))
                            .build());
                    sub.type(DialogType.notice(ActionButton.builder(MM.deserialize("<green>🌐 Mở Website (web.haohansmp.io.vn)</green>"))
                            .action(DialogAction.staticAction(ClickEvent.openUrl("https://web.haohansmp.io.vn")))
                            .build()));
                });

                // Root Dialog
                builder.base(DialogBase.builder(MM.deserialize("<gold><bold>HaoHanSMP</bold></gold> <dark_gray>•</dark_gray> <yellow>Cài Đặt</yellow>"))
                        .externalTitle(MM.deserialize("<gold><bold>HaoHanSMP</bold></gold>"))
                        .canCloseWithEscape(true)
                        .body(List.of(
                                DialogBody.plainMessage(MM.deserialize("<gray>Hệ thống quản lý và cài đặt server dành cho người chơi.</gray>\n<dark_gray>Chọn một mục bên dưới để cấu hình:</dark_gray>"))
                        ))
                        .build());
                builder.type(DialogType.dialogList(subDialogs.build(), ActionButton.builder(MM.deserialize("<red>Đóng</red>")).build(), 1, 220));
            });
        });

        // Add to PAUSE_SCREEN_ADDITIONS (#minecraft:pause_screen_additions)
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.TAGS.preFlatten(RegistryKey.DIALOG), event -> {
            PreFlattenTagRegistrar<Dialog> registrar = event.registrar();
            registrar.addToTag(DialogTagKeys.PAUSE_SCREEN_ADDITIONS, List.of(
                    TagEntry.valueEntry(HAOHAN_MENU_KEY)
            ));
        });
    }
}
