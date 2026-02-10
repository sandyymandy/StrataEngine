package engine.strata.util.math;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import static java.lang.Math.sqrt;

public class Vec3f {
    private float x;
    private float y;
    private float z;

    public static Vec3f ZERO = new Vec3f(0,0,0);

    public Vec3f(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3f(Vector3d vector3d){
        this((float) vector3d.x(), (float) vector3d.y(), (float) vector3d.z());
    }

    public Vec3f(Vector3f vector3f){
        this(vector3f.x(), vector3f.y(), vector3f.z());
    }

    public Vec3f(Vec3d vec3d){
        this((float) vec3d.getX(), (float) vec3d.getY(), (float) vec3d.getZ());
    }

    public Vec3f(){
        this(0, 0, 0);
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }

    public void setX(float x) {
        this.x = x;
    }

    public void setY(float y) {
        this.y = y;
    }

    public void setZ(float z) {
        this.z = z;
    }

    public void set(float x, float y, float z){
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void set(Vec3f pos) {
        set(pos.getX(), pos.getY(), pos.getZ());
    }

    public Vec3f normalize() {
        float d = (float) sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        return d < 1.0E-5F ? ZERO : new Vec3f(this.x / d, this.y / d, this.z / d);
    }

    public float dotProduct(Vec3f vec) {
        return this.x * vec.x + this.y * vec.y + this.z * vec.z;
    }

    public Vec3f add(float x, float y, float z) {
        return new Vec3f(this.x + x, this.y + y, this.z + z);
    }

    public Vec3f add(Vec3f vec) {
        return this.add(vec.x, vec.y, vec.z);
    }

    public Vec3f add(float value) {
        return this.add(value, value, value);
    }

    public Vec3f subtract(float x, float y, float z) {
        return this.add(-x, -y, -z);
    }

    public Vec3f subtract(Vec3f vec) {
        return this.subtract(vec.x, vec.y, vec.z);
    }

    public Vec3f subtract(float value) {
        return this.subtract(value, value, value);
    }

    public Vector3d toVector3d(){
        return new Vector3d(x,y,z);
    }

    public Vector3f toVector3f(){
        return new Vector3f(x,y,z);
    }

    public Vec3d toVec3d(){
        return new Vec3d(this);
    }

    public Vec3f rotate(Quaternionf quat) {
        return new Vec3f(quat.transform(toVector3d(), toVector3d()));
    }

}
