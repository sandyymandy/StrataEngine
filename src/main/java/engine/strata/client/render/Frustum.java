package engine.strata.client.render;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Represents the camera's view frustum for culling.
 * Uses JOML's built-in FrustumIntersection for efficient calculations.
 */
public class Frustum {
    private final FrustumIntersection frustumIntersection;
    private final Matrix4f viewProjection;

    // Cached planes for debugging/visualization
    private final Vector3f[] planes = new Vector3f[6];

    public Frustum() {
        this.frustumIntersection = new FrustumIntersection();
        this.viewProjection = new Matrix4f();

        for (int i = 0; i < 6; i++) {
            planes[i] = new Vector3f();
        }
    }

    /**
     * Updates the frustum based on the view-projection matrix.
     * Call this every frame after updating the camera.
     */
    public void update(Matrix4f projection, Matrix4f view) {
        // Combine projection and view matrices
        projection.mul(view, viewProjection);

        // Update the frustum intersection helper
        frustumIntersection.set(viewProjection);
    }

    /**
     * Tests if a point is inside the frustum.
     */
    public boolean testPoint(float x, float y, float z) {
        return frustumIntersection.testPoint(x, y, z);
    }

    /**
     * Tests if a sphere is inside or intersecting the frustum.
     * @param x Center X coordinate
     * @param y Center Y coordinate
     * @param z Center Z coordinate
     * @param radius Sphere radius
     * @return true if the sphere is visible
     */
    public boolean testSphere(float x, float y, float z, float radius) {
        return frustumIntersection.testSphere(x, y, z, radius);
    }

    /**
     * Tests if an axis-aligned bounding box (AABB) is inside or intersecting the frustum.
     * @param minX Minimum X coordinate
     * @param minY Minimum Y coordinate
     * @param minZ Minimum Z coordinate
     * @param maxX Maximum X coordinate
     * @param maxY Maximum Y coordinate
     * @param maxZ Maximum Z coordinate
     * @return true if the AABB is visible
     */
    public boolean testAabb(float minX, float minY, float minZ,
                            float maxX, float maxY, float maxZ) {
        return frustumIntersection.testAab(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Tests if an AABB centered at a point is visible.
     * Useful for chunks and entities with known sizes.
     */
    public boolean testAabbCentered(float centerX, float centerY, float centerZ,
                                    float halfSizeX, float halfSizeY, float halfSizeZ) {
        return testAabb(
                centerX - halfSizeX, centerY - halfSizeY, centerZ - halfSizeZ,
                centerX + halfSizeX, centerY + halfSizeY, centerZ + halfSizeZ
        );
    }

    /**
     * Tests if a cube centered at a point is visible.
     * @param centerX Center X coordinate
     * @param centerY Center Y coordinate
     * @param centerZ Center Z coordinate
     * @param size Full size of the cube (not half-size)
     * @return true if the cube is visible
     */
    public boolean testCube(float centerX, float centerY, float centerZ, float size) {
        float halfSize = size / 2.0f;
        return testAabbCentered(centerX, centerY, centerZ, halfSize, halfSize, halfSize);
    }

    /**
     * Gets the view-projection matrix used for this frustum.
     * Useful for debugging.
     */
    public Matrix4f getViewProjection() {
        return new Matrix4f(viewProjection);
    }

    /**
     * Distance-based culling check combined with frustum culling.
     * Tests both if object is in frustum AND within max distance.
     */
    public boolean testSphereWithDistance(float x, float y, float z, float radius,
                                          float camX, float camY, float camZ,
                                          float maxDistance) {
        // First check distance (cheaper than frustum test)
        float dx = x - camX;
        float dy = y - camY;
        float dz = z - camZ;
        float distSq = dx * dx + dy * dy + dz * dz;
        float maxDistSq = (maxDistance + radius) * (maxDistance + radius);

        if (distSq > maxDistSq) {
            return false; // Too far
        }

        // Then check frustum
        return testSphere(x, y, z, radius);
    }

    /**
     * Tests if an AABB is visible with distance culling.
     */
    public boolean testAabbWithDistance(float minX, float minY, float minZ,
                                        float maxX, float maxY, float maxZ,
                                        float camX, float camY, float camZ,
                                        float maxDistance) {
        // Calculate center of AABB
        float centerX = (minX + maxX) / 2.0f;
        float centerY = (minY + maxY) / 2.0f;
        float centerZ = (minZ + maxZ) / 2.0f;

        // Calculate approximate radius (distance from center to corner)
        float dx = maxX - centerX;
        float dy = maxY - centerY;
        float dz = maxZ - centerZ;
        float radius = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Distance check
        dx = centerX - camX;
        dy = centerY - camY;
        dz = centerZ - camZ;
        float distSq = dx * dx + dy * dy + dz * dz;
        float maxDistSq = (maxDistance + radius) * (maxDistance + radius);

        if (distSq > maxDistSq) {
            return false; // Too far
        }

        // Frustum check
        return testAabb(minX, minY, minZ, maxX, maxY, maxZ);
    }
}