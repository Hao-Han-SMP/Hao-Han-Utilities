package vn.haohansmp.utilities.enderchest;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.Lidded;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import vn.haohansmp.utilities.protection.ProtectionService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EnderChestListener implements Listener {
    private final Plugin plugin;
    private final ProtectionService protectionService;
    private final NamespacedKey pdcKey;
    private final Map<Location, Integer> activeViewers = new ConcurrentHashMap<>();

    public EnderChestListener(Plugin plugin, ProtectionService protectionService) {
        this.plugin = plugin;
        this.protectionService = protectionService;
        this.pdcKey = new NamespacedKey(plugin, "ender_chest_items");
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("ender-chest.enabled", true);
    }

    public int getEnderChestSize() {
        int size = plugin.getConfig().getInt("ender-chest.size", 54);
        if (size % 9 != 0 || size < 9 || size > 54) {
            return 54;
        }
        return size;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isEnabled()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST) return;
        if (event.useInteractedBlock() == Event.Result.DENY) return;

        Player player = event.getPlayer();
        if (protectionService != null && !protectionService.canBreakBlock(player, block)) {
            return;
        }

        event.setCancelled(true);
        open54SlotEnderChest(player, block.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!isEnabled()) return;
        if (event.getInventory().getType() != InventoryType.ENDER_CHEST) return;
        if (event.getInventory().getHolder() instanceof EnderChestHolder) return;

        if (!(event.getPlayer() instanceof Player player)) return;

        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> open54SlotEnderChest(player, null));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof EnderChestHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        ItemStack[] contents = event.getInventory().getContents();
        savePlayerEnderChest(player, contents);

        Location blockLoc = holder.getBlockLocation();
        if (blockLoc != null) {
            activeViewers.compute(blockLoc, (loc, count) -> (count == null || count <= 1) ? null : count - 1);
            if (!activeViewers.containsKey(blockLoc)) {
                if (blockLoc.getBlock().getType() == Material.ENDER_CHEST
                        && blockLoc.getBlock().getState() instanceof Lidded lidded) {
                    lidded.close();
                }
                player.playSound(blockLoc, Sound.BLOCK_ENDER_CHEST_CLOSE, SoundCategory.BLOCKS, 0.5f, 1.0f);
            }
        }
    }

    public void open54SlotEnderChest(Player player, Location blockLocation) {
        int size = getEnderChestSize();
        EnderChestHolder holder = new EnderChestHolder(player, blockLocation);
        Component title = Component.translatable("container.enderchest");
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        ItemStack[] contents = loadPlayerEnderChest(player, size);
        inv.setContents(contents);

        player.openInventory(inv);

        if (blockLocation != null) {
            activeViewers.merge(blockLocation, 1, Integer::sum);
            if (blockLocation.getBlock().getType() == Material.ENDER_CHEST
                    && blockLocation.getBlock().getState() instanceof Lidded lidded) {
                lidded.open();
            }
            player.playSound(blockLocation, Sound.BLOCK_ENDER_CHEST_OPEN, SoundCategory.BLOCKS, 0.5f, 1.0f);
        }
    }

    public ItemStack[] loadPlayerEnderChest(Player player, int size) {
        byte[] bytes = player.getPersistentDataContainer().get(pdcKey, PersistentDataType.BYTE_ARRAY);
        if (bytes != null && bytes.length > 0) {
            ItemStack[] deserialized = deserializeItemStacks(bytes, size);
            if (deserialized != null && deserialized.length == size) {
                return deserialized;
            }
        }

        // Migration from legacy 27-slot vanilla EnderChest
        ItemStack[] newContents = new ItemStack[size];
        ItemStack[] vanillaContents = player.getEnderChest().getContents();
        int copyLength = Math.min(vanillaContents.length, size);
        System.arraycopy(vanillaContents, 0, newContents, 0, copyLength);

        // Save migrated data immediately
        savePlayerEnderChest(player, newContents);
        return newContents;
    }

    public void savePlayerEnderChest(Player player, ItemStack[] contents) {
        byte[] bytes = serializeItemStacks(contents);
        if (bytes != null) {
            player.getPersistentDataContainer().set(pdcKey, PersistentDataType.BYTE_ARRAY, bytes);
        }

        // Sync first 27 slots back to vanilla EnderChest for external plugin compatibility
        ItemStack[] vanillaCopy = Arrays.copyOfRange(contents, 0, Math.min(27, contents.length));
        player.getEnderChest().setContents(vanillaCopy);
    }

    public static byte[] serializeItemStacks(ItemStack[] items) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos)) {
            boos.writeInt(items.length);
            for (ItemStack item : items) {
                boos.writeObject(item);
            }
            boos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    public static ItemStack[] deserializeItemStacks(byte[] bytes, int expectedSize) {
        if (bytes == null || bytes.length == 0) return new ItemStack[expectedSize];
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream bois = new BukkitObjectInputStream(bais)) {
            int length = bois.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) bois.readObject();
            }
            if (length != expectedSize) {
                items = Arrays.copyOf(items, expectedSize);
            }
            return items;
        } catch (IOException | ClassNotFoundException e) {
            return new ItemStack[expectedSize];
        }
    }
}
