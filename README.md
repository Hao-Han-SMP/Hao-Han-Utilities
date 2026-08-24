<div align="center">

# Hao Han Utilities

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Paper](https://img.shields.io/badge/Paper-API-222222?style=for-the-badge&logo=paper&logoColor=white)](https://papermc.io/)
[![Purpur](https://img.shields.io/badge/Purpur-Compatible-8A4FFF?style=for-the-badge)](https://purpurmc.org/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.x-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![SQLite](https://img.shields.io/badge/SQLite-WAL-003B57?style=for-the-badge&logo=sqlite&logoColor=white)](https://sqlite.org/)

Ngôn ngữ: Tiếng Việt | [English](README.en.md)

</div>

## Giới thiệu

Hao Han Utilities là plugin Paper/Purpur `1.21.11` gồm các tính năng tiện ích:

- **Carry:** người chơi có thể nhấc block chức năng, động vật hoặc người chơi khác lên và mang đến vị trí khác.
- **Phantom Suppression:** ngăn Phantom xuất hiện và xóa Phantom đang tồn tại trong các world đã tải.
- **54-slot EnderChest:** mở rộng EnderChest lên 54 ô (6 hàng), tự động chuyển đổi dữ liệu từ EnderChest 27 ô nguyên bản.
- **Torch Ignition:** đánh thiêu cháy entity khi sử dụng Đuốc (Đuốc thường, Đuốc linh hồn, Đuốc đá đỏ) làm vũ khí cận chiến.
- **Concrete Mixer:** dùng cauldron nước để đổi concrete powder thành concrete cùng màu.

Plugin hoàn toàn server-side, không yêu cầu mod client hoặc resource pack.

## Cách sử dụng Carry

### Nhấc vật

1. Đảm bảo cả tay chính và tay phụ đều trống.
2. Giữ phím kích hoạt carry (mặc định là Sprint/`Ctrl`).
3. Chuột phải vào block, động vật hoặc người chơi muốn nhấc.

Nếu tay đang có đồ hoặc block không hỗ trợ carry, plugin không chặn thao tác và không gửi thông báo; Minecraft sẽ đặt block hoặc tương tác như bình thường.

Carry mode được bật mặc định cho từng người chơi. Dùng `/hhu toggle` để bật/tắt; khi tắt, plugin không chặn tương tác chuột phải của người chơi đó. Có thể đổi control giữ kích hoạt bằng `/hhu bind sprint` hoặc `/hhu bind sneak`. Bind đi theo cài đặt Sprint/Sneak phía client, kể cả khi người chơi đã đổi phím trong Controls.

Nếu tắt mode trong lúc đang carry, người chơi vẫn có thể đặt vật đang giữ xuống an toàn; mode tắt sẽ áp dụng cho những lần nhấc tiếp theo.

Người chơi chỉ có thể carry một vật tại một thời điểm. Container càng chứa nhiều đồ thì người chơi càng di chuyển chậm.

### Đặt vật

1. Nhìn vào vị trí muốn đặt.
2. Chuột phải vào một mặt block.
3. Vật đang carry sẽ được đặt ở mặt vừa chọn.

### Động vật

Các động vật và sinh vật thụ động được hỗ trợ sẽ giữ lại dữ liệu như:

- Máu, tuổi và biến thể.
- Tên tùy chỉnh.
- Equipment, inventory và Persistent Data Container.

### Người chơi

Giữ phím kích hoạt carry với hai tay trống rồi chuột phải vào người chơi khác để ôm họ. Người được ôm dùng pose ngồi của Minecraft, vẫn có thể nhìn xung quanh bình thường và nhấn phím Sneak (mặc định `Shift`) để tự thoát như khi ride entity.

### Tương thích GSit

Nếu server có GSit, right-click người chơi được điều phối như sau:

- Right-click thường: gọi GSit để ngồi lên đầu người chơi.
- Giữ phím carry (mặc định Sprint/Ctrl) rồi right-click: carry người chơi.

Tích hợp này dùng GSit API và không đóng gói GSit vào plugin. Trong `plugins/GSit/config.yml`, giữ `Options.PlayerSit.allow-player-sit-on-player: true` để API có thể tạo trạng thái ngồi; listener của HaoHanUtilities đã chặn click mặc định trước khi GSit xử lý, tránh tạo hai hành động cùng lúc. Sau khi đổi cấu hình GSit, dùng `/gsitreload` hoặc khởi động lại server.

Người chơi đã tắt carry mode bằng `/hhu toggle off` sẽ không thể bị người khác ôm. Nếu họ tắt mode trong lúc đang được ôm, plugin cho họ xuống ngay lập tức.

### SoulAnchor

Nếu server cài `SoulAnchor plugin`, người chơi có thể carry nguyên một Soul Anchor. Plugin giữ lại:

- UUID của anchor.
- Chủ sở hữu và tên.
- Danh sách người chơi được chia sẻ.

Chỉ chủ sở hữu của anchor mới có thể bưng anchor đi

SoulAnchor là tích hợp tùy chọn; Hao Han Utilities vẫn hoạt động bình thường khi không cài plugin này.

## Block được hỗ trợ

Các block mặc định gồm:

- Chest, trapped chest, barrel và shulker box.
- Furnace, blast furnace, smoker và brewing stand.
- Hopper, dispenser, dropper và crafter.
- Chiseled bookshelf, decorated pot, jukebox, beehive và bee nest.
- Crafting table, smithing table, stonecutter, cartography table, loom, grindstone và enchanting table.

Danh sách có thể chỉnh trong `plugins/HaoHanUtilities/config.yml`.

## An toàn dữ liệu

- Inventory và trạng thái block được lưu bằng snapshot của Minecraft/Paper.
- Mỗi thao tác carry được ghi vào SQLite theo chuỗi `PREPARED → CARRIED → PLACING → PLACED/RESTORED`.
- Nếu server crash hoặc người chơi thoát khi đang carry, trạng thái có thể được nạp lại từ database.
- Dữ liệu được lưu tại `plugins/HaoHanUtilities/carry-blocks.db`.

## EnderChest 54 Ô

- Khi mở EnderChest, giao diện kho đồ nâng cấp lên 54 ô (6 hàng).
- Tự động chuyển toàn bộ vật phẩm từ EnderChest 27 ô ban đầu của người chơi sang kho mới trong lần đầu mở.
- Đồng bộ lại 27 ô đầu tiên về EnderChest vanilla để tương thích hoàn toàn với lệnh `/enderchest` hoặc plugin khác.
- Lưu trữ an toàn trong Persistent Data Container (PDC) của người chơi.

## Đuốc Gây Cháy (Torch Fire)

- Đánh thiêu cháy đối phương hoặc quái vật khi dùng đuốc trên tay chính.
- **Đuốc thường (Torch):** thiêu cháy 3 giây.
- **Đuốc linh hồn (Soul Torch):** thiêu cháy 4 giây với hiệu ứng lửa linh hồn.
- **Đuốc đá đỏ (Redstone Torch):** thiêu cháy 1 giây.
- Tự động tuân thủ quyền bảo vệ vùng (WorldGuard, GriefPrevention, v.v.) và khu vực chống PVP.

## Cài đặt

1. Build hoặc tải `HaoHanUtilities-3.1.0.jar`.
2. Chép file vào thư mục `plugins/` của server.
3. Khởi động lại server.
4. Chỉnh `plugins/HaoHanUtilities/config.yml` nếu cần.

Yêu cầu:

- Paper hoặc Purpur `1.21.11`.
- Java `21`.
- Không sử dụng Bukkit `/reload` để kiểm tra các thao tác carry hoặc recovery.

## Cấu hình nhanh

```yaml
debug: false

placement:
  maximum-distance: 5.0

carrying:
  # Trạng thái của người chơi chưa từng dùng lệnh toggle.
  enabled-by-default: true
  # Phím giữ mặc định khi nhấc vật: sprint hoặc sneak.
  default-activation-key: sprint
  # Tốc độ khi mang vật thường hoặc container rỗng.
  movement-speed-multiplier: 0.75
  # Tốc độ khi container đầy; lượng đồ được nội suy giữa hai mức.
  full-container-movement-speed-multiplier: 0.35

entities:
  enabled: true

players:
  enabled: true

phantom-suppression:
  enabled: true
  remove-existing: true

ender-chest:
  enabled: true
  size: 54

torch-fire:
  enabled: true
  duration-seconds:
    torch: 3
    soul-torch: 4
    redstone-torch: 1
  consume-torch: false

concrete-mixer:
  enabled: true
  require-permission: false
  lower-water-level: true
  effects:
    enabled: true
    splash:
      particles:
        enabled: true
      sound:
        enabled: true
        name: ENTITY_GENERIC_SPLASH
        volume: 0.75
        pitch: 1.0
    transform:
      particles:
        enabled: true
      sound:
        enabled: true
        name: BLOCK_FIRE_EXTINGUISH
        volume: 0.65
        pitch: 1.25
```

## Lệnh

| Lệnh | Mô tả |
| --- | --- |
| `/hhu info` | Hiển thị phiên bản và trạng thái plugin. |
| `/hhu toggle [on\|off]` | Bật/tắt carry mode cá nhân; không truyền tham số sẽ đảo trạng thái. |
| `/hhu bind <sprint\|sneak>` | Chọn control giữ để kích hoạt carry; phím vật lý được đổi trong Controls. |
| `/hhu reload` | Tải lại config/messages và dọn Phantom đã tải. |
| `/hhu status <player>` | Xem giao dịch carry hiện tại của người chơi. |
| `/hhu inspect <carryId>` | Xem chi tiết một giao dịch carry. |
| `/hhu recover <player> original` | Khôi phục vật về vị trí ban đầu. |
| `/hhu recover <player> here` | Khôi phục vật tại vị trí admin đang nhìn. |

Aliases: `/haohanutilities`, `/hhu`, `/carryblocks`, `/carryblock`, `/cb`.

## Build

Windows:

```powershell
.\gradlew.bat clean test build
```

Linux/macOS:

```bash
./gradlew clean test build
```

File deploy được tạo tại:

```text
build/libs/HaoHanUtilities-3.1.0.jar
```
