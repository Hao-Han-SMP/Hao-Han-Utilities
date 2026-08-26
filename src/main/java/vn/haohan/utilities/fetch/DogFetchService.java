package vn.haohan.utilities.fetch;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DogFetchService {
    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, FetchSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, ExcitedDogSession> excitedDogs = new ConcurrentHashMap<>();
    private final Map<UUID, ThrowChargeSession> chargingSessions = new ConcurrentHashMap<>();
    private BukkitTask task;
    private int tickCount = 0;

    public DogFetchService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        for (ThrowChargeSession session : chargingSessions.values()) {
            Player player = Bukkit.getPlayer(session.getPlayerUuid());
            if (player != null && player.isOnline()) {
                player.sendActionBar(Component.empty());
            }
        }
        chargingSessions.clear();

        for (FetchSession session : activeSessions.values()) {
            if (session.getWolfUuid() != null) {
                Wolf wolf = getWolf(session.getWolfUuid());
                if (wolf != null) {
                    session.dropStickIfHolding(wolf);
                    wolf.getPathfinder().stopPathfinding();
                } else {
                    session.cleanupVisual();
                }
            } else {
                session.cleanupVisual();
            }
        }
        activeSessions.clear();

        for (ExcitedDogSession session : excitedDogs.values()) {
            Wolf wolf = getWolf(session.getWolfUuid());
            if (wolf != null) {
                wolf.getPathfinder().stopPathfinding();
            }
        }
        excitedDogs.clear();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("dog-fetch.enabled", true);
    }

    public boolean isExcitedWaitEnabled() {
        return plugin.getConfig().getBoolean("dog-fetch.excited-wait.enabled", true);
    }

    public double getExcitedWaitSpeedMultiplier() {
        return plugin.getConfig().getDouble("dog-fetch.excited-wait.speed-multiplier", 1.45);
    }

    public double getExcitedCircleRadius() {
        return plugin.getConfig().getDouble("dog-fetch.excited-wait.circle-radius", 2.6);
    }

    public double getSearchRadius() {
        return plugin.getConfig().getDouble("dog-fetch.search-radius", 32.0);
    }

    public double getMinThrowVelocity() {
        return plugin.getConfig().getDouble("dog-fetch.min-throw-velocity", 0.55);
    }

    public double getMaxThrowVelocity() {
        return plugin.getConfig().getDouble("dog-fetch.max-throw-velocity", 1.85);
    }

    public int getMaxChargeTicks() {
        return plugin.getConfig().getInt("dog-fetch.max-charge-ticks", 24);
    }

    public double getRunSpeedMultiplier() {
        return plugin.getConfig().getDouble("dog-fetch.run-speed-multiplier", 1.55);
    }

    public double getReturnSpeedMultiplier() {
        return plugin.getConfig().getDouble("dog-fetch.return-speed-multiplier", 1.40);
    }

    public boolean isShowStickInMouth() {
        return plugin.getConfig().getBoolean("dog-fetch.show-stick-in-mouth", true);
    }

    public boolean isFetching(Wolf wolf) {
        if (wolf == null) return false;
        return activeSessions.containsKey(wolf.getUniqueId());
    }

    public boolean isTrackedStick(Item item) {
        if (item == null) return false;
        for (FetchSession session : activeSessions.values()) {
            if (session.getStickItem() != null && session.getStickItem().equals(item)) {
                return true;
            }
        }
        return false;
    }

    public void startCharging(Player player) {
        if (!isEnabled()) return;
        ThrowChargeSession session = chargingSessions.computeIfAbsent(
                player.getUniqueId(),
                uuid -> new ThrowChargeSession(uuid, tickCount)
        );
        session.refreshInteract(tickCount);
        player.playSound(player.getEyeLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 0.45f, 1.2f);
    }

    public void refreshCharging(Player player) {
        if (!isEnabled()) return;
        ThrowChargeSession session = chargingSessions.get(player.getUniqueId());
        if (session != null) {
            session.refreshInteract(tickCount);
        } else {
            startCharging(player);
        }
    }

    public void cancelCharging(Player player) {
        ThrowChargeSession session = chargingSessions.remove(player.getUniqueId());
        if (session != null && player.isOnline()) {
            player.sendActionBar(Component.empty());
        }
    }

    public void handleSneakRelease(Player player) {
        ThrowChargeSession session = chargingSessions.remove(player.getUniqueId());
        if (session != null) {
            executeThrow(player, session);
        }
    }

    public void executeThrow(Player player, ThrowChargeSession session) {
        player.sendActionBar(Component.empty());

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.STICK) {
            return;
        }

        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }

        double chargeRatio = Math.min(1.0, (double) session.getChargeTicks() / Math.max(1, getMaxChargeTicks()));
        double velocityScalar = getMinThrowVelocity() + (getMaxThrowVelocity() - getMinThrowVelocity()) * chargeRatio;

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Location spawnLoc = TrajectoryRenderer.getRightHandOrigin(eye);
        Vector velocity = direction.clone().multiply(velocityScalar).add(new Vector(0, 0.22, 0));

        List<Location> trajectoryPoints = TrajectoryRenderer.calculateTrajectory(spawnLoc, velocity, 32);
        TrajectoryRenderer.drawThrowFlash(player.getWorld(), trajectoryPoints);

        Item stickItem = player.getWorld().dropItem(spawnLoc, new ItemStack(Material.STICK, 1));
        stickItem.setVelocity(velocity);
        stickItem.setPickupDelay(200);

        player.getWorld().playSound(eye, Sound.ENTITY_SNOWBALL_THROW, 0.9f, (float) (0.75 + chargeRatio * 0.45));

        double searchRadius = getSearchRadius();
        Wolf candidateWolf = player.getWorld().getNearbyEntities(player.getLocation(), searchRadius, searchRadius, searchRadius)
                .stream()
                .filter(entity -> entity instanceof Wolf)
                .map(entity -> (Wolf) entity)
                .filter(wolf -> wolf.isTamed() && wolf.getOwnerUniqueId() != null && wolf.getOwnerUniqueId().equals(player.getUniqueId()))
                .filter(wolf -> !wolf.isDead() && wolf.isValid() && !wolf.isLeashed())
                .filter(wolf -> !wolf.isSitting())
                .filter(wolf -> !isFetching(wolf))
                .min(java.util.Comparator.comparingDouble(wolf -> wolf.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);

        startFetch(player, candidateWolf, stickItem);
    }

    public void startFetch(Player player, Wolf wolf, Item stickItem) {
        if (wolf != null) {
            excitedDogs.remove(wolf.getUniqueId());
        }

        UUID key = wolf != null ? wolf.getUniqueId() : stickItem.getUniqueId();
        FetchSession session = new FetchSession(wolf != null ? wolf.getUniqueId() : null, player.getUniqueId(), stickItem);
        activeSessions.put(key, session);

        if (wolf != null) {
            wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_WOLF_PANT, 0.9f, 1.2f);
            wolf.getPathfinder().moveTo(stickItem.getLocation(), getRunSpeedMultiplier());
        }
    }

    public void cancelFetch(Wolf wolf) {
        if (wolf == null) return;
        FetchSession session = activeSessions.remove(wolf.getUniqueId());
        if (session != null) {
            session.dropStickIfHolding(wolf);
            wolf.getPathfinder().stopPathfinding();
        }
        excitedDogs.remove(wolf.getUniqueId());
    }

    public void cancelExcited(Wolf wolf) {
        if (wolf == null) return;
        excitedDogs.remove(wolf.getUniqueId());
        wolf.getPathfinder().stopPathfinding();
    }

    private void tick() {
        if (!isEnabled()) {
            if (!activeSessions.isEmpty() || !excitedDogs.isEmpty() || !chargingSessions.isEmpty()) {
                shutdown();
            }
            return;
        }

        tickCount++;

        tickChargingSessions();

        if (isExcitedWaitEnabled()) {
            handleExcitedWaitingDogs();
        }

        Iterator<Map.Entry<UUID, FetchSession>> iterator = activeSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, FetchSession> entry = iterator.next();
            FetchSession session = entry.getValue();
            session.incrementTicks(1);

            Item stick = session.getStickItem();
            if (stick != null && stick.isValid() && !stick.isDead()) {
                if (!session.isLanded()) {
                    if (stick.isOnGround() || stick.getVelocity().lengthSquared() < 0.01) {
                        session.setLanded(true);
                        stick.getWorld().playSound(stick.getLocation(), Sound.BLOCK_WOOD_FALL, 0.8f, 1.2f);
                        stick.getWorld().spawnParticle(Particle.BLOCK, stick.getLocation().add(0, 0.1, 0), 6, 0.08, 0.08, 0.08, 0.05, Material.OAK_PLANKS.createBlockData());
                    } else {
                        stick.getWorld().spawnParticle(Particle.DUST, stick.getLocation(), 1, 0, 0, 0, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(245, 245, 255), 0.35f));
                    }
                }
            }

            if (session.getWolfUuid() == null) {
                if (session.getTicksElapsed() > 300) {
                    if (stick != null && stick.isValid()) {
                        stick.setPickupDelay(0);
                    }
                    iterator.remove();
                }
                continue;
            }

            Wolf wolf = getWolf(session.getWolfUuid());
            if (wolf == null || !wolf.isValid() || wolf.isDead()) {
                session.cleanupVisual();
                if (stick != null && stick.isValid()) {
                    stick.setPickupDelay(0);
                }
                iterator.remove();
                continue;
            }

            if (session.getState() == FetchState.CHASING) {
                handleChasing(wolf, session, iterator);
            } else if (session.getState() == FetchState.RETURNING) {
                handleReturning(wolf, session, iterator);
            }
        }
    }

    private void tickChargingSessions() {
        Iterator<Map.Entry<UUID, ThrowChargeSession>> chargeIt = chargingSessions.entrySet().iterator();
        while (chargeIt.hasNext()) {
            Map.Entry<UUID, ThrowChargeSession> entry = chargeIt.next();
            ThrowChargeSession session = entry.getValue();
            Player player = Bukkit.getPlayer(session.getPlayerUuid());

            if (player == null || !player.isOnline() || player.isDead()) {
                chargeIt.remove();
                continue;
            }

            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (mainHand.getType() != Material.STICK) {
                player.sendActionBar(Component.empty());
                chargeIt.remove();
                continue;
            }

            // If player stopped sneaking or released right-click (>6 ticks without interact packet)
            if (!player.isSneaking() || (tickCount - session.getLastInteractTick() > 6)) {
                executeThrow(player, session);
                chargeIt.remove();
                continue;
            }

            // Actively charging
            session.incrementChargeTicks();
            double ratio = Math.min(1.0, (double) session.getChargeTicks() / Math.max(1, getMaxChargeTicks()));
            double velScalar = getMinThrowVelocity() + (getMaxThrowVelocity() - getMinThrowVelocity()) * ratio;

            Location eye = player.getEyeLocation();
            Vector dir = eye.getDirection().normalize();
            Location spawnLoc = TrajectoryRenderer.getRightHandOrigin(eye);
            Vector velocity = dir.clone().multiply(velScalar).add(new Vector(0, 0.22, 0));

            List<Location> points = TrajectoryRenderer.calculateTrajectory(spawnLoc, velocity, 32);
            TrajectoryRenderer.drawPreview(player, points);

            if (ratio >= 1.0 && !session.isFullSoundPlayed()) {
                player.playSound(eye, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.8f);
                session.setFullSoundPlayed(true);
            } else if (session.getChargeTicks() % 5 == 0) {
                player.playSound(eye, Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, (float) (1.0 + ratio * 0.6));
            }

            renderChargeActionBar(player, ratio);
        }
    }

    private void renderChargeActionBar(Player player, double ratio) {
        int totalBars = 10;
        int filled = (int) Math.round(ratio * totalBars);
        int empty = totalBars - filled;
        String bar = "<dark_gray>[<green>" + "■".repeat(filled) + "<gray>" + "■".repeat(empty) + "<dark_gray>]";
        String message = ratio >= 1.0
                ? "<gold>⚡ Gồng ném: " + bar + " <green><bold>100% <yellow>(Thả để ném)"
                : "<gold>⚡ Gồng ném: " + bar + " <white>" + (int) (ratio * 100) + "%";
        player.sendActionBar(miniMessage.deserialize(message));
    }

    private void handleExcitedWaitingDogs() {
        Set<UUID> holdingStickPlayers = new HashSet<>();
        double searchRadius = getSearchRadius();

        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (mainHand.getType() == Material.STICK || offHand.getType() == Material.STICK) {
                holdingStickPlayers.add(player.getUniqueId());

                List<Wolf> nearbyTamedWolves = player.getWorld().getNearbyEntities(player.getLocation(), searchRadius, searchRadius, searchRadius)
                        .stream()
                        .filter(e -> e instanceof Wolf)
                        .map(e -> (Wolf) e)
                        .filter(w -> w.isTamed() && w.getOwnerUniqueId() != null && w.getOwnerUniqueId().equals(player.getUniqueId()))
                        .filter(w -> !w.isDead() && w.isValid() && !w.isLeashed())
                        .filter(w -> !w.isSitting())
                        .filter(w -> !isFetching(w))
                        .toList();

                for (Wolf wolf : nearbyTamedWolves) {
                    ExcitedDogSession session = excitedDogs.computeIfAbsent(
                            wolf.getUniqueId(),
                            id -> new ExcitedDogSession(id, player.getUniqueId(), getExcitedCircleRadius())
                    );
                    session.tick(wolf, player, getExcitedWaitSpeedMultiplier());
                }
            }
        }

        // Cleanup dogs whose owner stopped holding a stick, went away, or IF THE DOG SAT DOWN
        Iterator<Map.Entry<UUID, ExcitedDogSession>> it = excitedDogs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ExcitedDogSession> entry = it.next();
            ExcitedDogSession session = entry.getValue();

            Wolf wolf = getWolf(session.getWolfUuid());
            Player player = Bukkit.getPlayer(session.getPlayerUuid());

            if (wolf == null || !wolf.isValid() || wolf.isDead() || wolf.isSitting()
                    || player == null || !player.isOnline()
                    || !holdingStickPlayers.contains(player.getUniqueId())
                    || !player.getWorld().equals(wolf.getWorld())
                    || wolf.getLocation().distanceSquared(player.getLocation()) > (searchRadius + 5.0) * (searchRadius + 5.0)) {
                if (wolf != null && wolf.isValid()) {
                    wolf.getPathfinder().stopPathfinding();
                }
                it.remove();
            }
        }
    }

    private void handleChasing(Wolf wolf, FetchSession session, Iterator<Map.Entry<UUID, FetchSession>> iterator) {
        Item stick = session.getStickItem();
        if (stick == null || !stick.isValid() || stick.isDead()) {
            wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_WOLF_WHINE, 0.9f, 1.0f);
            wolf.getPathfinder().stopPathfinding();
            iterator.remove();
            return;
        }

        if (!stick.getWorld().equals(wolf.getWorld())) {
            wolf.getPathfinder().stopPathfinding();
            iterator.remove();
            return;
        }

        double distanceSq = wolf.getLocation().distanceSquared(stick.getLocation());
        if (distanceSq <= 3.2 || wolf.getEyeLocation().distanceSquared(stick.getLocation()) <= 3.2) {
            stick.remove();
            wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.3f);
            wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_WOLF_PANT, 0.8f, 1.15f);

            if (isShowStickInMouth()) {
                attachVisualStick(wolf, session);
            }

            session.setState(FetchState.RETURNING);
            session.resetStuckTicks();
            return;
        }

        if (tickCount % 4 == 0) {
            wolf.getPathfinder().moveTo(stick.getLocation(), getRunSpeedMultiplier());
        }

        if (session.getTicksElapsed() > 400) {
            stick.setPickupDelay(0);
            wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_WOLF_WHINE, 0.9f, 1.0f);
            wolf.getPathfinder().stopPathfinding();
            iterator.remove();
        }
    }

    private void handleReturning(Wolf wolf, FetchSession session, Iterator<Map.Entry<UUID, FetchSession>> iterator) {
        Player player = Bukkit.getPlayer(session.getPlayerUuid());
        if (player == null || !player.isOnline() || player.isDead() || !player.getWorld().equals(wolf.getWorld())) {
            session.dropStickIfHolding(wolf);
            wolf.getPathfinder().stopPathfinding();
            iterator.remove();
            return;
        }

        ItemDisplay visual = session.getVisualStick();
        if (visual != null && visual.isValid()) {
            Location mouthLoc = getWolfMouthLocation(wolf);
            visual.teleport(mouthLoc);

            if (tickCount % 16 == 0) {
                wolf.getWorld().spawnParticle(Particle.HEART, wolf.getLocation().add(0, 0.85, 0), 1, 0.15, 0.15, 0.15, 0.01);
            }
        }

        double distanceSq = wolf.getLocation().distanceSquared(player.getLocation());
        if (distanceSq <= 3.8 || wolf.getEyeLocation().distanceSquared(player.getEyeLocation()) <= 4.0) {
            session.cleanupVisual();
            wolf.getPathfinder().stopPathfinding();

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(Material.STICK, 1));
            if (!leftover.isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(Material.STICK, 1));
            }

            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.4f);
            wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 1.0f, 1.25f);
            wolf.getWorld().spawnParticle(Particle.HEART, wolf.getLocation().add(0, 0.75, 0), 5, 0.25, 0.2, 0.25, 0.02);

            iterator.remove();
            return;
        }

        if (tickCount % 4 == 0) {
            wolf.getPathfinder().moveTo(player.getLocation(), getReturnSpeedMultiplier());
        }

        if (session.getTicksElapsed() > 600) {
            session.dropStickIfHolding(wolf);
            wolf.getPathfinder().stopPathfinding();
            iterator.remove();
        }
    }

    private void attachVisualStick(Wolf wolf, FetchSession session) {
        try {
            Location mouthLoc = getWolfMouthLocation(wolf);
            ItemDisplay display = wolf.getWorld().spawn(mouthLoc, ItemDisplay.class, entity -> {
                entity.setItemStack(new ItemStack(Material.STICK));
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                entity.setTransformation(new Transformation(
                        new Vector3f(0.0f, 0.0f, 0.0f),
                        new Quaternionf().rotateZ((float) Math.toRadians(-45.0)),
                        new Vector3f(0.45f, 0.45f, 0.45f),
                        new Quaternionf()
                ));
                entity.setBrightness(new Display.Brightness(15, 15));
                entity.setPersistent(false);
                entity.setInvulnerable(true);
                entity.setGravity(false);
                entity.setSilent(true);
            });
            session.setVisualStick(display);
        } catch (Exception ignored) {}
    }

    private Location getWolfMouthLocation(Wolf wolf) {
        Location loc = wolf.getLocation();
        Vector forward = loc.getDirection().setY(0);
        if (forward.lengthSquared() < 0.0001) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();

        Location mouthLoc = loc.clone()
                .add(forward.clone().multiply(0.40))
                .add(0, 0.38, 0);
        mouthLoc.setYaw(loc.getYaw());
        mouthLoc.setPitch(0.0f);
        return mouthLoc;
    }

    private Wolf getWolf(UUID uuid) {
        if (uuid == null) return null;
        Entity entity = Bukkit.getEntity(uuid);
        return entity instanceof Wolf wolf ? wolf : null;
    }
}
