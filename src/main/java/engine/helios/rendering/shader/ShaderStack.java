package engine.helios.rendering.shader;

import engine.strata.core.io.ResourceManager;
import engine.strata.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

public class ShaderStack {
    private final int programId;
    private final Map<String, Integer> uniformLocations = new HashMap<>();

    // Shared, thread-local-ish buffers — always used on the render thread.
    private static final FloatBuffer MAT_BUF = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer VEC4_BUF = BufferUtils.createFloatBuffer(4);

    // ── Construction ──────────────────────────────────────────────────────────

    public ShaderStack(Identifier vertexId, Identifier fragmentId) {
        String vertSrc = ResourceManager.loadAsString(vertexId, "shaders", "vert");
        String fragSrc = ResourceManager.loadAsString(fragmentId, "shaders", "frag");

        if (vertSrc.isEmpty() || fragSrc.isEmpty()) {
            throw new RuntimeException("Shader source is empty! Check paths for: "
                    + vertexId + " or " + fragmentId);
        }

        int vert = compile(vertSrc, GL_VERTEX_SHADER);
        int frag = compile(fragSrc, GL_FRAGMENT_SHADER);

        this.programId = glCreateProgram();
        glAttachShader(programId, vert);
        glAttachShader(programId, frag);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader link error: " + glGetProgramInfoLog(programId));
        }

        glDeleteShader(vert);
        glDeleteShader(frag);
    }

    // ── Activation ────────────────────────────────────────────────────────────

    public void use() {
        glUseProgram(programId);
    }

    // ── Uniform setters ───────────────────────────────────────────────────────

    /** Upload a 4×4 matrix uniform. */
    public void setUniform(String name, Matrix4f matrix) {
        int loc = loc(name);
        matrix.get(MAT_BUF);
        glUniformMatrix4fv(loc, false, MAT_BUF);
    }

    /** Upload a vec4 uniform (e.g. {@code u_Tint}). */
    public void setUniform(String name, Vector4f vec) {
        int loc = loc(name);
        vec.get(VEC4_BUF);
        glUniform4fv(loc, VEC4_BUF);
    }

    /** Upload a vec4 uniform via individual components. */
    public void setUniform(String name, float r, float g, float b, float a) {
        glUniform4f(loc(name), r, g, b, a);
    }

    /** Upload a single float uniform. */
    public void setUniform(String name, float value) {
        glUniform1f(loc(name), value);
    }

    /** Upload a boolean (mapped to int 0/1) uniform. */
    public void setUniform(String name, boolean value) {
        setUniform(name, value ? 1 : 0);
    }

    /** Upload an integer uniform. */
    public void setUniform(String name, int value) {
        glUniform1i(loc(name), value);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int loc(String name) {
        return uniformLocations.computeIfAbsent(name,
                n -> glGetUniformLocation(programId, n));
    }

    private int compile(String source, int type) {
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader compilation error ("
                    + (type == GL_VERTEX_SHADER ? "Vertex" : "Fragment") + "): "
                    + glGetShaderInfoLog(id));
        }
        return id;
    }

    public int getProgramId() { return programId; }
}