package vn.haohansmp.utilities.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohansmp.utilities.carry.CarryKind;
import vn.haohansmp.utilities.carry.CarryPayload;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Optional adapter for the SoulAnchor 1.0.3 project. Reflection keeps
 * HaoHanUtilities loadable when SoulAnchor is not installed.
 */
public final class SoulAnchorIntegration {
    private static final String PLUGIN_NAME = "SoulAnchor";
    private static final String EXPECTED_MAIN = "dev.haohansmp.soulanchor.SoulAnchorPlugin";
    private static final String TYPE_KEY = "soulanchor:soul_anchor";
    private static final int SNAPSHOT_VERSION = 1;

    private final JavaPlugin plugin;
    private boolean incompatibilityLogged;
    private boolean incompatible;

    public SoulAnchorIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        Plugin anchorPlugin = anchorPlugin();
        if (anchorPlugin == null) {
            return;
        }
        try {
            Class<?> mainClass = anchorPlugin.getClass();
            Class<?> anchorClass = Class.forName(
                    mainClass.getName() + "$Anchor",
                    true,
                    mainClass.getClassLoader()
            );
            requireMethod(mainClass, "anchorAt", Block.class);
            requireMethod(mainClass, "anchorFromEntity", Entity.class);
            requireMethod(mainClass, "createAnchorDisplayItem");
            requireMethod(mainClass, "removeAnchor", UUID.class);
            requireMethod(mainClass, "cancelWarmupsTouching", UUID.class);
            requireMethod(mainClass, "spawnVisuals", anchorClass);
            requireMethod(mainClass, "saveAnchors");
            requireMethod(mainClass, "getAnchorBlockMaterial");
            requireMethod(mainClass, "locationKey", Location.class);
            requireField(mainClass, "anchorsById");
            requireField(mainClass, "anchorIdsByLocation");
            anchorClass.getDeclaredConstructor(
                    UUID.class,
                    UUID.class,
                    String.class,
                    Location.class,
                    float.class,
                    float.class,
                    long.class,
                    Set.class,
                    UUID.class,
                    UUID.class
            );
            plugin.getLogger().info("SoulAnchor 1.0.3 carry integration enabled.");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logIncompatibility(new IllegalStateException("compatibility check failed", exception));
        }
    }

    public boolean isAvailable() {
        return anchorPlugin() != null;
    }

    public boolean isAnchor(Block block) {
        Plugin anchorPlugin = anchorPlugin();
        if (anchorPlugin == null) {
            return false;
        }
        try {
            return anchorAt(anchorPlugin, block).isPresent();
        } catch (RuntimeException exception) {
            logIncompatibility(exception);
            return false;
        }
    }

    public Block backingBlock(Entity entity) {
        Plugin anchorPlugin = anchorPlugin();
        if (anchorPlugin == null) {
            return null;
        }
        try {
            Optional<Object> anchor = anchorFromEntity(anchorPlugin, entity);
            if (anchor.isEmpty()) {
                return null;
            }
            Location location = (Location) invokeAccessor(anchor.get(), "location");
            return location.getBlock();
        } catch (RuntimeException exception) {
            logIncompatibility(exception);
            return null;
        }
    }

    public String validatePickup(Player player, Block block) {
        Plugin anchorPlugin = requirePlugin();
        Object anchor = anchorAt(anchorPlugin, block)
                .orElseThrow(() -> new IllegalStateException("Soul Anchor is no longer present"));
        UUID ownerId = (UUID) invokeAccessor(anchor, "ownerId");
        if (!ownerId.equals(player.getUniqueId())) {
            return "anchor-not-owner";
        }
        return null;
    }

    public CarryPayload capture(Block block) {
        Plugin anchorPlugin = requirePlugin();
        Object anchor = anchorAt(anchorPlugin, block)
                .orElseThrow(() -> new IllegalStateException("Soul Anchor is no longer present"));
        AnchorSnapshot snapshot = snapshot(anchor);
        ItemStack visual = (ItemStack) invoke(anchorPlugin, "createAnchorDisplayItem", new Class<?>[0]);
        return new CarryPayload(
                CarryKind.SOUL_ANCHOR,
                TYPE_KEY,
                block.getBlockData().getAsString(),
                encode(snapshot),
                visual.serializeAsBytes(),
                null
        );
    }

    public boolean runProtectionProbe(Block block, BooleanSupplier probe) {
        Plugin anchorPlugin = requirePlugin();
        String key = locationKey(anchorPlugin, block.getLocation());
        Map<String, UUID> locations = anchorIdsByLocation(anchorPlugin);
        UUID anchorId = locations.remove(key);
        if (anchorId == null) {
            throw new IllegalStateException("Soul Anchor is no longer present");
        }
        try {
            return probe.getAsBoolean();
        } finally {
            locations.putIfAbsent(key, anchorId);
        }
    }

    public void removeForCarry(Block block, CarryPayload payload) {
        Plugin anchorPlugin = requirePlugin();
        AnchorSnapshot expected = decode(payload);
        Object anchor = anchorAt(anchorPlugin, block)
                .orElseThrow(() -> new IllegalStateException("Soul Anchor is no longer present"));
        UUID actualId = (UUID) invokeAccessor(anchor, "id");
        if (!expected.id().equals(actualId)) {
            throw new IllegalStateException("Soul Anchor ID changed before pickup");
        }
        invoke(anchorPlugin, "removeAnchor", new Class<?>[] {UUID.class}, expected.id());
        invoke(anchorPlugin, "cancelWarmupsTouching", new Class<?>[] {UUID.class}, expected.id());
    }

    public void restore(Block destination, CarryPayload payload) {
        Plugin anchorPlugin = requirePlugin();
        AnchorSnapshot snapshot = decode(payload);

        Object existingById = anchorsById(anchorPlugin).get(snapshot.id());
        if (existingById != null) {
            Location existingLocation = (Location) invokeAccessor(existingById, "location");
            if (sameBlock(existingLocation, destination.getLocation())) {
                ensureBackingBlock(anchorPlugin, destination);
                return;
            }
            throw new IllegalStateException("Soul Anchor ID already exists at another location");
        }
        if (anchorAt(anchorPlugin, destination).isPresent()) {
            throw new IllegalStateException("Destination already contains another Soul Anchor");
        }

        ensureBackingBlock(anchorPlugin, destination);
        Object anchor = constructAnchor(anchorPlugin, snapshot, destination.getLocation());
        Object spawned = invoke(
                anchorPlugin,
                "spawnVisuals",
                new Class<?>[] {anchor.getClass()},
                anchor
        );

        anchorsById(anchorPlugin).put(snapshot.id(), spawned);
        anchorIdsByLocation(anchorPlugin).put(
                locationKey(anchorPlugin, destination.getLocation()),
                snapshot.id()
        );
        invoke(anchorPlugin, "saveAnchors", new Class<?>[0]);
    }

    public boolean matches(Block block, CarryPayload payload) {
        if (!isAvailable()) {
            return false;
        }
        AnchorSnapshot snapshot = decode(payload);
        return anchorAt(requirePlugin(), block)
                .map(anchor -> snapshot.id().equals(invokeAccessor(anchor, "id")))
                .orElse(false);
    }

    public boolean matchesBackingBlock(Block block, CarryPayload payload) {
        if (payload.kind() != CarryKind.SOUL_ANCHOR || payload.blockData() == null) {
            return false;
        }
        try {
            Material expected = Bukkit.createBlockData(payload.blockData()).getMaterial();
            return block.getType() == expected;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private Plugin anchorPlugin() {
        if (incompatible) {
            return null;
        }
        Plugin found = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (found == null || !found.isEnabled()) {
            return null;
        }
        if (!EXPECTED_MAIN.equals(found.getClass().getName())) {
            logIncompatibility(new IllegalStateException("Unexpected main class: " + found.getClass().getName()));
            return null;
        }
        return found;
    }

    private Plugin requirePlugin() {
        Plugin found = anchorPlugin();
        if (found == null) {
            throw new IllegalStateException("SoulAnchor integration is unavailable");
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    private static Optional<Object> anchorAt(Plugin plugin, Block block) {
        return (Optional<Object>) invoke(plugin, "anchorAt", new Class<?>[] {Block.class}, block);
    }

    @SuppressWarnings("unchecked")
    private static Optional<Object> anchorFromEntity(Plugin plugin, Entity entity) {
        return (Optional<Object>) invoke(plugin, "anchorFromEntity", new Class<?>[] {Entity.class}, entity);
    }

    private static Object constructAnchor(Plugin plugin, AnchorSnapshot snapshot, Location location) {
        try {
            Class<?> anchorClass = Class.forName(
                    plugin.getClass().getName() + "$Anchor",
                    true,
                    plugin.getClass().getClassLoader()
            );
            Constructor<?> constructor = anchorClass.getDeclaredConstructor(
                    UUID.class,
                    UUID.class,
                    String.class,
                    Location.class,
                    float.class,
                    float.class,
                    long.class,
                    Set.class,
                    UUID.class,
                    UUID.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(
                    snapshot.id(),
                    snapshot.ownerId(),
                    snapshot.name(),
                    location.toBlockLocation(),
                    snapshot.yaw(),
                    snapshot.pitch(),
                    snapshot.createdAt(),
                    snapshot.sharedWith(),
                    null,
                    null
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot construct Soul Anchor snapshot", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> anchorsById(Plugin plugin) {
        return (Map<UUID, Object>) readField(plugin, "anchorsById");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, UUID> anchorIdsByLocation(Plugin plugin) {
        return (Map<String, UUID>) readField(plugin, "anchorIdsByLocation");
    }

    private static String locationKey(Plugin plugin, Location location) {
        return (String) invoke(plugin, "locationKey", new Class<?>[] {Location.class}, location);
    }

    private static void ensureBackingBlock(Plugin plugin, Block block) {
        Material material = (Material) invoke(plugin, "getAnchorBlockMaterial", new Class<?>[0]);
        block.setType(material, false);
    }

    private static AnchorSnapshot snapshot(Object anchor) {
        @SuppressWarnings("unchecked")
        Set<UUID> sharedWith = (Set<UUID>) invokeAccessor(anchor, "sharedWith");
        return new AnchorSnapshot(
                (UUID) invokeAccessor(anchor, "id"),
                (UUID) invokeAccessor(anchor, "ownerId"),
                (String) invokeAccessor(anchor, "name"),
                (float) invokeAccessor(anchor, "yaw"),
                (float) invokeAccessor(anchor, "pitch"),
                (long) invokeAccessor(anchor, "createdAt"),
                sharedWith
        );
    }

    private static byte[] encode(AnchorSnapshot snapshot) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(SNAPSHOT_VERSION);
                writeUuid(output, snapshot.id());
                writeUuid(output, snapshot.ownerId());
                output.writeUTF(snapshot.name());
                output.writeFloat(snapshot.yaw());
                output.writeFloat(snapshot.pitch());
                output.writeLong(snapshot.createdAt());
                output.writeInt(snapshot.sharedWith().size());
                for (UUID playerId : snapshot.sharedWith()) {
                    writeUuid(output, playerId);
                }
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot encode Soul Anchor", exception);
        }
    }

    private static AnchorSnapshot decode(CarryPayload payload) {
        if (payload.kind() != CarryKind.SOUL_ANCHOR) {
            throw new IllegalArgumentException("Payload is not a Soul Anchor");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload.data()))) {
            int version = input.readInt();
            if (version != SNAPSHOT_VERSION) {
                throw new IllegalStateException("Unsupported Soul Anchor snapshot version: " + version);
            }
            UUID id = readUuid(input);
            UUID ownerId = readUuid(input);
            String name = input.readUTF();
            float yaw = input.readFloat();
            float pitch = input.readFloat();
            long createdAt = input.readLong();
            int sharedCount = input.readInt();
            if (sharedCount < 0 || sharedCount > 10_000) {
                throw new IllegalStateException("Invalid Soul Anchor shared player count");
            }
            Set<UUID> sharedWith = new HashSet<>();
            for (int index = 0; index < sharedCount; index++) {
                sharedWith.add(readUuid(input));
            }
            return new AnchorSnapshot(id, ownerId, name, yaw, pitch, createdAt, sharedWith);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot decode Soul Anchor", exception);
        }
    }

    private static Object invokeAccessor(Object target, String name) {
        return invoke(target, name, new Class<?>[0]);
    }

    private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("SoulAnchor method failed: " + name, cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("SoulAnchor method is unavailable: " + name, exception);
        }
    }

    private static Object readField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("SoulAnchor field is unavailable: " + name, exception);
        }
    }

    private static Method requireMethod(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static Field requireField(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private void logIncompatibility(RuntimeException exception) {
        incompatible = true;
        if (incompatibilityLogged) {
            return;
        }
        incompatibilityLogged = true;
        plugin.getLogger().warning(
                "SoulAnchor integration disabled because its internal API is incompatible: "
                        + exception.getMessage()
        );
    }

    private static boolean sameBlock(Location left, Location right) {
        return left.getWorld() != null
                && right.getWorld() != null
                && left.getWorld().getUID().equals(right.getWorld().getUID())
                && left.getBlockX() == right.getBlockX()
                && left.getBlockY() == right.getBlockY()
                && left.getBlockZ() == right.getBlockZ();
    }

    private static void writeUuid(DataOutputStream output, UUID uuid) throws IOException {
        output.writeLong(uuid.getMostSignificantBits());
        output.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private record AnchorSnapshot(
            UUID id,
            UUID ownerId,
            String name,
            float yaw,
            float pitch,
            long createdAt,
            Set<UUID> sharedWith
    ) {
        private AnchorSnapshot {
            sharedWith = Set.copyOf(sharedWith);
        }
    }
}
