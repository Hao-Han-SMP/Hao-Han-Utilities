package vn.haohan.utilities;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohan.utilities.carry.CarryPreferences;
import vn.haohan.utilities.carry.CarryService;
import vn.haohan.utilities.carry.CarrySessionManager;
import vn.haohan.utilities.carry.CarrySnapshotService;
import vn.haohan.utilities.carry.CarryValidator;
import vn.haohan.utilities.command.HaoHanUtilitiesCommand;
import vn.haohan.utilities.config.MessageService;
import vn.haohan.utilities.concrete.ConcreteMixerListener;
import vn.haohan.utilities.crop.CropHarvestListener;
import vn.haohan.utilities.database.CarryRepository;
import vn.haohan.utilities.database.DatabaseManager;
import vn.haohan.utilities.database.SQLiteCarryRepository;
import vn.haohan.utilities.enderchest.EnderChestListener;
import vn.haohan.utilities.fetch.DogFetchListener;
import vn.haohan.utilities.fetch.DogFetchService;
import vn.haohan.utilities.food.GoldenAppleListener;
import vn.haohan.utilities.heart.CrystalHeartItem;
import vn.haohan.utilities.heart.CrystalHeartListener;
import vn.haohan.utilities.integration.SoulAnchorIntegration;
import vn.haohan.utilities.integration.GSitIntegration;
import vn.haohan.utilities.listener.CarryRestrictionListener;
import vn.haohan.utilities.listener.PickupPlaceListener;
import vn.haohan.utilities.listener.PlayerLifecycleListener;
import vn.haohan.utilities.phantom.PhantomSuppressionListener;
import vn.haohan.utilities.protection.ProtectionService;
import vn.haohan.utilities.recipe.RottenFleshSmokingRecipe;
import vn.haohan.utilities.recovery.RecoveryService;
import vn.haohan.utilities.render.CarryRenderer;
import vn.haohan.utilities.render.ItemDisplayRenderer;
import vn.haohan.utilities.torch.TorchFireListener;

import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;

public final class HaoHanUtilitiesPlugin extends JavaPlugin {
    private CarryService carryService;
    private CarryRenderer renderer;
    private CrystalHeartItem heartItem;
    private RottenFleshSmokingRecipe rottenFleshSmokingRecipe;
    private DogFetchService dogFetchService;

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

        heartItem = new CrystalHeartItem(this);
        heartItem.registerRecipe();
        rottenFleshSmokingRecipe = new RottenFleshSmokingRecipe(this);
        rottenFleshSmokingRecipe.register();
        CrystalHeartListener heartListener = new CrystalHeartListener(this, heartItem);

        dogFetchService = new DogFetchService(this);
        dogFetchService.start();

        PluginManager plugins = getServer().getPluginManager();
        GSitIntegration gsit = new GSitIntegration();
        plugins.registerEvents(new PickupPlaceListener(carryService, preferences, gsit), this);
        plugins.registerEvents(new CarryRestrictionListener(this, carryService), this);
        plugins.registerEvents(new PlayerLifecycleListener(carryService, recovery), this);
        plugins.registerEvents(phantomSuppression, this);
        plugins.registerEvents(new GoldenAppleListener(this), this);
        plugins.registerEvents(new CropHarvestListener(this, protection), this);
        plugins.registerEvents(new ConcreteMixerListener(this, protection), this);
        plugins.registerEvents(new EnderChestListener(this, protection), this);
        plugins.registerEvents(new TorchFireListener(this), this);
        plugins.registerEvents(heartListener, this);
        plugins.registerEvents(new DogFetchListener(this, dogFetchService), this);

        HaoHanUtilitiesCommand commandHandler = new HaoHanUtilitiesCommand(
                this, carryService, validator, preferences, messages, phantomSuppression, heartItem, heartListener);
        PluginCommand command = Objects.requireNonNull(getCommand("haohanutilities"), "Command missing from plugin.yml");
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        renderer.start();
        int removedPhantoms = phantomSuppression.cleanupLoadedWorlds();
        recovery.recoverOnStartup();
        getLogger().info("H\u1ea3o H\u00e1n Utilities enabled. Removed " + removedPhantoms + " loaded Phantom(s).");
    }

    @Override
    public void onDisable() {
        if (carryService != null) carryService.shutdown();
        if (renderer != null) renderer.stop();
        if (heartItem != null) heartItem.unregisterRecipe();
        if (rottenFleshSmokingRecipe != null) rottenFleshSmokingRecipe.unregister();
        if (dogFetchService != null) dogFetchService.shutdown();
    }
}
