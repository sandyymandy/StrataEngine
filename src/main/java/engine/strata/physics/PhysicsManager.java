package engine.strata.physics;

import com.github.stephengold.joltjni.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PhysicsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhysicsManager.class);

    private PhysicsSystem physicsSystem;
    private BodyInterface bodyInterface;

    private TempAllocator tempAllocator;
    private JobSystem jobSystem;

    // Object layers (consistent with your earlier choice)
    public static final int LAYER_DYNAMIC = 0;   // moving things (props, entities, falling blocks)
    public static final int LAYER_STATIC   = 1;   // world chunks / terrain

    public void init() {
        // Make sure natives are loaded first (call this early, e.g. in game startup)
        JoltLoader.load();

        JoltPhysicsObject.startCleaner();           // important — reclaims native memory
        Jolt.registerDefaultAllocator();
        Jolt.installDefaultAssertCallback();
        Jolt.installDefaultTraceCallback();
        boolean ok = Jolt.newFactory();
        if (!ok) {
            throw new RuntimeException("Failed to create Jolt factory");
        }
        Jolt.registerTypes();

        // ───────────────────────────────────────────────
        // Layer configuration (very similar to HelloJoltJni example)
        // ───────────────────────────────────────────────
        int numObjLayers = 2;

        ObjectLayerPairFilterTable pairFilter = new ObjectLayerPairFilterTable(numObjLayers);
        pairFilter.enableCollision(LAYER_DYNAMIC, LAYER_DYNAMIC);     // props collide with props
        pairFilter.enableCollision(LAYER_DYNAMIC, LAYER_STATIC);      // props collide with world
        pairFilter.disableCollision(LAYER_STATIC, LAYER_STATIC);      // world chunks don't collide with each other

        // For simplicity we use 1 broad-phase layer (good enough for voxel games)
        // You can later go to 2 BP layers if perf becomes an issue
        int numBpLayers = 1;

        BroadPhaseLayerInterfaceTable bpInterface = new BroadPhaseLayerInterfaceTable(numObjLayers, numBpLayers);
        bpInterface.mapObjectToBroadPhaseLayer(LAYER_DYNAMIC, (short) 0);
        bpInterface.mapObjectToBroadPhaseLayer(LAYER_STATIC,   (short) 0);

        ObjectVsBroadPhaseLayerFilter ovbFilter = new ObjectVsBroadPhaseLayerFilterTable(
                bpInterface, numBpLayers,
                pairFilter, numObjLayers
        );

        // ───────────────────────────────────────────────
        // Create PhysicsSystem
        // ───────────────────────────────────────────────
        physicsSystem = new PhysicsSystem();

        int maxBodies       = 65536;
        int numMutexes      = 0;           // 0 = auto
        int maxBodyPairs    = 65536 * 4;
        int maxContacts     = 20480;

        physicsSystem.init(maxBodies, numMutexes, maxBodyPairs, maxContacts,
                bpInterface, ovbFilter, pairFilter);

        physicsSystem.setGravity(new Vec3(0, -9.81f, 0));

        bodyInterface = physicsSystem.getBodyInterface();

        // Allocators & threading
        tempAllocator = new TempAllocatorImplWithMallocFallback(32 * 1024 * 1024); // 32 MiB
        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        jobSystem = new JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, numThreads);

        LOGGER.info("Jolt-JNI initialized | max bodies: {} | threads: {}", maxBodies, numThreads);
    }

    public void update(float deltaTime) {
        // Fixed timestep recommended — adjust collisionSteps if needed
        final float fixedStep = 1f / 60f;
        int collisionSteps = 1; // or 2–3 if fast objects / tunneling issues

        int error = physicsSystem.update(deltaTime, collisionSteps, tempAllocator, jobSystem);
        if (error != 0) {
            LOGGER.warn("Physics update error code: {}", error);
        }
    }

    public BodyInterface getBodyInterface() {
        return bodyInterface;
    }

    public PhysicsSystem getPhysicsSystem() {
        return physicsSystem;
    }

    public void shutdown() {
        if (jobSystem != null) {
            jobSystem.close();
            jobSystem = null;
        }
        if (tempAllocator != null) {
            tempAllocator.close();
            tempAllocator = null;
        }
        if (physicsSystem != null) {
            physicsSystem.close();
            physicsSystem = null;
        }
        Jolt.destroyFactory(); // important cleanup
        LOGGER.info("Jolt-JNI shutdown complete");
    }
}