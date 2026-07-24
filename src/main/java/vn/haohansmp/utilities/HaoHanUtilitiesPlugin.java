package vn.haohansmp.utilities;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohansmp.utilities.carry.CarryPreferences;
import vn.haohansmp.utilities.carry.CarryService;
import vn.haohansmp.utilities.carry.CarrySessionManager;
import vn.haohansmp.utilities.carry.CarrySnapshotService;
import vn.haohansmp.utilities.carry.CarryValidator;
import vn.haohansmp.utilities.command.HaoHanUtilitiesCommand;
import vn.haohansmp.utilities.config.MessageService;
import vn.haohansmp.utilities.crop.CropHarvestListener;
import vn.haohansmp.utilities.database.CarryRepository;
import vn.haohansmp.utilities.database.DatabaseManager;
import vn.haohansmp.utilities.database.SQLiteCarryRepository;
import vn.haohansmp.utilities.food.GoldenAppleListener;
import vn.haohansmp.utilities.listener.CarryRestrictionListener;
import vn.haohansmp.utilities.listener.PickupPlaceListener;
import vn.haohansmp.utilities.listener.PlayerLifecycleListener;
import vn.haohansmp.utilities.phantom.PhantomSuppressionListener;
import vn.haohansmp.utilities.integration.SoulAnchorIntegration;
import vn.haohansmp.utilities.protection.ProtectionService;
import vn.haohansmp.utilities.recovery.RecoveryService;
import vn.haohansmp.utilities.render.CarryRenderer;
import vn.haohansmp.utilities.render.ItemDisplayRenderer;

import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;

public final class HaoHanUtilitiesPlugin extends JavaPlugin {
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

        CarryRepository repository = new SQLiteCarryRepository(databaseManager);
        CarrySessionManager sessions = new CarrySessionManager();
        CarryPreferences preferences = new CarryPreferences(this);
        MessageService messages = new MessageService(this);
        SoulAnchorIntegration soulAnchors = new SoulAnchorIntegration(this);
        soulAnchors.initialize();
        CarryValidator validator = new CarryValidator(this, sessions, soulAnchors);
        CarrySnapshotService snapshots = new CarrySnapshotService(this);
        ProtectionService protection = new ProtectionService(this);
        renderer = new ItemDisplayRenderer(this, sessions, snapshots);

        carryService = new CarryService(
                this,
                repository,
                sessions,
                validator,
                preferences,
                snapshots,
                soulAnchors,
                protection,
                renderer,
                messages
        );
        RecoveryService recovery = new RecoveryService(this, repository, carryService, snapshots, soulAnchors);
        PhantomSuppressionListener phantomSuppression = new PhantomSuppressionListener(this);

        PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(new PickupPlaceListener(carryService, preferences), this);
        plugins.registerEvents(new CarryRestrictionListener(this, carryService), this);
        plugins.registerEvents(new PlayerLifecycleListener(carryService, recovery), this);
        plugins.registerEvents(phantomSuppression, this);
        plugins.registerEvents(new GoldenAppleListener(this), this);
        plugins.registerEvents(new CropHarvestListener(this, protection), this);

        HaoHanUtilitiesCommand commandHandler = new HaoHanUtilitiesCommand(
                this, carryService, validator, preferences, messages, phantomSuppression);
        PluginCommand command = Objects.requireNonNull(getCommand("haohanutilities"), "Command missing from plugin.yml");
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        renderer.start();
        int removedPhantoms = phantomSuppression.cleanupLoadedWorlds();
        recovery.recoverOnStartup();
        getLogger().info("Hảo Hán Utilities enabled. Removed " + removedPhantoms + " loaded Phantom(s).");
    }

    @Override
    public void onDisable() {
        if (carryService != null) carryService.shutdown();
        if (renderer != null) renderer.stop();
    }
}
