<div align="center">

# Hảo Hán Utilities

Bộ tiện ích server-side dành cho Hao Hán SMP: bưng functional block an toàn và loại bỏ Phantom khỏi thế giới.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Paper](https://img.shields.io/badge/Paper-API-222222?style=for-the-badge&logo=paper&logoColor=white)](https://papermc.io/)
[![Purpur](https://img.shields.io/badge/Purpur-Compatible-8A4FFF?style=for-the-badge)](https://purpurmc.org/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.x-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![SQLite](https://img.shields.io/badge/SQLite-WAL-003B57?style=for-the-badge&logo=sqlite&logoColor=white)](https://sqlite.org/)

Ngôn ngữ: Tiếng Việt | [English](README.en.md)

</div>

## Tổng Quan

Hảo Hán Utilities là plugin Paper/Purpur `1.21.11` cho Hao Hán SMP. Phiên bản đầu tiên gồm hai module:

- **Carry Blocks:** cúi người, tay không và right click để bưng một container hoặc functional block; right click lần nữa để đặt xuống.
- **Phantom Suppression:** chặn mọi lần spawn Phantom và xóa Phantom đã tồn tại khi plugin, world hoặc chunk được load.

Carry Blocks không biến block thành item. Inventory và trạng thái block được giữ trong SQLite transaction journal, còn `BlockDisplay` chỉ dùng để hiển thị block trước người chơi.

## Công Nghệ Sử Dụng

| Toolkit | Vai trò |
| --- | --- |
| Paper API | API plugin và block-state cho Minecraft `1.21.11`. |
| Purpur | Môi trường server tương thích được khuyến nghị. |
| Java 21 | Ngôn ngữ và runtime chính. |
| Gradle Wrapper | Build reproducible, không cần cài Gradle toàn cục. |
| SQLite (WAL + FULL sync) | Transaction journal chống mất đồ/duplicate và hỗ trợ crash recovery. |
| JUnit 5 | Kiểm thử codec và vòng đời SQLite transaction. |

## Tính Năng

### Carry Blocks

- Mỗi người chơi chỉ được bưng một block tại một thời điểm.
- Luồng transaction: `PREPARED → CARRIED → PLACING → PLACED/RESTORED`.
- Chỉ xóa block sau khi payload `PREPARED` đã được ghi thành công.
- Giữ inventory với Paper byte serialization, gồm cả metadata, enchantment, book, bundle, shulker content và PDC của item.
- Giữ `BlockData`, custom name, lock và Persistent Data Container của block.
- Giữ burn/cook time, cook speed của furnace, blast furnace, smoker.
- Giữ brewing time, fuel level và recipe brew time của brewing stand.
- Fingerprint phát hiện block/destination bị thay đổi giữa hai phase.
- Runtime block lock chặn break/place, explosion, piston, hopper và inventory viewer trong transaction.
- Plugin chunk ticket giữ chunk không unload giữa lúc SQLite đang ghi.
- Một follow task chung cập nhật toàn bộ `BlockDisplay`; không tạo task riêng cho từng người chơi.
- Tự restore về vị trí gốc khi logout, kick, chết hoặc plugin tắt nếu vị trí vẫn an toàn.
- Crash recovery không ghi đè block khác và giữ payload trong database khi không thể xác định an toàn.
- Protection probe qua `BlockBreakEvent`, `BlockCanBuildEvent` và hai custom event cancellable.
- Chặn rương đôi trong phiên bản hiện tại để tránh lỗi hai-block transaction.

Block được hỗ trợ mặc định:

| Nhóm | Block |
| --- | --- |
| Container | Chest đơn, Trapped Chest đơn, Barrel, Hopper, Dispenser, Dropper, Shulker Box, Crafter |
| Furnace | Furnace, Blast Furnace, Smoker |
| Functional | Brewing Stand, Crafting Table, Smithing Table, Stonecutter, Cartography Table, Loom, Grindstone, Enchanting Table |

### Phantom Suppression

- Cancel mọi `EntitySpawnEvent` có type `PHANTOM`, không phụ thuộc spawn reason.
- Xóa Phantom đã load khi plugin khởi động.
- Dọn Phantom khi world hoặc chunk được load.
- Áp dụng cho mọi world, độc lập với blacklist Carry Blocks.
- Có thể tắt riêng trong `config.yml` nếu cần bảo trì.

## Yêu Cầu

- Paper hoặc Purpur `1.21.11`.
- Java `21`.
- Không cần mod hoặc resource pack phía client.
- Gradle không bắt buộc; repository đã có Gradle Wrapper.

## Cài Đặt

1. Build plugin hoặc tải file JAR từ bản phát hành.
2. Copy `HaoHanUtilities-1.0.0.jar` vào thư mục `plugins/`.
3. Khởi động hoặc restart server; không dùng `/reload` của Bukkit để test transaction.
4. Kiểm tra log `Hảo Hán Utilities enabled` và chỉnh `plugins/HaoHanUtilities/config.yml` nếu cần.

Database được tạo tại:

```text
plugins/HaoHanUtilities/carry-blocks.db
```

## Cách Sử Dụng Carry Blocks

1. Đảm bảo hai tay trống.
2. Cúi người và right click block được hỗ trợ.
3. Di chuyển tới vị trí mới; không sprint, glide, teleport hoặc combat khi đang bưng theo config mặc định.
4. Right click mặt block tại vị trí muốn đặt.
5. Plugin ghi phase `PLACING`, restore và verify payload trước khi kết thúc transaction.

## Lệnh

| Lệnh | Mô tả |
| --- | --- |
| `/haohanutilities info` | Hiển thị version và module đang có. |
| `/haohanutilities reload` | Reload config/messages và dọn Phantom đang load. |
| `/haohanutilities status <player>` | Xem carry transaction đang hoạt động của người chơi. |
| `/haohanutilities inspect <carryId>` | Xem metadata của một transaction. |
| `/haohanutilities recover <player> original` | Khôi phục payload về vị trí gốc nếu chunk đã load và vị trí trống. |
| `/haohanutilities recover <player> here` | Khôi phục payload tại vị trí admin đang nhìn. |

Alias:

```text
/hhu
/carryblocks
/carryblock
/cb
```

## Permission

| Permission | Mặc định | Mô tả |
| --- | --- | --- |
| `haohanutilities.carry.use` | true | Cho phép bưng và đặt block. |
| `haohanutilities.admin` | op | Cho phép dùng lệnh quản trị/recovery. |
| `haohanutilities.bypass.protection` | op | Bỏ qua protection probe; chỉ nên cấp cho admin tin cậy. |

## Cấu Hình

File cấu hình được tạo tại:

```text
plugins/HaoHanUtilities/config.yml
```

Các key chính:

| Key | Mặc định | Mô tả |
| --- | --- | --- |
| `pickup.require-sneaking` | `true` | Yêu cầu người chơi cúi khi pickup. |
| `pickup.require-empty-main-hand` | `true` | Yêu cầu tay chính trống. |
| `pickup.require-empty-off-hand` | `true` | Yêu cầu tay phụ trống. |
| `placement.require-solid-support` | `true` | Yêu cầu block đỡ rắn. |
| `carrying.disable-teleport` | `true` | Chặn teleport khi đang bưng. |
| `recovery.restore-on-startup` | `true` | Đọc transaction dang dở khi plugin enable. |
| `phantom-suppression.enabled` | `true` | Không cho Phantom spawn. |
| `phantom-suppression.remove-existing` | `true` | Xóa Phantom đã tồn tại trong chunk được load. |
| `worlds.mode` | `BLACKLIST` | Chế độ danh sách world của Carry Blocks. |
| `blocks.allowed` | xem config | Allowlist block có thể bưng. |

## Build Từ Mã Nguồn

Windows:

```powershell
.\gradlew.bat clean test shadowJar
```

Linux/macOS:

```bash
./gradlew clean test shadowJar
```

JAR deploy được tạo tại:

```text
build/libs/HaoHanUtilities-1.0.0.jar
```

## Ghi Chú Vận Hành

- Luôn shutdown/restart server đúng cách khi cập nhật plugin.
- Không xóa `carry-blocks.db`, file `-wal` hoặc `-shm` khi server đang chạy.
- Nếu recovery fail-closed vì vị trí đã bị chiếm, dùng `status`, `inspect` và `recover`; plugin không tự ghi đè block lạ.
- Backup world và thư mục plugin trước khi nâng cấp trên server production.
- Các protection plugin có thể cancel protection probe; custom integration có thể listen `CarryBlockPickupEvent` và `CarryBlockPlaceEvent`.
