package engine.strata.client.render.shader;

import engine.strata.util.Identifier;
import org.joml.Matrix4f;

public class StaticShader extends ShaderProgram {
    private static final Identifier VERTEX_FILE = Identifier.ofEngine("vertex");
    private static final Identifier FRAGMENT_FILE = Identifier.ofEngine("fragment");

    private int location_transformationMatrix;
    private int location_projectionMatrix; // Added Projection
    private int location_viewMatrix;       // Added View (Camera)

    public StaticShader() {
        super(VERTEX_FILE, FRAGMENT_FILE);
    }

    @Override
    protected void bindAttributes() {
        super.bindAttribute(0, "position"); // Bind VAO index 0 to "position" in shader
        super.bindAttribute(1, "color");
    }

    @Override
    protected void getAllUniformLocations() {
        location_transformationMatrix = super.getUniformLocation("transformationMatrix");
        location_projectionMatrix = super.getUniformLocation("projectionMatrix");
        location_viewMatrix = super.getUniformLocation("viewMatrix");
    }

    public void loadTransformationMatrix(Matrix4f matrix) {
        super.loadMatrix(location_transformationMatrix, matrix);
    }

    public void loadViewMatrix(Matrix4f view) {
        super.loadMatrix(location_viewMatrix, view);
    }

    public void loadProjectionMatrix(Matrix4f projection) {
        super.loadMatrix(location_projectionMatrix, projection);
    }
}