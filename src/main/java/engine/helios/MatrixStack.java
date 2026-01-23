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

    public void rotate(float angle, float x, float y, float z) {
        stack.peek().rotate((float) Math.toRadians(angle), x, y, z);
    }

    public void scale(float x, float y, float z) {
        stack.peek().scale(x, y, z);
    }

    public Matrix4f peek() {
        return stack.peek();
    }
}