package engine.strata.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StrataServer {
    private boolean running = true;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double MS_PER_TICK = 1000.0 / TICKS_PER_SECOND;
    public static final Logger LOGGER = LoggerFactory.getLogger("Server");

    public void start() {
        LOGGER.info("Loading World...");
        // Load chunks, setup networking
        run();
    }

    public void run() {
        long lastTime = System.currentTimeMillis();

        while (running) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastTime;

            if (elapsed >= MS_PER_TICK) {
                tick();
                lastTime = now;

                // Calculate how much time we have left to sleep
                long workTime = System.currentTimeMillis() - now;
                long sleepTime = (long) (MS_PER_TICK - workTime);

                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    private void tick() {
        // World Physics, Entity AI, Send Packets to Clients
    }

    public void stop() {
        running = false;
        LOGGER.info("Saving world...");
    }
}