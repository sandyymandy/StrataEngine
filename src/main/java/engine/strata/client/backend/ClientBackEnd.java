package engine.strata.client.backend;

import engine.strata.api.ClientBackEndInitializer;
import engine.strata.client.StrataClient;
import engine.strata.client.input.InputSystem;
import engine.strata.client.input.keybind.Keybinds;
import engine.strata.core.entrypoint.EntrypointManager;
import engine.strata.entity.Entity;
import engine.strata.entity.entities.BiaEntity;
import engine.strata.entity.entities.MikaEntity;
import engine.strata.entity.entities.PlayerEntity;
import engine.strata.entity.util.EntityKey;
import engine.strata.event.events.KeyEvent;
import engine.strata.event.events.MouseEvent;
import engine.strata.event.events.MouseScrollEvent;
import engine.strata.registry.registries.EntityRegistry;
import engine.strata.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientBackEnd {
    public static final Logger LOGGER = LoggerFactory.getLogger("ClientBackEnd");
    private final InputSystem inputSystem = new InputSystem();
    private final World world;
    private final PlayerEntity player;
    private boolean hideCursor = true;

    public ClientBackEnd() {
        EntrypointManager.invoke("client_back_end", ClientBackEndInitializer.class,
                ClientBackEndInitializer::onBackEndInitialize);

        StrataClient.getInstance().getEventBus().subscribe(KeyEvent.class, inputSystem::handleKeyEvent);
        StrataClient.getInstance().getEventBus().subscribe(MouseEvent.class, inputSystem::handleMouseEvent);
        StrataClient.getInstance().getEventBus().subscribe(MouseScrollEvent.class, inputSystem::handleScrollEvent);

        this.world = new World("TestWorld", System.currentTimeMillis());
        this.player = EntityRegistry.PLAYER.create(world);
        this.player.setPosition(0, 90, 0);
        world.addEntity(player);
        spawnTestEntities(EntityRegistry.BIA,5);
        spawnTestEntities(EntityRegistry.MIKA,10);

        // Pre-load chunks around spawn
        LOGGER.info("Pre-loading spawn chunks...");
        world.preloadChunks(player.getPosition().getX(), player.getPosition().getY(), player.getPosition().getZ());
    }

    private void spawnTestEntities(EntityKey<?> entityKey, int pos) {
        LOGGER.info("Spawning test entities...");

        for (int i = 0; i < 15; i++) {
            Entity bia = entityKey.create(world);
            bia.setPosition(i + 1.5, 120, -pos+.5);
            bia.setHeadPitch((float) (i / 0.4));
            world.addEntity(bia);
        }
    }

    public void processInput() {
        StrataClient.getInstance().getEventBus().flush();
        inputSystem.update();

        if (Keybinds.HIDE_CURSOR.isJustReleasedFrame()) {
            hideCursor = !hideCursor;
        }

        StrataClient.getInstance().getWindow().setCursorLocked(hideCursor);
    }

    public void tick() {
        inputSystem.tick();
        world.tick();
        StrataClient.getInstance().getCamera().tick();
    }

    public PlayerEntity getPlayer() {
        return player;
    }

    public World getWorld() {
        return world;
    }

    public void shutdown() {
        this.world.shutdown();
    }
}