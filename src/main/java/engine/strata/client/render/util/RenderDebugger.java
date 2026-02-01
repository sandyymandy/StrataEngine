package engine.strata.client.render.util;

import engine.helios.BufferBuilder;
import engine.helios.RenderLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Debug utility for monitoring the render pipeline.
 * Helps identify bottlenecks and rendering issues.
 */
public class RenderDebugger {
    private static final Logger LOGGER = LoggerFactory.getLogger("RenderDebugger");
    private static boolean enabled = false;

    // Statistics
    private static final Map<String, Integer> layerVertexCounts = new HashMap<>();
    private static final Map<String, Long> layerRenderTimes = new HashMap<>();
    private static int totalEntitiesRendered = 0;
    private static int totalEntitiesCulled = 0;

    /**
     * Enables or disables debug mode.
     */
    public static void setEnabled(boolean enable) {
        enabled = enable;
        if (enable) {
            LOGGER.info("Render debugging enabled");
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Records that an entity was rendered.
     */
    public static void recordEntityRendered() {
        if (!enabled) return;
        totalEntitiesRendered++;
    }

    /**
     * Records that an entity was culled.
     */
    public static void recordEntityCulled() {
        if (!enabled) return;
        totalEntitiesCulled++;
    }

    /**
     * Records render statistics for a layer.
     */
    public static void recordLayerStats(RenderLayer layer, BufferBuilder buffer, long renderTimeNanos) {
        if (!enabled) return;

        String layerName = layer.texture().toString();
        layerVertexCounts.put(layerName, buffer.getVertexCount());
        layerRenderTimes.put(layerName, renderTimeNanos);
    }

    /**
     * Logs frame statistics.
     */
    public static void logFrameStats() {
        if (!enabled) return;

        LOGGER.info("=== Render Frame Stats ===");
        LOGGER.info("Entities: {} rendered, {} culled", totalEntitiesRendered, totalEntitiesCulled);

        if (!layerVertexCounts.isEmpty()) {
            LOGGER.info("Layer Statistics:");
            for (Map.Entry<String, Integer> entry : layerVertexCounts.entrySet()) {
                String layer = entry.getKey();
                int vertices = entry.getValue();
                long timeNanos = layerRenderTimes.getOrDefault(layer, 0L);
                double timeMs = timeNanos / 1_000_000.0;

                LOGGER.info("  {}: {} vertices, {:.2f}ms", layer, vertices, timeMs);
            }
        }

        // Reset for next frame
        resetFrameStats();
    }

    /**
     * Resets statistics for the current frame.
     */
    private static void resetFrameStats() {
        totalEntitiesRendered = 0;
        totalEntitiesCulled = 0;
        layerVertexCounts.clear();
        layerRenderTimes.clear();
    }

    /**
     * Validates that a buffer is in the correct state for rendering.
     */
    public static void validateBuffer(BufferBuilder buffer, String context) {
        if (!enabled) return;

        if (!buffer.isBuilding()) {
            LOGGER.warn("[{}] Buffer is not building!", context);
        }

        if (buffer.getVertexCount() == 0) {
            LOGGER.warn("[{}] Buffer has no vertices!", context);
        }
    }

    /**
     * Logs detailed information about a render layer.
     */
    public static void logLayerInfo(RenderLayer layer) {
        if (!enabled) return;

        LOGGER.info("Layer Info:");
        LOGGER.info("  Texture: {}", layer.texture());
        LOGGER.info("  Translucent: {}", layer.isTranslucent());
        LOGGER.info("  Shader: {}", layer.shaderStack());
    }
}