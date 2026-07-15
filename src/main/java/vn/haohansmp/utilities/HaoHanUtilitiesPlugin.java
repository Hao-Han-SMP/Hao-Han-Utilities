package vn.haohansmp.utilities;

import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohansmp.utilities.carry.CarryLockManager;
import vn.haohansmp.utilities.carry.CarryService;
import vn.haohansmp.utilities.carry.CarrySessionManager;
import vn.haohansmp.utilities.carry.CarryValidator;
import vn.haohansmp.utilities.chest.ChestCarryListener;
import vn.haohansmp.utilities.chest.ChestCarryService;
import vn.haohansmp.utilities.command.HaoHanUtilitiesCommand;
import vn.haohansmp.utilities.config.MessageService;
import vn.haohansmp.utilities.database.CarryRepository;
import vn.haohansmp.utilities.database.DatabaseManager;
import vn.haohansmp.utilities.database.SQLiteCarryRepository;
import vn.haohansmp.utilities.listener.BlockProtectionListener;
import vn.haohansmp.utilities.listener.CarryRestrictionListener;
import vn.haohansmp.utilities.listener.PickupPlaceListener;
import vn.haohansmp.utilities.listener.PlayerLifecycleListener;
import vn.haohansmp.utilities.listener.ViewerTracker;
import vn.haohansmp.utilities.phantom.PhantomSuppressionListener;
import vn.haohansmp.utilities.protection.ProtectionService;
import vn.haohansmp.utilities.recovery.RecoveryService;
import vn.haohansmp.utilities.render.BlockDisplayRenderer;
import vn.haohansmp.utilities.render.CarryRenderer;
import vn.haohansmp.utilities.serializer.BasicBlockSerializer;
import vn.haohansmp.utilities.serializer.BlockSerializerRegistry;
import vn.haohansmp.utilities.serializer.BrewingStandSerializer;
import vn.haohansmp.utilities.serializer.ContainerBlockSerializer;
import vn.haohansmp.utilities.serializer.FurnaceBlockSerializer;

import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class HaoHanUtilitiesPlugin extends JavaPlugin {
    private ExecutorService databaseExecutor;
    private CarryService carryService;
    private CarryRenderer renderer;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        DatabaseManager databaseManager = new DatabaseManager(this);
        try {
            databaseManager.initialize();
        } catch (SQLException | RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Cannot initialize carry database", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        databaseExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "HaoHanUtilities-Database");
            thread.setDaemon(true);
            return thread;
        });

        CarryRepository repository = new SQLiteCarryRepository(databaseManager);
        CarrySessionManager sessions = new CarrySessionManager();
        CarryLockManager locks = new CarryLockManager(this);
        ViewerTracker viewers = new ViewerTracker();
        MessageService messages = new MessageService(this);
        CarryValidator validator = new CarryValidator(this, sessions, locks, viewers);
        ProtectionService protection = new ProtectionService(this);
        renderer = new BlockDisplayRenderer(this, sessions);

        BlockSerializerRegistry serializers = new BlockSerializerRegistry(List.of(
                new FurnaceBlockSerializer(),
                new BrewingStandSerializer(),
                new ContainerBlockSerializer(),
                new BasicBlockSerializer(EnumSet.of(
                        Material.CRAFTING_TABLE,
                        Material.SMITHING_TABLE,
                        Material.STONECUTTER,
                        Material.CARTOGRAPHY_TABLE,
                        Material.LOOM,
                        Material.GRINDSTONE,
                        Material.ENCHANTING_TABLE
                ))
        ));

        carryService = new CarryService(this, databaseExecutor, repository, sessions, validator, locks,
                viewers, serializers, protection, renderer, messages);
        ChestCarryService chestCarry = new ChestCarryService(this, carryService, protection, messages);
        RecoveryService recovery = new RecoveryService(this, databaseExecutor, repository, carryService, serializers);
        PhantomSuppressionListener phantomSuppression = new PhantomSuppressionListener(this);

        PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(viewers, this);
        plugins.registerEvents(new ChestCarryListener(chestCarry), this);
        plugins.registerEvents(new PickupPlaceListener(carryService), this);
        plugins.registerEvents(new CarryRestrictionListener(this, carryService), this);
        plugins.registerEvents(new PlayerLifecycleListener(carryService, recovery), this);
        plugins.registerEvents(new BlockProtectionListener(locks), this);
        plugins.registerEvents(phantomSuppression, this);

        HaoHanUtilitiesCommand commandHandler = new HaoHanUtilitiesCommand(
                this, carryService, validator, messages, phantomSuppression);
        PluginCommand command = Objects.requireNonNull(getCommand("haohanutilities"), "Command missing from plugin.yml");
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        carryService.start();
        renderer.start();
        int removedPhantoms = phantomSuppression.cleanupLoadedWorlds();
        recovery.recoverOnStartup();
        getLogger().info("Hảo Hán Utilities enabled. Removed " + removedPhantoms + " loaded Phantom(s).");
    }

    @Override
    public void onDisable() {
        if (carryService != null) carryService.shutdown();
        if (renderer != null) renderer.stop();
        if (databaseExecutor != null) {
            databaseExecutor.shutdown();
            try {
                if (!databaseExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    getLogger().warning("Database executor did not stop within 5 seconds");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
