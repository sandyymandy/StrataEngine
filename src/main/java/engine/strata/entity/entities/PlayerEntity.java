package engine.strata.entity.entities;

import engine.strata.client.input.keybind.Keybinds;
import engine.strata.entity.Entity;
import engine.strata.entity.util.EntityKey;
import engine.strata.util.BlockPos;
import engine.strata.util.math.BlockRaycast;
import engine.strata.world.World;

import java.util.List;

public class PlayerEntity extends Entity {


    /** Max distance (in blocks) at which the player can interact with blocks. */
    private static final float REACH = 10;

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
        setModelPosition(0, 0, 0.1f);
    }

    // Movement speeds
    private float groundSpeed = 7f; // blocks per second (similar to Minecraft sprint)
    private float airSpeed = 0.02f; // Air control multiplier
    private float jumpStrength = 15.0f; // Upward velocity for jumping

    @Override
    public void tick() {
        super.tick();

        // Handle movement using rigid body physics
        handleMovement();

        // Update the block the player is looking at (used for both interaction
        // and the outline rendered by MasterRenderer).
        updateTargetedBlock();

        handleBlockInteraction();
        handleMorph();
    }

    // ── Movement ──────────────────────────────────────────────────────────────

    private void handleMovement() {
//        setNoClip(true);
        float forward = 0;
        float strafe = 0;
        float vertical = 0;

        // Get movement input
        if (Keybinds.FORWARDS.isPressedTick()) forward -= 1;
        if (Keybinds.BACKWARDS.isPressedTick()) forward += 1;
        if (Keybinds.RIGHT.isPressedTick()) strafe += 1;
        if (Keybinds.LEFT.isPressedTick()) strafe -= 1;
        if (Keybinds.UP.isPressedTick()) vertical += 1;
        if (Keybinds.DOWN.isPressedTick()) vertical -= 1;

        // 2. Convert the entity's yaw to radians
        // (Ensure you are using standard java.lang.Math here if your engine's Math class lacks toRadians)


        // Speed modifications
        float speed = groundSpeed;
        if (Keybinds.FAST.isPressedTick()) {
            speed = 12.0f;
        } else if (Keybinds.CROUCH.isPressedTick()) {
            speed = 4.0f;
        }

        // Apply movement based on mode
        if (noClip) {
            // Flying mode - move in 3D direction of camera
            if (forward != 0 || strafe != 0 || vertical != 0) {
                moveRelative(forward, strafe, speed);
            }
        } else {
            // Ground mode - horizontal movement only
            if (forward != 0 || strafe != 0) {
                moveRelative(forward, strafe, speed);
            }

            // Jumping
            if (Keybinds.JUMP.isPressedTick()) {
                jump(jumpStrength);
            }
        }
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
            this.world.setBlock(new BlockPos(this.getPosition()), (short) 2);
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