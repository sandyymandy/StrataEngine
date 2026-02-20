package engine.strata.entity.entities;

import engine.strata.client.StrataClient;
import engine.strata.client.input.keybind.Keybinds;
import engine.strata.entity.Entity;
import engine.strata.entity.PhysicsEntity;
import engine.strata.entity.util.EntityKey;
import engine.strata.registry.registries.EntityRegistry;
import engine.strata.util.math.BlockPos;
import engine.strata.util.math.BlockRaycast;
import engine.strata.util.math.Vec3d;
import engine.strata.world.World;

import java.util.ArrayList;
import java.util.List;

public class PlayerEntity extends PhysicsEntity {

    // ── Block interaction ─────────────────────────────────────────────────────

    /** Max distance (in blocks) at which the player can interact with blocks. */
    private static final double REACH = 7.0;

    /**
     * The block the player is currently looking at.
     * Updated every tick — read by MasterRenderer for the outline.
     * Null when looking at air or beyond reach.
     */
    private volatile BlockRaycast.RaycastResult targetedBlock = null;

    // ── Entity morphing ───────────────────────────────────────────────────────

    /**
     * The EntityKey the player currently appears as.
     * Null = use the player's own renderer (no morph).
     */
    private EntityKey<?> morphTarget = null;

    /** Cached ordered list of all registered entity types for cycling. */
    private List<EntityKey<?>> morphList = null;

    /** Index into morphList for the current morph (−1 = no morph / player). */
    private int morphIndex = -1;

    // ─────────────────────────────────────────────────────────────────────────

    public PlayerEntity(EntityKey<?> key, World world) {
        super(key, world);
    }

    @Override
    public void tick() {
        super.tick();

        // Update the block the player is looking at (used for both interaction
        // and the outline rendered by MasterRenderer).
        updateTargetedBlock();

        handleBlockInteraction();
        handleMorph();
    }

    // ── Block targeting ───────────────────────────────────────────────────────

    private void updateTargetedBlock() {
        targetedBlock = BlockRaycast.raycastFromEntity(world, this, REACH);
    }

    // ── Block interaction ─────────────────────────────────────────────────────

    private void handleBlockInteraction() {
        if (Keybinds.PLACE.isJustPressedTick()) {
            if (targetedBlock != null && targetedBlock.isHit()) {
                BlockPos placePos = targetedBlock.getAdjacentPos();
                if (placePos != null) {
                    this.world.setBlock(placePos, (short) 2);
                }
            }
        }

        if (Keybinds.DEBUG_PLACE.isPressedTick()) {
            this.world.setBlock(new BlockPos(position), (short) 2);
        }

        if (Keybinds.REMOVE.isJustPressedTick()) {
            if (targetedBlock != null && targetedBlock.isHit()) {
                this.world.setBlock(targetedBlock.getBlockPos(), (short) 0); // Air
            }
        }
    }

    // ── Entity morphing ───────────────────────────────────────────────────────

    private void handleMorph() {
        // Add MORPH_NEXT / MORPH_PREV to your Keybinds class, e.g.:
        //   public static final Keybind MORPH_NEXT = new Keybind(GLFW.GLFW_KEY_N);
        //   public static final Keybind MORPH_PREV = new Keybind(GLFW.GLFW_KEY_B);

        if (Keybinds.MORPH_NEXT.isJustPressedTick()) {
            cycleMorph(+1);
        }
        if (Keybinds.MORPH_PREV.isJustPressedTick()) {
            cycleMorph(-1);
        }
    }

    private void cycleMorph(int direction) {
        // Build the morph list lazily from the registry
        if (morphList == null) {return;
//            morphList = new ArrayList<>(EntityRegistry.getAllEntityKeys());
        }

        if (morphList.isEmpty()) return;

        morphIndex = (morphIndex + direction + morphList.size()) % morphList.size();
        morphTarget = morphList.get(morphIndex);
    }

    /**
     * Resets the player back to their own appearance.
     */
    public void clearMorph() {
        morphTarget = null;
        morphIndex = -1;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /**
     * Returns the block the player is currently looking at, or a miss result
     * if nothing is in range.  Never null.
     */
    public BlockRaycast.RaycastResult getTargetedBlock() {
        BlockRaycast.RaycastResult r = targetedBlock;
        return r != null ? r : BlockRaycast.RaycastResult.miss();
    }

    /**
     * Returns the EntityKey the player is currently morphed into, or null if
     * the player is using their own renderer.
     */
    public EntityKey<?> getMorphTarget() {
        return morphTarget;
    }

    public boolean isMorphed() {
        return morphTarget != null;
    }
}