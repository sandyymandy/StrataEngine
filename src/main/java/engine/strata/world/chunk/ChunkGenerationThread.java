package engine.strata.world.chunk;

import engine.strata.client.StrataClient;
import engine.strata.world.gen.TerrainGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Worker thread that generates chunks from a queue.
 * Each thread can handle up to 20 chunks in its queue.
 */
public class ChunkGenerationThread extends Thread {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChunkGenThread");
    private static final int MAX_QUEUE_SIZE = 20;

    private final BlockingQueue<ChunkGenerationTask> taskQueue;
    private final TerrainGenerator terrainGenerator;
    private final AtomicBoolean running;
    private final int threadId;

    public ChunkGenerationThread(int threadId, TerrainGenerator terrainGenerator) {
        super("ChunkGen-" + threadId);
        this.threadId = threadId;
        this.terrainGenerator = terrainGenerator;
        this.taskQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
        this.running = new AtomicBoolean(true);
        this.setDaemon(true);

        if(StrataClient.getInstance().getDebugInfo().showChunkDebug()) LOGGER.info("Started chunk generation thread {}", threadId);
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                // Wait for a task (blocks until available)
                ChunkGenerationTask task = taskQueue.take();

                // Generate the chunk
                long startTime = System.nanoTime();
                terrainGenerator.generateChunk(task.chunk());
                long endTime = System.nanoTime();

                double timeMs = (endTime - startTime) / 1_000_000.0;
                if(StrataClient.getInstance().getDebugInfo().showChunkDebug()) LOGGER.debug("Thread {} generated {} in {:.2f}ms",
                        threadId, task.chunk(), timeMs);

                // Notify completion
                if (task.callback() != null) {
                    task.callback().onChunkGenerated(task.chunk());
                }

            } catch (InterruptedException e) {
                LOGGER.info("Chunk generation thread {} interrupted", threadId);
                break;
            } catch (Exception e) {
                LOGGER.error("Error generating chunk in thread {}", threadId, e);
            }
        }

        LOGGER.info("Chunk generation thread {} stopped", threadId);
    }

    /**
     * Submits a chunk generation task.
     * Returns false if the queue is full.
     */
    public boolean submitTask(Chunk chunk, ChunkGenerationCallback callback) {
        if (taskQueue.remainingCapacity() == 0) {
            return false;
        }

        return taskQueue.offer(new ChunkGenerationTask(chunk, callback));
    }

    /**
     * Gets the number of pending tasks in the queue.
     */
    public int getPendingTasks() {
        return taskQueue.size();
    }

    /**
     * Checks if the queue has space for more tasks.
     */
    public boolean hasCapacity() {
        return taskQueue.remainingCapacity() > 0;
    }

    /**
     * Stops the thread gracefully.
     */
    public void shutdown() {
        running.set(false);
        this.interrupt();
    }

    /**
     * Task record for chunk generation.
     */
    private record ChunkGenerationTask(Chunk chunk, ChunkGenerationCallback callback) {}

    /**
     * Callback interface for chunk generation completion.
     */
    public interface ChunkGenerationCallback {
        void onChunkGenerated(Chunk chunk);
    }
}