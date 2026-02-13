package engine.strata.client.backend;

import engine.strata.client.StrataClient;
import engine.strata.client.input.InputSystem;
import engine.strata.client.input.keybind.Keybinds;
import engine.strata.client.render.snapshot.EntityRenderSnapshot;
import engine.strata.entity.entities.BiaEntity;
import engine.strata.entity.Entity;
import engine.strata.entity.entities.PlayerEntity;
import engine.strata.event.events.KeyEvent;
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
    private volatile Map<Integer, EntityRenderSnapshot> latestEntitySnapshots = new HashMap<>();
    private volatile float latestPartialTicks;

    public ClientBackEnd() {
        StrataClient.getInstance().getEventBus().subscribe(KeyEvent.class, inputSystem::handleKeyEvent);
        this.world = new World("TestWorld", System.currentTimeMillis());
        this.player = EntityRegistry.PLAYER.create(world);
        world.addEntity(player);
        spawnTestEntities();

        // Pre-load chunks around spawn
        LOGGER.info("Pre-loading spawn chunks...");
        world.preloadChunks(player.getPosition().getX(), player.getPosition().getY(), player.getPosition().getZ(), 5);
    }

    private void spawnTestEntities() {
        LOGGER.info("Spawning test entities...");

        for (int i = 0; i < 10; i++) {
            BiaEntity bia = EntityRegistry.BIA.create(world);
            bia.setPosition(i * 2,  0, -5);
            bia.setPitch((float) (i/.4));
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
        Map<Integer, EntityRenderSnapshot> frameMap = new HashMap<>();

        for (Entity entity : world.getEntities()) {
            EntityRenderSnapshot snapshot = new EntityRenderSnapshot(entity.getId(), entity.getUuid(), entity.getKey(), partialTicks);

            snapshot.setPosition((float) entity.getPosition().getX(), (float) entity.getPosition().getY(), (float) entity.getPosition().getZ());
            snapshot.setRotation(entity.getRotation().getX(), entity.getRotation().getY(), entity.getRotation().getZ());
            snapshot.setScale(entity.getScale().getX(), entity.getScale().getY(), entity.getScale().getZ());
            snapshot.setPrevPosition(entity.prevX, entity.prevY, entity.prevZ);
            snapshot.setPrevYaw(entity.prevYaw);
            snapshot.setPrevPitch(entity.prevPitch);

            frameMap.put(entity.getId(), snapshot);
        }

        this.latestEntitySnapshots = frameMap;
        this.latestPartialTicks = partialTicks;
    }

    public Map<Integer, EntityRenderSnapshot> getLatestEntitySnapshots() { return latestEntitySnapshots; }
    public float getLatestPartialTicks() { return latestPartialTicks; }
    public PlayerEntity getPlayer() { return player; }
    public World getWorld() { return world; }
    public void stop() { this.running = false; }
}