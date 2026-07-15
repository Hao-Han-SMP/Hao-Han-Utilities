package vn.haohansmp.utilities.chest;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohansmp.utilities.carry.CarryService;
import vn.haohansmp.utilities.config.MessageService;
import vn.haohansmp.utilities.protection.ProtectionService;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class ChestCarryService {
    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    private final JavaPlugin plugin;
    private final CarryService carryService;
    private final ProtectionService protection;
    private final MessageService messages;
    private final NamespacedKey carriedChestKey;

    public ChestCarryService(JavaPlugin plugin, CarryService carryService,
                             ProtectionService protection, MessageService messages) {
        this.plugin = plugin;
        this.carryService = carryService;
        this.protection = protection;
        this.messages = messages;
        this.carriedChestKey = new NamespacedKey(plugin, "carried_chest");
    }

    public boolean isChest(Block block) {
        return block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST;
    }

    public void pickup(Player player, Block selectedBlock) {
        Material selectedMaterial = selectedBlock.getType();
        if (!plugin.getConfig().getBoolean("chest-carry.enabled", true)) {
            messages.send(player, "chest-carry-disabled");
            return;
        }
        if (!player.hasPermission("haohanutilities.carry.use")) {
            messages.send(player, "no-permission");
            return;
        }
        if (carryService.isCarrying(player.getUniqueId())) {
            messages.send(player, "already-carrying");
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
            messages.send(player, "invalid-player-state");
            return;
        }
        if (!worldAllowed(selectedBlock.getWorld())) {
            messages.send(player, "world-disabled");
            return;
        }
        int inventorySlot = player.getInventory().firstEmpty();
        if (inventorySlot < 0) {
            messages.send(player, "chest-inventory-full");
            return;
        }

        BlockState selectedState = selectedBlock.getState();
        if (!(selectedState instanceof Chest selectedChest)
                || !(selectedBlock.getBlockData() instanceof org.bukkit.block.data.type.Chest selectedData)) {
            messages.send(player, "unsupported-block");
            return;
        }
        if (!selectedChest.getInventory().getViewers().isEmpty()) {
            messages.send(player, "container-in-use");
            return;
        }

        Optional<Block> otherBlock = findOtherHalf(selectedBlock, selectedData);
        Chest otherChest = null;
        org.bukkit.block.data.type.Chest otherData = null;
        if (otherBlock.isPresent()) {
            BlockState otherState = otherBlock.get().getState();
            if (!(otherState instanceof Chest chest)
                    || !(otherBlock.get().getBlockData() instanceof org.bukkit.block.data.type.Chest data)) {
                messages.send(player, "chest-pair-invalid");
                return;
            }
            otherChest = chest;
            otherData = data;
        } else if (selectedData.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE) {
            messages.send(player, "chest-pair-invalid");
            return;
        }

        UUID operationId = UUID.randomUUID();
        if (!protection.canPickup(player, selectedBlock, operationId)
                || (otherBlock.isPresent() && !protection.canPickup(player, otherBlock.get(), operationId))) {
            messages.send(player, "no-permission");
            return;
        }

        // Protection listeners are allowed to mutate state. Re-read both halves before the destructive step.
        if (!(selectedBlock.getState() instanceof Chest freshSelectedChest)
                || !(selectedBlock.getBlockData() instanceof org.bukkit.block.data.type.Chest freshSelectedData)) {
            messages.send(player, "chest-pair-invalid");
            return;
        }
        Optional<Block> freshOtherBlock = findOtherHalf(selectedBlock, freshSelectedData);
        ItemStack reservedSlotItem = player.getInventory().getItem(inventorySlot);
        if (!sameBlock(otherBlock, freshOtherBlock)
                || !freshSelectedChest.getInventory().getViewers().isEmpty()
                || (reservedSlotItem != null && !reservedSlotItem.getType().isAir())) {
            messages.send(player, "chest-state-changed");
            return;
        }
        selectedChest = freshSelectedChest;
        selectedData = freshSelectedData;
        otherBlock = freshOtherBlock;
        if (otherBlock.isPresent()) {
            if (!(otherBlock.get().getState() instanceof Chest freshOtherChest)
                    || !(otherBlock.get().getBlockData() instanceof org.bukkit.block.data.type.Chest freshOtherData)) {
                messages.send(player, "chest-pair-invalid");
                return;
            }
            otherChest = freshOtherChest;
            otherData = freshOtherData;
        }

        ChestSnapshot selectedSnapshot;
        ChestSnapshot otherSnapshot = null;
        BlockData selectedOriginalData = selectedBlock.getBlockData().clone();
        BlockData otherOriginalData = otherBlock.map(block -> block.getBlockData().clone()).orElse(null);
        try {
            selectedSnapshot = ChestSnapshot.capture(selectedChest);
            if (otherChest != null) {
                otherSnapshot = ChestSnapshot.capture(otherChest);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Cannot capture chest state [operationId=" + operationId + "]", exception);
            messages.send(player, "snapshot-error");
            return;
        }

        ItemStack carriedItem;
        try {
            carriedItem = createCarriedItem(selectedMaterial, selectedSnapshot);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Cannot create carried chest item [operationId=" + operationId + "]", exception);
            messages.send(player, "snapshot-error");
            return;
        }

        try {
            selectedBlock.setType(Material.AIR, false);
            if (otherBlock.isPresent()) {
                org.bukkit.block.data.type.Chest singleData =
                        (org.bukkit.block.data.type.Chest) otherData.clone();
                singleData.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
                otherBlock.get().setBlockData(singleData, false);
                Chest repaired = requireChest(otherBlock.get());
                otherSnapshot.applyTo(repaired);
                if (!repaired.update(true, false)) {
                    throw new IllegalStateException("Cannot update remaining half of double chest");
                }
            }
            player.getInventory().setItem(inventorySlot, carriedItem);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Chest move failed; rolling back [operationId=" + operationId + "]", exception);
            try {
                player.getInventory().setItem(inventorySlot, null);
                restoreBlock(selectedBlock, selectedOriginalData, selectedSnapshot);
                if (otherBlock.isPresent() && otherOriginalData != null && otherSnapshot != null) {
                    restoreBlock(otherBlock.get(), otherOriginalData, otherSnapshot);
                }
            } catch (RuntimeException rollbackFailure) {
                plugin.getLogger().log(Level.SEVERE,
                        "Chest move rollback failed [operationId=" + operationId + "]", rollbackFailure);
            }
            messages.send(player, "chest-pickup-failed");
            return;
        }

        plugin.getLogger().info("AUDIT chestMove operationId=" + operationId
                + " player=" + player.getName() + "/" + player.getUniqueId()
                + " material=" + selectedMaterial
                + " original=" + selectedBlock.getWorld().getUID() + ":"
                + selectedBlock.getX() + "," + selectedBlock.getY() + "," + selectedBlock.getZ()
                + " double=" + otherBlock.isPresent());
        messages.send(player, "chest-pickup-success", Map.of(
                "chest", selectedMaterial == Material.TRAPPED_CHEST ? "trapped chest" : "chest"
        ));
    }

    public boolean isCarriedChestItem(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof BlockStateMeta meta)) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(carriedChestKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public void restorePlacedState(Player player, Block placedBlock, ItemStack sourceItem) {
        if (!isCarriedChestItem(sourceItem)
                || !(sourceItem.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof Chest storedChest)) {
            return;
        }
        try {
            ChestSnapshot snapshot = ChestSnapshot.capture(storedChest);
            Chest placedChest = requireChest(placedBlock);
            snapshot.applyTo(placedChest);
            if (!placedChest.update(true, false)) {
                throw new IllegalStateException("Cannot apply carried chest state to placed block");
            }
            messages.send(player, "chest-place-success");
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Cannot restore carried chest item at "
                    + placedBlock.getLocation(), exception);
            throw exception;
        }
    }

    private ItemStack createCarriedItem(Material material, ChestSnapshot snapshot) {
        ItemStack item = new ItemStack(material);
        if (!(item.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof Chest itemChest)) {
            throw new IllegalStateException("Chest item does not expose BlockStateMeta");
        }
        snapshot.applyTo(itemChest);
        meta.setBlockState(itemChest);
        meta.getPersistentDataContainer().set(carriedChestKey, PersistentDataType.BYTE, (byte) 1);
        if (snapshot.customName() != null) {
            meta.customName(snapshot.customName());
        }
        item.setItemMeta(meta);
        return item;
    }

    private Optional<Block> findOtherHalf(Block selectedBlock, org.bukkit.block.data.type.Chest selectedData) {
        if (selectedData.getType() == org.bukkit.block.data.type.Chest.Type.SINGLE) {
            return Optional.empty();
        }
        for (BlockFace face : HORIZONTAL_FACES) {
            Block candidate = selectedBlock.getRelative(face);
            if (candidate.getBlockData() instanceof org.bukkit.block.data.type.Chest candidateData
                    && ChestPairRules.matchesOtherHalf(
                    selectedBlock.getType(), selectedData.getType(), selectedData.getFacing(),
                    candidate.getType(), candidateData.getType(), candidateData.getFacing())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static Chest requireChest(Block block) {
        if (!(block.getState() instanceof Chest chest)) {
            throw new IllegalStateException("Expected chest at " + block.getLocation());
        }
        return chest;
    }

    private static boolean sameBlock(Optional<Block> first, Optional<Block> second) {
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        Block left = first.get();
        Block right = second.get();
        return left.getWorld().getUID().equals(right.getWorld().getUID())
                && left.getX() == right.getX()
                && left.getY() == right.getY()
                && left.getZ() == right.getZ();
    }

    private static void restoreBlock(Block block, BlockData data, ChestSnapshot snapshot) {
        block.setBlockData(data, false);
        Chest restored = requireChest(block);
        snapshot.applyTo(restored);
        if (!restored.update(true, false)) {
            throw new IllegalStateException("Cannot restore chest during rollback at " + block.getLocation());
        }
    }

    private boolean worldAllowed(World world) {
        boolean listed = plugin.getConfig().getStringList("worlds.list").contains(world.getName());
        String mode = plugin.getConfig().getString("worlds.mode", "BLACKLIST").toUpperCase(Locale.ROOT);
        return "WHITELIST".equals(mode) ? listed : !listed;
    }
}
