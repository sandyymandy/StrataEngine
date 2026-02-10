package engine.strata.util.math;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import static java.lang.Math.sqrt;

public class Vec3d {
    private double x;
    private double y;
    private double z;

    public static Vec3d ZERO = new Vec3d(0,0,0);

    public Vec3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3d(Vector3d vector3d){
        this(vector3d.x(), vector3d.y(), vector3d.z());
    }

    public Vec3d(Vector3f vector3f){
        this(vector3f.x(), vector3f.y(), vector3f.z());
    }

    public Vec3d(Vec3f vec3f){
        this(vec3f.getX(), vec3f.getY(), vec3f.getZ());
    }

    public Vec3d(){
        this(0, 0, 0);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public void set(double x, double y, double z){
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void set(Vec3d pos) {
        set(pos.getX(), pos.getY(), pos.getZ());
    }

    public Vec3d normalize() {
        double d = sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        return d < 1.0E-5F ? ZERO : new Vec3d(this.x / d, this.y / d, this.z / d);
    }

    public double dotProduct(Vec3d vec) {
        return this.x * vec.x + this.y * vec.y + this.z * vec.z;
    }

    public Vec3d add(double x, double y, double z) {
        return new Vec3d(this.x + x, this.y + y, this.z + z);
    }

    public Vec3d add(Vec3d vec) {
        return this.add(vec.x, vec.y, vec.z);
    }

    public Vec3d add(double value) {
        return this.add(value, value, value);
    }

    public Vec3d subtract(double x, double y, double z) {
        return this.add(-x, -y, -z);
    }

    public Vec3d subtract(Vec3d vec) {
        return this.subtract(vec.x, vec.y, vec.z);
    }

    public Vec3d subtract(double value) {
        return this.subtract(value, value, value);
    }

    public Vector3d toVector3d(){
        return new Vector3d(x,y,z);
    }

    public Vector3f toVector3f(){
        return new Vector3f((float)x,(float)y,(float)z);
    }

    public Vec3f toVec3f(){
        return new Vec3f(this);
    }

    public Vec3d rotate(Quaternionf quat) {
        return new Vec3d(quat.transform(toVector3d(), toVector3d()));
    }

}
