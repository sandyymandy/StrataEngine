package engine.strata.client.frontend.render.model;

import engine.strata.client.frontend.render.animation.AnimationProcessor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable template representing a bone's static properties from the model file.
 *
 * <p>This is a read-only template. All runtime animation state lives in
 * {@link BoneState}, managed by {@link AnimationProcessor}.
 *
 * <p>The base rotation is stored as a {@link Vector3f} in radians (x=pitch, y=yaw, z=roll).
 * The model loader converts degrees from JSON to radians during loading.
 */
public class StrataBone {

    private final String name;
    private final StrataBone parent;
    private final Vector3f pivot;
    /** Base rotation in radians (x=pitch, y=yaw, z=roll). */
    private final Vector3f rotation;
    private final List<String> meshIds;
    private final List<StrataBone> children;
    private final boolean defaultHidden;

    public StrataBone(String name, StrataBone parent, Vector3f pivot,
                      Vector3f rotation, List<String> meshIds, boolean defaultHidden) {
        this.name = name;
        this.parent = parent;
        this.pivot = new Vector3f(pivot);
        this.rotation = new Vector3f(rotation);
        this.meshIds = new ArrayList<>(meshIds);
        this.children = new ArrayList<>();
        this.defaultHidden = defaultHidden;
    }

    public StrataBone(String name, StrataBone parent, Vector3f pivot,
                      Vector3f rotation, List<String> meshIds) {
        this(name, parent, pivot, rotation, meshIds, false);
    }

    public String getName() { return name; }
    public StrataBone getParent() { return parent; }

    /** Do not modify the returned instance. */
    public Vector3f getPivot() { return pivot; }

    /** Returns the base rotation in radians (x=pitch, y=yaw, z=roll). Do not modify. */
    public Vector3f getRotation() { return rotation; }

    public List<String> getMeshIds() { return meshIds; }
    public List<StrataBone> getChildren() { return children; }
    public boolean isDefaultHidden() { return defaultHidden; }

    /** Should only be called during model loading. */
    public void addChild(StrataBone child) { children.add(child); }

    // Hierarchy traversal

    public boolean isDescendantOf(StrataBone ancestor) {
        StrataBone current = this.parent;
        while (current != null) {
            if (current == ancestor) return true;
            current = current.parent;
        }
        return false;
    }

    public int getDepth() {
        int depth = 0;
        StrataBone current = this.parent;
        while (current != null) {
            depth++;
            current = current.parent;
        }
        return depth;
    }

    public StrataBone findChildByName(String name) {
        for (StrataBone child : children) {
            if (child.getName().equals(name)) return child;
            StrataBone result = child.findChildByName(name);
            if (result != null) return result;
        }
        return null;
    }

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