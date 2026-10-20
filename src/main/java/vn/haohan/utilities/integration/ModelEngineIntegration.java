package vn.haohan.utilities.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Optional bridge to ModelEngine 4.x.
 * Uses reflection so HaoHanUtilities runs even when ModelEngine is absent.
 */
public final class ModelEngineIntegration {
    private final Plugin plugin;
    private boolean available;
    private Method getAPIMethod;
    private Method getBlueprintMethod;
    private Method createActiveModelMethod;
    private Method createModeledEntityMethod;
    private Method getModeledEntityMethod;

    public ModelEngineIntegration(Plugin plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        if (!Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) {
            this.available = false;
            return;
        }

        try {
            Class<?> apiClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            this.getAPIMethod = apiClass.getMethod("getAPI");
            this.getBlueprintMethod = apiClass.getMethod("getBlueprint", String.class);
            this.createActiveModelMethod = apiClass.getMethod("createActiveModel", String.class);
            this.createModeledEntityMethod = apiClass.getMethod("createModeledEntity", Entity.class);
            this.getModeledEntityMethod = apiClass.getMethod("getModeledEntity", Entity.class);
            this.available = true;
            plugin.getLogger().info("ModelEngine integration initialized successfully for Straw Dummy.");
        } catch (Throwable t) {
            this.available = false;
            plugin.getLogger().log(Level.WARNING, "ModelEngine detected but API could not be initialized: " + t.getMessage());
        }
    }

    public boolean isAvailable() {
        return available && Bukkit.getPluginManager().isPluginEnabled("ModelEngine");
    }

    public boolean hasBlueprint(String modelId) {
        if (!isAvailable()) return false;
        try {
            Object bp = getBlueprintMethod.invoke(null, modelId);
            return bp != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Attaches a ModelEngine model onto an entity (e.g. Interaction or dummy base).
     * Returns true if attached successfully.
     */
    public boolean attachModel(Entity entity, String modelId, double scale) {
        if (!isAvailable() || entity == null || modelId == null) return false;
        try {
            Object blueprint = getBlueprintMethod.invoke(null, modelId);
            if (blueprint == null) return false;

            Object activeModel = createActiveModelMethod.invoke(null, modelId);
            if (activeModel == null) return false;

            Class<?> activeModelClass = activeModel.getClass();
            Method setScaleMethod = activeModelClass.getMethod("setScale", double.class);
            setScaleMethod.invoke(activeModel, scale);

            try {
                Method setCanHurt = activeModelClass.getMethod("setCanHurt", boolean.class);
                setCanHurt.invoke(activeModel, true);
            } catch (Throwable ignored) {}

            try {
                Method setInvisUpdate = activeModelClass.getMethod("setInvisUpdate", boolean.class);
                setInvisUpdate.invoke(activeModel, true);
            } catch (Throwable ignored) {}

            Object modeledEntity = createModeledEntityMethod.invoke(null, entity);
            if (modeledEntity == null) return false;

            Class<?> modeledEntityClass = modeledEntity.getClass();
            Method addModelMethod = modeledEntityClass.getMethod("addModel", activeModel.getClass().getInterfaces()[0], boolean.class);
            addModelMethod.invoke(modeledEntity, activeModel, true);

            try {
                Method setBaseEntityVisible = modeledEntityClass.getMethod("setBaseEntityVisible", boolean.class);
                setBaseEntityVisible.invoke(modeledEntity, false);
            } catch (Throwable ignored) {}

            try {
                Method setModelRotationLocked = modeledEntityClass.getMethod("setModelRotationLocked", boolean.class);
                setModelRotationLocked.invoke(modeledEntity, true);
            } catch (Throwable ignored) {}

            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to attach ModelEngine model '" + modelId + "': " + t.getMessage());
            return false;
        }
    }

    /**
     * Plays an animation on the modeled entity if present.
     */
    public boolean playAnimation(Entity entity, String modelId, String animationName) {
        if (!isAvailable() || entity == null) return false;
        try {
            Object modeledEntity = getModeledEntityMethod.invoke(null, entity);
            if (modeledEntity == null) return false;

            Method getModelMethod = modeledEntity.getClass().getMethod("getModel", String.class);
            Object optionalModel = getModelMethod.invoke(modeledEntity, modelId);
            if (optionalModel instanceof java.util.Optional<?> opt && opt.isPresent()) {
                Object activeModel = opt.get();
                Method getAnimHandlerMethod = activeModel.getClass().getMethod("getAnimationHandler");
                Object animHandler = getAnimHandlerMethod.invoke(activeModel);
                if (animHandler != null) {
                    Method playAnim = animHandler.getClass().getMethod("playAnimation", String.class, double.class, double.class, double.class, boolean.class);
                    playAnim.invoke(animHandler, animationName, 0.25, 0.25, 1.0, true);
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Removes ModelEngine model from entity.
     */
    public void destroyModel(Entity entity) {
        if (!isAvailable() || entity == null) return;
        try {
            Object modeledEntity = getModeledEntityMethod.invoke(null, entity);
            if (modeledEntity != null) {
                Method destroyMethod = modeledEntity.getClass().getMethod("destroy");
                destroyMethod.invoke(modeledEntity);
            }
        } catch (Throwable ignored) {}
    }
}
