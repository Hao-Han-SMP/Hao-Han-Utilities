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

Hao Han Utilities là plugin Paper/Purpur `1.21.11` tập trung vào hai tính năng:

- **Carry:** người chơi có thể nhấc block chức năng hoặc động vật lên, mang đến vị trí khác rồi đặt xuống.
- **Phantom Suppression:** ngăn Phantom xuất hiện và xóa Phantom đang tồn tại trong các world đã tải.

Plugin hoàn toàn server-side, không yêu cầu mod client hoặc resource pack.

## Cách sử dụng Carry

### Nhấc vật

1. Đảm bảo cả tay chính và tay phụ đều trống.
2. Giữ phím sprint (mặc định là `Ctrl`).
3. Chuột phải vào block hoặc động vật muốn nhấc.

Nếu tay đang có đồ hoặc block không hỗ trợ carry, plugin không chặn thao tác và không gửi thông báo; Minecraft sẽ đặt block hoặc tương tác như bình thường.

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

## Cài đặt

1. Build hoặc tải `HaoHanUtilities-2.0.0.jar`.
2. Chép file vào thư mục `plugins/` của server.
3. Khởi động lại server.
4. Chỉnh `plugins/HaoHanUtilities/config.yml` nếu cần.

Yêu cầu:

- Paper hoặc Purpur `1.21.11`.
- Java `21`.
- Không sử dụng Bukkit `/reload` để kiểm tra các thao tác carry hoặc recovery.

## Cấu hình nhanh

```yaml
carrying:
  # Tốc độ khi mang vật thường hoặc container rỗng.
  movement-speed-multiplier: 0.75
  # Tốc độ khi container đầy; lượng đồ được nội suy giữa hai mức.
  full-container-movement-speed-multiplier: 0.35

entities:
  enabled: true

phantom-suppression:
  enabled: true
  remove-existing: true
```

## Lệnh

| Lệnh | Mô tả |
| --- | --- |
| `/hhu info` | Hiển thị phiên bản và trạng thái plugin. |
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
build/libs/HaoHanUtilities-2.0.0.jar
```
