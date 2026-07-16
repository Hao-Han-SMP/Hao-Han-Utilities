package vn.haohansmp.utilities.carry;

import io.papermc.paper.entity.EntitySerializationFlag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohansmp.utilities.chest.ChestPlacementRules;
import vn.haohansmp.utilities.placement.VanillaPlacementRules;

import java.util.OptionalDouble;
import java.util.UUID;

public final class CarrySnapshotService {
    public static final String VISUAL_ENTITY_TAG = "haohanutilities.carry_visual";

    private final NamespacedKey placedEntityKey;

    public CarrySnapshotService(JavaPlugin plugin) {
        placedEntityKey = new NamespacedKey(plugin, "placed_carry_id");
    }

    public CarryPayload captureBlock(Block block) {
        BlockState snapshot = block.getState();
        ItemStack storedItem = new ItemStack(snapshot.getType());
        if (storedItem.getItemMeta() instanceof BlockStateMeta meta) {
            meta.setBlockState(snapshot);
            storedItem.setItemMeta(meta);
        }
        byte[] itemBytes = storedItem.serializeAsBytes();
        return new CarryPayload(
                CarryKind.BLOCK,
                snapshot.getType().getKey().asString(),
                snapshot.getBlockData().getAsString(),
                itemBytes,
                itemBytes,
                null
        );
    }

    public CarryPayload captureEntity(Entity entity) {
        ItemStack visual = entity.getPickItemStack();
        if (visual == null || visual.getType().isAir()) {
            Material spawnEgg = Material.matchMaterial(entity.getType().name() + "_SPAWN_EGG");
            visual = new ItemStack(spawnEgg == null ? Material.LEAD : spawnEgg);
        }
        byte[] entityBytes = Bukkit.getUnsafe().serializeEntity(
                entity,
                EntitySerializationFlag.PASSENGERS
        );
        return new CarryPayload(
                CarryKind.ENTITY,
                entity.getType().getKey().asString(),
                null,
                entityBytes,
                visual.serializeAsBytes(),
                entity.getUniqueId()
        );
    }

    public OptionalDouble containerFillRatio(CarryPayload payload) {
        if (payload.kind() != CarryKind.BLOCK) {
            return OptionalDouble.empty();
        }
        try {
            ItemStack storedItem = ItemStack.deserializeBytes(payload.data());
            if (!(storedItem.getItemMeta() instanceof BlockStateMeta meta) || !meta.hasBlockState()
                    || !(meta.getBlockState() instanceof Container container)) {
                return OptionalDouble.empty();
            }
            Inventory inventory = container.getSnapshotInventory();
            ItemStack[] contents = inventory.getStorageContents();
            if (contents.length == 0) {
                return OptionalDouble.of(0.0);
            }
            double filledSlots = 0.0;
            for (ItemStack item : contents) {
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                int maximum = Math.max(1, item.getMaxStackSize());
                filledSlots += Math.min(1.0, (double) item.getAmount() / maximum);
            }
            return OptionalDouble.of(Math.min(1.0, filledSlots / contents.length));
        } catch (RuntimeException ignored) {
            return OptionalDouble.empty();
        }
    }

    public void restoreBlock(Block destination, CarryPayload payload) {
        restoreBlock(null, destination, payload);
    }

    public void restoreBlock(Player player, Block destination, CarryPayload payload) {
        restoreBlock(player, destination, payload, null);
    }

    public void restoreBlock(
            Player player,
            Block destination,
            CarryPayload payload,
            BlockFace clickedFace
    ) {
        BlockData placementData = player == null
                ? placementBlockData(payload)
                : placementBlockData(player, payload, clickedFace);
        destination.setBlockData(placementData, false);
        ItemStack storedItem = ItemStack.deserializeBytes(payload.data());
        if (!(storedItem.getItemMeta() instanceof BlockStateMeta meta) || !meta.hasBlockState()) {
            pairChestLikeVanilla(player, destination, clickedFace);
            return;
        }

        BlockState restored = meta.getBlockState().copy(destination.getLocation());
        restored.setBlockData(placementData);
        if (!restored.update(true, false)) {
            throw new IllegalStateException("Cannot restore block state at " + destination.getLocation());
        }
        pairChestLikeVanilla(player, destination, clickedFace);
    }

    public Entity restoreEntity(Location destination, UUID carryId, CarryPayload payload) {
        Entity restored = Bukkit.getUnsafe().deserializeEntity(payload.data(), destination.getWorld(), false, true);
        restored.getPersistentDataContainer().set(
                placedEntityKey,
                PersistentDataType.STRING,
                carryId.toString()
        );
        if (!restored.spawnAt(destination, CreatureSpawnEvent.SpawnReason.CUSTOM)) {
            throw new IllegalStateException("Cannot spawn restored entity at " + destination);
        }
        return restored;
    }

    public Entity spawnVisualEntity(Location location, CarryPayload payload) {
        Entity visual = Bukkit.getUnsafe().deserializeEntity(payload.data(), location.getWorld(), false, true);
        visual.setPersistent(false);
        visual.setInvulnerable(true);
        visual.setSilent(true);
        visual.setGravity(false);
        visual.setNoPhysics(true);
        visual.addScoreboardTag(VISUAL_ENTITY_TAG);
        if (visual instanceof LivingEntity living) {
            living.setAI(false);
            living.setCollidable(false);
            living.setCanPickupItems(false);
        }
        if (!visual.spawnAt(location, CreatureSpawnEvent.SpawnReason.CUSTOM)) {
            throw new IllegalStateException("Cannot spawn carried entity visual at " + location);
        }
        return visual;
    }

    public boolean matchesSourceBlock(Block block, CarryPayload payload) {
        return payload.kind() == CarryKind.BLOCK
                && block.getType().getKey().asString().equals(payload.typeKey())
                && block.getBlockData().getAsString().equals(payload.blockData());
    }

    public boolean matchesPlacedBlock(Block block, CarryPayload payload) {
        if (payload.kind() != CarryKind.BLOCK
                || !block.getType().getKey().asString().equals(payload.typeKey())) {
            return false;
        }
        return true;
    }

    public static org.bukkit.block.data.BlockData placementBlockData(CarryPayload payload) {
        var data = Bukkit.createBlockData(payload.blockData());
        if (data instanceof org.bukkit.block.data.type.Chest chest) {
            chest.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
        }
        return data;
    }

    public static BlockData placementBlockData(Player player, CarryPayload payload) {
        return placementBlockData(player, payload, null);
    }

    public static BlockData placementBlockData(
            Player player,
            CarryPayload payload,
            BlockFace clickedFace
    ) {
        BlockData data = placementBlockData(payload);
        return VanillaPlacementRules.orient(data, player, clickedFace);
    }

    public Entity findPlacedEntity(BlockPosition position, UUID carryId) {
        var world = Bukkit.getWorld(position.worldUuid());
        if (world == null) {
            return null;
        }
        var chunk = world.getChunkAt(position.x() >> 4, position.z() >> 4);
        String expected = carryId.toString();
        for (Entity entity : chunk.getEntities()) {
            String marker = entity.getPersistentDataContainer().get(placedEntityKey, PersistentDataType.STRING);
            if (expected.equals(marker)) {
                return entity;
            }
        }
        return null;
    }

    public void clearPlacedEntityMarker(Entity entity) {
        entity.getPersistentDataContainer().remove(placedEntityKey);
    }

    private static void pairChestLikeVanilla(
            Player player,
            Block destination,
            BlockFace clickedFace
    ) {
        if (player == null
                || !(destination.getBlockData() instanceof org.bukkit.block.data.type.Chest placed)) {
            return;
        }

        if (player.isSneaking()) {
            pairSneakingChest(destination, placed, clickedFace);
            return;
        }

        BlockFace facing = placed.getFacing();
        BlockFace clockwise = ChestPlacementRules.clockwise(facing);
        Block partner = singleMatchingChest(destination.getRelative(clockwise), destination, facing);
        org.bukkit.block.data.type.Chest.Type placedType =
                ChestPlacementRules.typeForPartner(facing, clockwise);

        if (partner == null) {
            BlockFace counterClockwise = ChestPlacementRules.counterClockwise(facing);
            partner = singleMatchingChest(destination.getRelative(counterClockwise), destination, facing);
            placedType = ChestPlacementRules.typeForPartner(facing, counterClockwise);
        }
        if (partner == null) {
            return;
        }

        org.bukkit.block.data.type.Chest partnerData =
                (org.bukkit.block.data.type.Chest) partner.getBlockData();
        placed.setType(placedType);
        partnerData.setType(ChestPlacementRules.opposite(placedType));
        destination.setBlockData(placed, false);
        partner.setBlockData(partnerData, false);
    }

    private static void pairSneakingChest(
            Block destination,
            org.bukkit.block.data.type.Chest placed,
            BlockFace clickedFace
    ) {
        if (!ChestPlacementRules.isHorizontal(clickedFace)) {
            return;
        }

        BlockFace directionToPartner = clickedFace.getOppositeFace();
        Block partner = destination.getRelative(directionToPartner);
        if (partner.getType() != destination.getType()
                || !(partner.getBlockData() instanceof org.bukkit.block.data.type.Chest partnerData)
                || partnerData.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE
                || partnerData.getFacing().getModX() == clickedFace.getModX()
                || partnerData.getFacing().getModZ() == clickedFace.getModZ()) {
            return;
        }

        org.bukkit.block.data.type.Chest.Type placedType =
                ChestPlacementRules.typeForPartner(partnerData.getFacing(), directionToPartner);
        if (placedType == org.bukkit.block.data.type.Chest.Type.SINGLE) {
            return;
        }
        placed.setFacing(partnerData.getFacing());
        placed.setType(placedType);
        partnerData.setType(ChestPlacementRules.opposite(placedType));
        destination.setBlockData(placed, false);
        partner.setBlockData(partnerData, false);
    }

    private static Block singleMatchingChest(
            Block candidate,
            Block destination,
            BlockFace facing
    ) {
        if (candidate.getType() != destination.getType()
                || !(candidate.getBlockData() instanceof org.bukkit.block.data.type.Chest chest)
                || chest.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE
                || chest.getFacing() != facing) {
            return null;
        }
        return candidate;
    }

}
