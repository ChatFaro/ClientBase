package cn.clientbase.util.player;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.math.MathHelper;

@Getter
@Setter
public class Rotation implements Cloneable {
    private float yaw;
    private float pitch;

    public Rotation() { this.yaw = 0f; this.pitch = 0f; }

    public Rotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public void setYawPitch(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override
    public Rotation clone() {
        return new Rotation(this.yaw, this.pitch);
    }

    public static float moveTowards(float current, float target, float maxStep) {
        float diff = MathHelper.wrapDegrees(target - current);
        diff = MathHelper.clamp(diff, -maxStep, maxStep);
        return current + diff;
    }
}
