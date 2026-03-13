package engine.strata.client.frontend.render.model;

import engine.strata.client.frontend.render.animation.AnimationProcessor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable template representing a bone's static properties from the model file.
 *
 * <h3>DESIGN — Immutability:</h3>
 * <p>This class is a READ-ONLY template. All runtime animation state lives in
 * {@link BoneState}, which is managed by {@link AnimationProcessor}.
 *
 * <h3>What StrataBone Contains (Static):</h3>
 * <ul>
 *   <li>name        – bone identifier</li>
 *   <li>parent      – parent bone reference (hierarchy)</li>
 *   <li>pivot       – rotation pivot point</li>
 *   <li>rotation    – base rotation as {@link Vector3f} in <em>radians</em> (pitch/yaw/roll = X/Y/Z)</li>
 *   <li>meshIds     – list of meshes attached to this bone</li>
 *   <li>children    – child bones</li>
 * </ul>
 *
 * <h3>What Lives in BoneState (Dynamic):</h3>
 * <ul>
 *   <li>rotationOffset  – per-frame rotation delta in radians</li>
 *   <li>positionOffset  – per-frame translation delta</li>
 *   <li>scaleOffset     – per-frame scale multiplier</li>
 *   <li>isVisible       – runtime visibility flag</li>
 * </ul>
 *
 * <h3>Rotation Format:</h3>
 * <p>The base rotation is stored as a {@link Vector3f} where each component is
 * an Euler angle in <em>radians</em>: {@code x=pitch, y=yaw, z=roll}.
 * The model loader converts degrees from the JSON file to radians during loading.
 */
public class StrataBone {

    // ══════════════════════════════════════════════════════════════════════════
    // IMMUTABLE CORE PROPERTIES
    // ══════════════════════════════════════════════════════════════════════════

    private final String name;
    private final StrataBone parent;
    private final Vector3f pivot;
    /** Base rotation in radians (x=pitch, y=yaw, z=roll). */
    private final Vector3f rotation;
    private final List<String> meshIds;
    private final List<StrataBone> children;
    private final boolean defaultHidden;

    // ══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a new immutable bone template.
     *
     * @param name          Bone identifier (e.g. "head", "body", "leftArm")
     * @param parent        Parent bone (null for root)
     * @param pivot         Rotation pivot point
     * @param rotation      Base rotation in <em>radians</em> (x=pitch, y=yaw, z=roll)
     * @param meshIds       Mesh IDs attached to this bone
     * @param defaultHidden Whether this bone is hidden by default
     */
    public StrataBone(String name, StrataBone parent, Vector3f pivot,
                      Vector3f rotation, List<String> meshIds, boolean defaultHidden) {
        this.name          = name;
        this.parent        = parent;
        this.pivot         = new Vector3f(pivot);    // defensive copy
        this.rotation      = new Vector3f(rotation); // defensive copy
        this.meshIds       = new ArrayList<>(meshIds);
        this.children      = new ArrayList<>();
        this.defaultHidden = defaultHidden;
    }

    /**
     * Creates a new immutable bone template (visible by default).
     *
     * @param name     Bone identifier (e.g. "head", "body", "leftArm")
     * @param parent   Parent bone (null for root)
     * @param pivot    Rotation pivot point
     * @param rotation Base rotation in <em>radians</em> (x=pitch, y=yaw, z=roll)
     * @param meshIds  Mesh IDs attached to this bone
     */
    public StrataBone(String name, StrataBone parent, Vector3f pivot,
                      Vector3f rotation, List<String> meshIds) {
        this(name, parent, pivot, rotation, meshIds, false);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ACCESSORS
    // ══════════════════════════════════════════════════════════════════════════

    /** Returns the bone name. */
    public String getName() {
        return name;
    }

    /** Returns the parent bone, or {@code null} for the root. */
    public StrataBone getParent() {
        return parent;
    }

    /**
     * Returns the rotation pivot point.
     * <strong>Do not modify the returned instance.</strong>
     */
    public Vector3f getPivot() {
        return pivot;
    }

    /**
     * Returns the base rotation in <em>radians</em> (x=pitch, y=yaw, z=roll).
     * <strong>Do not modify the returned instance.</strong>
     */
    public Vector3f getRotation() {
        return rotation;
    }

    /** Returns the list of mesh IDs attached to this bone. */
    public List<String> getMeshIds() {
        return meshIds;
    }

    /** Returns the list of child bones. */
    public List<StrataBone> getChildren() {
        return children;
    }

    /** Returns {@code true} if this bone is hidden by default in the model file. */
    public boolean isDefaultHidden() {
        return defaultHidden;
    }

    /**
     * Adds a child bone to this bone's hierarchy.
     * Should only be called during model loading.
     */
    public void addChild(StrataBone child) {
        children.add(child);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HIERARCHY TRAVERSAL
    // ══════════════════════════════════════════════════════════════════════════

    /** Returns {@code true} if this bone is a descendant of {@code ancestor}. */
    public boolean isDescendantOf(StrataBone ancestor) {
        StrataBone current = this.parent;
        while (current != null) {
            if (current == ancestor) return true;
            current = current.parent;
        }
        return false;
    }

    /** Returns the depth of this bone in the hierarchy (root = 0). */
    public int getDepth() {
        int depth = 0;
        StrataBone current = this.parent;
        while (current != null) {
            depth++;
            current = current.parent;
        }
        return depth;
    }

    /** Finds a child bone by name (recursive), or {@code null} if not found. */
    public StrataBone findChildByName(String name) {
        for (StrataBone child : children) {
            if (child.getName().equals(name)) return child;
            StrataBone result = child.findChildByName(name);
            if (result != null) return result;
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // OBJECT METHODS
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof StrataBone other)) return false;
        return Objects.equals(name, other.name) &&
                Objects.equals(parent != null ? parent.name : null,
                        other.parent != null ? other.parent.name : null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, parent != null ? parent.name : null);
    }

    @Override
    public String toString() {
        return String.format("StrataBone{name='%s', pivot=%s, rotation=%s, meshes=%d, children=%d}",
                name, pivot, rotation, meshIds.size(), children.size());
    }
}