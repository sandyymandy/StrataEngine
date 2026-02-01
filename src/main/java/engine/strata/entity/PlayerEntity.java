package engine.strata.entity;

import engine.strata.client.StrataClient;
import engine.strata.client.input.keybind.Keybinds;
import engine.strata.client.window.Window;
import engine.strata.world.World;

public class PlayerEntity extends Entity {
    private float speed;
    private float normalSpeed = 0.25f;
    private float slowSpeed = 0.05f;
    private boolean firstMouse = true;
    private double lastMouseX, lastMouseY;
    private float sensitivity = 0.15f;

    public PlayerEntity(EntityKey<?> key, World world) {
        super(key, world);
    }

    @Override
    public void tick() {
        super.tick();
        updateMovement();
    }

    public void handleMouse() {
        Window window = StrataClient.getInstance().getWindow();
        double x = window.getMouseX();
        double y = window.getMouseY();

        if (firstMouse) {
            lastMouseX = x;
            lastMouseY = y;
            firstMouse = false;
        }

        float offsetX = (float) (x - lastMouseX);
        float offsetY = (float) (lastMouseY - y);
        lastMouseX = x;
        lastMouseY = y;

        this.setYaw(this.getYaw() + offsetX * sensitivity);
        this.setPitch(this.getPitch() - offsetY * sensitivity); // Adjusted sign for standard feel

        if (this.getPitch() > 89.0f) this.setPitch(89.0f);
        if (this.getPitch() < -89.0f) this.setPitch(-89.0f);
    }

    public void updateMovement() {
        // Calculate direction based on YAW
        float radYaw = (float) Math.toRadians(this.getYaw());
        float sin = (float) Math.sin(radYaw);
        float cos = (float) Math.cos(radYaw);

        speed = normalSpeed;

        if(Keybinds.SLOW.isActive()) {
            speed = slowSpeed;
        }

        if (Keybinds.RIGHT.isActive()) {
            this.setPosX(this.getX() - sin * speed);
            this.setPosZ(this.getZ() + cos * speed);
        }
        if (Keybinds.LEFT.isActive()) {
            this.setPosX(this.getX() + sin * speed);
            this.setPosZ(this.getZ() - cos * speed);
        }
        if (Keybinds.FORWARDS.isActive()) {
            this.setPosX(this.getX() + cos * speed);
            this.setPosZ(this.getZ() + sin * speed);
        }
        if (Keybinds.BACKWARDS.isActive()) {
            this.setPosX(this.getX() - cos * speed);
            this.setPosZ(this.getZ() - sin * speed);
        }
        if (Keybinds.UP.isActive()) {
            this.setPosY(this.getY() + speed);
        }
        if (Keybinds.DOWN.isActive()) {
            this.setPosY(this.getY() - speed);
        }
    }
}
