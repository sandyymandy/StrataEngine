package engine.strata.client.backend;

import engine.strata.client.StrataClient;
import engine.strata.client.input.InputSystem;
import engine.strata.client.input.keybind.Keybinds;
import engine.strata.client.render.snapshot.EntityRenderSnapshot;
import engine.strata.client.render.snapshot.EntitySnapshotPool;
import engine.strata.entity.entities.BiaEntity;
import engine.strata.entity.Entity;
import engine.strata.entity.entities.PlayerEntity;
import engine.strata.event.events.KeyEvent;
import engine.strata.physics.JoltLoader;
import engine.strata.registry.registries.EntityRegistry;
import engine.strata.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ClientBackEnd implements Runnable {
    public static final Logger LOGGER = LoggerFactory.getLogger("ClientBackEnd");
    private final InputSystem inputSystem = new InputSystem();
    private final World world;
    private final PlayerEntity player;
    private boolean running = true;
    private boolean hideCursor = true;
    private static final float TICKS_PER_SECOND = 20.0F;
    private static final float TIME_PER_TICK = 1.0F / TICKS_PER_SECOND;

    // The "Bridge" between threads
    private final EntitySnapshotPool snapshotPool = new EntitySnapshotPool();
    private volatile float latestPartialTicks;

    public ClientBackEnd() {
        StrataClient.getInstance().getEventBus().subscribe(KeyEvent.class, inputSystem::handleKeyEvent);
        this.world = new World("TestWorld", System.currentTimeMillis());
        this.player = EntityRegistry.PLAYER.create(world);
        this.player.setPosition(0,120,0);
        world.addEntity(player);
        spawnTestEntities();
        JoltLoader.load();

        // Pre-load chunks around spawn
        LOGGER.info("Pre-loading spawn chunks...");
        world.preloadChunks(player.getPosition().getX(), player.getPosition().getY(), player.getPosition().getZ());
    }

    private void spawnTestEntities() {
        LOGGER.info("Spawning test entities...");

        for (int i = 0; i < 10; i++) {
            BiaEntity bia = EntityRegistry.BIA.create(world);
            bia.setPosition(i * 2,  120, -5);
            bia.setHeadPitch((float) (i/.4));
            world.addEntity(bia);
        }
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();
        double accumulator = 0.0;

        while (running) {
            long now = System.nanoTime();
            accumulator += (now - lastTime) / 1_000_000_000.0;
            lastTime = now;

            processInput();

            while (accumulator >= TIME_PER_TICK) {
                tick();
                accumulator -= TIME_PER_TICK;
            }

            // Capture the state for the Frontend (Snapshot Phase)
            float partialTicks = (float) (accumulator / 0.05);
            captureFrameState(partialTicks);

        }
    }

    private void processInput() {
        StrataClient.getInstance().getEventBus().flush();
        inputSystem.update();

        if (Keybinds.HIDE_CURSOR.isCanceled()) {
            hideCursor = !hideCursor;
        }

        StrataClient.getInstance().getWindow().setCursorLocked(hideCursor);

    }

    private void tick() {
        world.tick();
        StrataClient.getInstance().getCamera().tick();
    }

    private void captureFrameState(float partialTicks) {
        for (Entity entity : world.getEntities()) {
            snapshotPool.updateSnapshot(entity, partialTicks);
        }

        this.latestPartialTicks = partialTicks;
    }

    public Map<Integer, EntityRenderSnapshot> getLatestEntitySnapshots() { return snapshotPool.getAllSnapshots(); }
    public float getLatestPartialTicks() { return latestPartialTicks; }
    public PlayerEntity getPlayer() { return player; }
    public World getWorld() { return world; }
    public void stop() { this.running = false; }
}