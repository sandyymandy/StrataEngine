package engine.helios.rendering.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Formal rendering pipeline with defined stages and lifecycle.
 * 
 * <h3>Design:</h3>
 * <p>This class provides a structured approach to rendering with clear phases:
 * <ol>
 *   <li>PRE_RENDER - Setup (clear buffers, set uniforms)</li>
 *   <li>RENDER_OPAQUE - Opaque geometry (terrain, entities)</li>
 *   <li>RENDER_TRANSPARENT - Transparent geometry (sorted back-to-front)</li>
 *   <li>POST_RENDER - Effects and overlays</li>
 * </ol>
 * 
 * <h3>Usage:</h3>
 * <pre>{@code
 * RenderPipeline pipeline = new RenderPipeline();
 * 
 * pipeline.registerStage(RenderStage.PRE_RENDER, () -> {
 *     RenderSystem.clear(0.5f, 0.7f, 0.9f, 1.0f);
 * });
 * 
 * pipeline.registerStage(RenderStage.RENDER_OPAQUE, () -> {
 *     renderChunks();
 *     renderEntities();
 * });
 * 
 * pipeline.execute(partialTicks, deltaTime);
 * }</pre>
 */
public class RenderPipeline {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RenderPipeline");
    
    private final List<StageEntry> stages = new ArrayList<>();
    private boolean isExecuting = false;
    
    // ══════════════════════════════════════════════════════════════════════════
    // STAGE REGISTRATION
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Register a callback for a specific render stage.
     * 
     * @param stage The render stage
     * @param callback The callback to execute
     */
    public void registerStage(RenderStage stage, RenderCallback callback) {
        stages.add(new StageEntry(stage, callback));
        // Keep stages sorted by ordinal
        stages.sort((a, b) -> Integer.compare(a.stage.ordinal(), b.stage.ordinal()));
    }
    
    /**
     * Register a callback with a specific name (for debugging).
     * 
     * @param stage The render stage
     * @param name Descriptive name for this callback
     * @param callback The callback to execute
     */
    public void registerStage(RenderStage stage, String name, RenderCallback callback) {
        stages.add(new StageEntry(stage, name, callback));
        stages.sort((a, b) -> Integer.compare(a.stage.ordinal(), b.stage.ordinal()));
    }
    
    /**
     * Clear all registered stages.
     */
    public void clearStages() {
        if (isExecuting) {
            throw new IllegalStateException("Cannot clear stages during pipeline execution");
        }
        stages.clear();
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // PIPELINE EXECUTION
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Execute the entire render pipeline.
     * 
     * @param partialTicks Frame interpolation factor
     * @param deltaTime Time since last frame (seconds)
     */
    public void execute(float partialTicks, float deltaTime) {
        if (isExecuting) {
            LOGGER.error("Recursive pipeline execution detected!");
            return;
        }
        
        isExecuting = true;
        long startTime = System.nanoTime();
        
        try {
            for (StageEntry entry : stages) {
                executeStage(entry, partialTicks, deltaTime);
            }
        } finally {
            isExecuting = false;
        }
        
        long endTime = System.nanoTime();
        double totalMs = (endTime - startTime) / 1_000_000.0;
        
        if (totalMs > 16.0) { // Slower than 60 FPS
            LOGGER.warn("Render pipeline took {}ms (target: 16ms)", totalMs);
        }
    }
    
    /**
     * Execute a specific render stage.
     */
    private void executeStage(StageEntry entry, float partialTicks, float deltaTime) {
        long stageStart = System.nanoTime();
        
        try {
            entry.callback.render(partialTicks, deltaTime);
        } catch (Exception e) {
            LOGGER.error("Error in render stage {}: {}", 
                        entry.name != null ? entry.name : entry.stage, e);
        }
        
        long stageEnd = System.nanoTime();
        double stageMs = (stageEnd - stageStart) / 1_000_000.0;
        
        // Log slow stages
        if (stageMs > 5.0) {
            LOGGER.warn("Slow render stage {}: {} ms",
                       entry.name != null ? entry.name : entry.stage, stageMs);
        }
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // DEBUG
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * @return Number of registered stage callbacks
     */
    public int getStageCount() {
        return stages.size();
    }
    
    /**
     * @return true if pipeline is currently executing
     */
    public boolean isExecuting() {
        return isExecuting;
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Callback interface for render stages.
     */
    @FunctionalInterface
    public interface RenderCallback {
        void render(float partialTicks, float deltaTime);
    }
    
    /**
     * Internal stage entry.
     */
    private static class StageEntry {
        final RenderStage stage;
        final String name;
        final RenderCallback callback;
        
        StageEntry(RenderStage stage, RenderCallback callback) {
            this(stage, null, callback);
        }
        
        StageEntry(RenderStage stage, String name, RenderCallback callback) {
            this.stage = stage;
            this.name = name;
            this.callback = callback;
        }
    }
    
    /**
     * Render pipeline stages in execution order.
     */
    public enum RenderStage {
        /** Clear buffers, setup global uniforms */
        PRE_RENDER,
        
        /** Render opaque world geometry (chunks, terrain) */
        RENDER_WORLD_OPAQUE,
        
        /** Render opaque entities */
        RENDER_ENTITIES_OPAQUE,
        
        /** Render transparent world geometry (water, glass) */
        RENDER_WORLD_TRANSPARENT,
        
        /** Render transparent entities */
        RENDER_ENTITIES_TRANSPARENT,
        
        /** Render particles */
        RENDER_PARTICLES,
        
        /** Render debug overlays, outlines */
        RENDER_DEBUG,
        
        /** Post-processing effects */
        POST_RENDER
    }
}
