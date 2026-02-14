package engine.helios;

import engine.strata.core.io.ResourceManager;
import engine.strata.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import static org.lwjgl.opengl.GL20.*;

public class ShaderStack {
    private final int programId;
    private final Map<String, Integer> uniformLocations = new HashMap<>();
    private static final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    public ShaderStack(Identifier vertexId, Identifier fragmentId) {
        String vertSrc = ResourceManager.loadAsString(vertexId, "shaders", "vert");
        String fragSrc = ResourceManager.loadAsString(fragmentId, "shaders", "frag");

        if (vertSrc.isEmpty() || fragSrc.isEmpty()) {
            throw new RuntimeException("Shader source is empty! Check paths for: " + vertexId + " or " + fragmentId);
        }

        int vertexShader = compile(vertSrc, GL_VERTEX_SHADER);
        int fragmentShader = compile(fragSrc, GL_FRAGMENT_SHADER);

        this.programId = glCreateProgram();
        glAttachShader(programId, vertexShader);
        glAttachShader(programId, fragmentShader);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader Link Error: " + glGetProgramInfoLog(programId));
        }

        // Cleanup shaders after linking
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
    }

    public void use() {
        glUseProgram(programId);
    }

    public void setUniform(String name, Matrix4f matrix) {
        int location = uniformLocations.computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
        matrix.get(matrixBuffer);
        glUniformMatrix4fv(location, false, matrixBuffer);
    }

    public void setUniform(String name, boolean value) {
        setUniform(name, value ? 1 : 0);
    }

    public void setUniform(String name, int value) {
        int location = uniformLocations.computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
        glUniform1i(location, value);
    }

    private int compile(String source, int type) {
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader Compilation Error (" +
                    (type == GL_VERTEX_SHADER ? "Vertex" : "Fragment") + "): " + glGetShaderInfoLog(id));
        }
        return id;
    }

    public int getProgramId() { return programId; }
}