package engine.strata.world.chunk;

import engine.strata.client.StrataClient;
import engine.strata.world.gen.TerrainGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages multiple chunk generation threads and distributes work between them.
 * Uses 3 threads, each capable of handling 20 chunks in their queue.
 */
public class ChunkGenerationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChunkGenManager");
    private static final int NUM_THREADS = 3;

    private final List<ChunkGenerationThread> threads;
    private final Queue<PendingChunkTask> waitingQueue;
    private final TerrainGenerator terrainGenerator;
    private final AtomicInteger totalGenerated;
    private final AtomicInteger totalQueued;

    public ChunkGenerationManager(long seed) {
        this.terrainGenerator = new TerrainGenerator(seed);
        this.threads = new ArrayList<>(NUM_THREADS);
        this.waitingQueue = new ConcurrentLinkedQueue<>();
        this.totalGenerated = new AtomicInteger(0);
        this.totalQueued = new AtomicInteger(0);

        // Create and start worker threads
        for (int i = 0; i < NUM_THREADS; i++) {
            ChunkGenerationThread thread = new ChunkGenerationThread(i, terrainGenerator);
            threads.add(thread);
            thread.start();
        }

        if(StrataClient.getInstance().getDebugInfo().showChunkDebug()) LOGGER.info("Chunk generation manager started with {} threads", NUM_THREADS);
    }

    /**
     * Requests a chunk to be generated asynchronously.
     * The chunk will be queued if all threads are busy.
     */
    public void generateChunkAsync(Chunk chunk, ChunkGenerationThread.ChunkGenerationCallback callback) {
        if (chunk.isGenerated()) {
//            LOGGER.warn("Attempted to generate already generated chunk: {}", chunk);
            return;
        }

        // Try to submit to a thread with capacity
        boolean submitted = false;
        for (ChunkGenerationThread thread : threads) {
            if (thread.submitTask(chunk, wrapCallback(callback))) {
                submitted = true;
                totalQueued.incrementAndGet();
                break;
            }
        }

        // If all threads are full, add to waiting queue
        if (!submitted) {
            waitingQueue.offer(new PendingChunkTask(chunk, callback));
            if(StrataClient.getInstance().getDebugInfo().showChunkDebug()) LOGGER.debug("Added {} to waiting queue (size: {})", chunk, waitingQueue.size());
        }
    }

    /**
     * Wraps the callback to track generation and process waiting queue.
     */
    private ChunkGenerationThread.ChunkGenerationCallback wrapCallback(
            ChunkGenerationThread.ChunkGenerationCallback originalCallback) {

        return chunk -> {
            totalGenerated.incrementAndGet();

            // Call original callback
            if (originalCallback != null) {
                originalCallback.onChunkGenerated(chunk);
            }

            // Try to process waiting queue
            processWaitingQueue();
        };
    }

    /**
     * Processes pending chunks from the waiting queue.
     */
    private void processWaitingQueue() {
        if (waitingQueue.isEmpty()) {
            return;
        }

        // Find a thread with capacity
        for (ChunkGenerationThread thread : threads) {
            if (!thread.hasCapacity()) {
                continue;
            }

            PendingChunkTask task = waitingQueue.poll();
            if (task != null) {
                if (thread.submitTask(task.chunk(), wrapCallback(task.callback()))) {
                    totalQueued.incrementAndGet();
                    if(StrataClient.getInstance().getDebugInfo().showChunkDebug()) LOGGER.debug("Moved chunk from waiting queue to thread (remaining: {})",
                            waitingQueue.size());
                } else {
                    // Put it back if submission failed
                    waitingQueue.offer(task);
                }
                break;
            }
        }
    }

    /**
     * Gets statistics about chunk generation.
     */
    public ChunkGenerationStats getStats() {
        int totalPending = 0;
        for (ChunkGenerationThread thread : threads) {
            totalPending += thread.getPendingTasks();
        }

        return new ChunkGenerationStats(
                totalGenerated.get(),
                totalQueued.get(),
                totalPending,
                waitingQueue.size()
        );
    }

    /**
     * Shuts down all generation threads.
     */
    public void shutdown() {
        if(StrataClient.getInstance().getDebugInfo().showChunkDebug()) LOGGER.info("Shutting down chunk generation manager...");

        for (ChunkGenerationThread thread : threads) {
            thread.shutdown();
        }

        // Wait for threads to finish
        for (ChunkGenerationThread thread : threads) {
            try {
                thread.join(1000); // Wait up to 1 second per thread
            } catch (InterruptedException e) {
                if(StrataClient.getInstance().getDebugInfo().showChunkDebug()) LOGGER.warn("Interrupted while waiting for thread to finish");
            }
        }

//        LOGGER.info("Chunk generation manager stopped. Generated {} chunks total",
//                totalGenerated.get());
    }

    /**
     * Pending chunk task in the waiting queue.
     */
    private record PendingChunkTask(
            Chunk chunk,
            ChunkGenerationThread.ChunkGenerationCallback callback
    ) {}

    /**
     * Statistics about chunk generation.
     */
    public record ChunkGenerationStats(
            int totalGenerated,
            int totalQueued,
            int currentlyPending,
            int waitingInQueue
    ) {
        @Override
        public String toString() {
            return String.format(
                    "Generated: %d, Queued: %d, Pending: %d, Waiting: %d",
                    totalGenerated, totalQueued, currentlyPending, waitingInQueue
            );
        }
    }
}