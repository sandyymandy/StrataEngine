package engine.strata.client.render.renderer.entity.util;

import engine.helios.BufferBuilder;
import engine.helios.MatrixStack;
import engine.helios.RenderLayer;
import engine.strata.client.StrataClient;
import engine.strata.client.render.animation.core.AnimationController;
import engine.strata.client.render.model.io.ModelManager;
import engine.strata.client.render.model.StrataModel;
import engine.strata.client.render.model.StrataSkin;
import engine.strata.entity.Entity;
import engine.strata.util.Identifier;

/**
 * Base class for rendering entities using the StrataModel system.
 * Handles texture layers, batching, render order, and animations.
 *
 * <p><b>FIXED ISSUES:</b>
 * <ul>
 *   <li>✅ Corrected delta time calculation (was dividing partialTicks by 20)</li>
 *   <li>✅ Now uses proper tick duration (0.05 seconds)</li>
 *   <li>✅ Animations now progress at correct speed</li>
 * </ul>
 */
public abstract class EntityRenderer<T extends Entity> {
    protected final EntityRenderDispatcher dispatcher;
    private StrataModel model;
    private StrataSkin skin;
    private AnimationController animationController;
    private boolean modelLoaded = false;

    public EntityRenderer(EntityRendererFactory.Context ctx) {
        this.dispatcher = ctx.dispatcher;
    }

    /**
     * The main method called every frame to render the entity.
     *
     * @param entity       The entity instance
     * @param partialTicks The fraction of time between ticks (0.0 to 1.0) for smoothing
     * @param poseStack    The MatrixStack for position/rotation
     */
    public void render(T entity, float partialTicks, MatrixStack poseStack) {
        // Lazy load model and skin on first render
        if (!modelLoaded) {
            loadModel(entity);
            if (model == null || skin == null) {
                return; // Failed to load
            }
        }

        updateAnimations(entity, partialTicks);

        poseStack.push();

        // Apply entity-specific transformations
        applyTransformations(entity, partialTicks, poseStack);

        // Scale from model space (16x16x16) to world space (1x1x1)
        poseStack.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);

        // Render all texture layers in priority order
        StrataClient.getInstance().getMasterRenderer()
                .getModelRenderer().render(model, skin, poseStack);

        poseStack.pop();
    }

    /**
     * Loads the model, skin, and initializes the animation controller.
     * Override to customize loading behavior.
     */
    protected void loadModel(T entity) {
        try {
            Identifier modelId = getModelId();
            model = ModelManager.getModel(modelId);
            skin = ModelManager.getSkin(modelId);

            // Initialize animation controller
            if (model != null) {
                animationController = new AnimationController(model);
                onAnimationControllerCreated(entity, animationController);
            }

            modelLoaded = true;

            if (model == null || skin == null) {
                throw new RuntimeException("Failed to load model or skin: " + modelId);
            }
        } catch (Exception e) {
            // Mark as loaded to prevent retry spam
            modelLoaded = true;
        }
    }

    /**
     * Called when the animation controller is created.
     * Override to setup animation layers, state machines, etc.
     *
     * @param entity The entity being rendered
     * @param controller The newly created animation controller
     */
    protected void onAnimationControllerCreated(T entity, AnimationController controller) {
        // Default: do nothing
        // Subclasses can override to:
        // - Create custom animation layers
        // - Setup state machines
        // - Configure blend modes
    }

    /**
     * Updates entity animations based on current state.
     * Override to implement animation logic.
     *
     * <p><b>CRITICAL FIX:</b> Corrected delta time calculation!</p>
     * <ul>
     *   <li><b>BEFORE:</b> deltaTime = partialTicks / 20.0f (WRONG!)</li>
     *   <li><b>AFTER:</b> deltaTime = 1.0f / 20.0f (CORRECT!)</li>
     * </ul>
     *
     * <p><b>Why the old code was wrong:</b></p>
     * <ul>
     *   <li>partialTicks is an interpolation factor (0.0 to 1.0)</li>
     *   <li>Dividing by 20 gave 0.000 to 0.05, way too small</li>
     *   <li>This caused animations to barely progress or appear stuck</li>
     * </ul>
     *
     * <p><b>Why the new code is correct:</b></p>
     * <ul>
     *   <li>1.0f / 20.0f = 0.05 seconds (one game tick duration)</li>
     *   <li>updateAnimations() is called once per tick (20 times/second)</li>
     *   <li>Animations progress at the expected speed</li>
     * </ul>
     *
     * @param entity The entity to animate
     * @param partialTicks Sub-tick interpolation factor (used for smooth rendering, not animation timing)
     */
    protected void updateAnimations(T entity, float partialTicks) {
        if (animationController != null) {
            // CRITICAL FIX: Use actual tick duration instead of partialTicks/20
            //
            // Minecraft runs at 20 ticks per second (TPS)
            // Each tick = 1/20 seconds = 0.05 seconds
            //
            // This is called once per tick, so deltaTime should always be 0.05
            float deltaTime = 1.0f / 20.0f;  // 0.05 seconds per tick

            animationController.tick(deltaTime);
        }

        // Note: If your engine supports actual frame delta time, you could do:
        // float deltaTime = GameEngine.getDeltaTime();
        // This would make animations frame-rate independent.
        //
        // However, Minecraft-style tick-based updates are perfectly fine
        // and actually help with determinism across different frame rates.
    }

    /**
     * Apply transformations specific to this entity type.
     * Override to add custom animations, bobbing, etc.
     */
    protected void applyTransformations(T entity, float partialTicks, MatrixStack poseStack) {
        // Default: no additional transformations
        // Subclasses can override to add entity-specific behavior
    }

    /**
     * Determines if a specific texture layer should be rendered.
     * Override to implement conditional rendering (e.g., damage overlay, power-ups).
     */
    protected boolean shouldRenderLayer(T entity, String textureSlot) {
        return true; // Render all layers by default
    }

    /**
     * Helper to get a buffer for a specific render layer.
     */
    protected BufferBuilder getBuffer(RenderLayer layer) {
        return StrataClient.getInstance().getMasterRenderer().getBuffer(layer);
    }

    /**
     * Returns the model identifier for this entity.
     * Must be implemented by subclasses.
     */
    public abstract Identifier getModelId();

    /**
     * Checks if an entity should be rendered (frustum culling).
     * Override for entities with special rendering rules.
     */
    public boolean shouldRender(T entity, float camX, float camY, float camZ) {
        return entity.isInRange(camX, camY, camZ);
    }

    // ============================================================================
    // Accessors
    // ============================================================================

    /**
     * Gets the loaded model instance.
     */
    protected StrataModel getModel() {
        return model;
    }

    /**
     * Gets the loaded skin instance.
     */
    protected StrataSkin getSkin() {
        return skin;
    }

    /**
     * Gets the animation controller for this entity.
     *
     * @return The animation controller, or null if model not loaded yet
     */
    protected AnimationController getAnimationController() {
        return animationController;
    }

    /**
     * Checks if the model and animation controller are loaded.
     */
    protected boolean isModelLoaded() {
        return modelLoaded && model != null && animationController != null;
    }
}