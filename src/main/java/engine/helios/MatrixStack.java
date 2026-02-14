package engine.helios;

import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;

public class MatrixStack {
    private final Deque<Matrix4f> stack = new ArrayDeque<>();

    public MatrixStack() {
        stack.push(new Matrix4f());
    }

    public void push() {
        stack.push(new Matrix4f(stack.peek()));
    }

    public void pop() {
        if (stack.size() > 1) stack.pop();
    }

    public void translate(float x, float y, float z) {
        stack.peek().translate(x, y, z);
    }

    public void rotateXYZ(float angle, float x, float y, float z) {
        stack.peek().rotate((float) Math.toRadians(angle), x, y, z);
    }

    public void rotateXYZ(float xAngle, float yAngle, float zAngle) {
        stack.peek().rotate((float) Math.toRadians(xAngle), 1, 0, 0);
        stack.peek().rotate((float) Math.toRadians(yAngle), 0, 1, 0);
        stack.peek().rotate((float) Math.toRadians(zAngle), 0, 0, 1);
    }

    public void rotateZYX(float zAngle, float yAngle, float xAngle) {
        stack.peek().rotate((float) Math.toRadians(zAngle), 0, 0, 1);
        stack.peek().rotate((float) Math.toRadians(yAngle), 0, 1, 0);
        stack.peek().rotate((float) Math.toRadians(xAngle), 1, 0, 0);
    }

    public void scale(float x, float y, float z) {
        stack.peek().scale(x, y, z);
    }

    public void identity() {
        stack.peek().identity();
    }

    public Matrix4f peek() {
        return stack.peek();
    }
}