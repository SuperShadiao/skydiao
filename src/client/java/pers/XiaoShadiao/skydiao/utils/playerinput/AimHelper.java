package pers.XiaoShadiao.skydiao.utils.playerinput;

import net.minecraft.client.DeltaTracker;
import net.minecraft.core.BlockPos;
import pers.XiaoShadiao.skydiao.utils.ToolList;

public class AimHelper {

    private float targetYaw;
    private float targetPitch;

    private final DeltaTracker timer = ToolList.mc.getDeltaTracker();
    private final double speed;

    private long lastUpdateTime;

    public AimHelper() {
        this(1);
    }

    public AimHelper(double speed) {
        this.speed = speed;
    }

    private float getSpeedPercent() {
        return (float) (timer.getGameTimeDeltaTicks() * speed * 2.66);
    }

    private float getYawByPercent(float startYaw, float endYaw, float percent) {

        float delta = (endYaw - startYaw) % 360;

        // 将差值归一化到 -180 ~ 180 的范围内
        if (delta > 180) {
            delta -= 360;
        } else if (delta < -180) {
            delta += 360;
        }

        float midYaw = startYaw + delta * percent * getSpeedPercent();

        // 保证返回值在 -180 ~ 180 范围内
        if (midYaw > 180) {
            midYaw -= 360;
        } else if (midYaw < -180) {
            midYaw += 360;
        }

        return midYaw;
    }

    private float getPitchByPercent(float startPitch, float endPitch, float percent) {

        float delta = (endPitch - startPitch) % 360;

        // 将差值归一化到 -180 ~180 的范围内
        if (delta >180) {
            delta -=360;
        } else if (delta < -180) {
            delta +=360;
        }

        //计算所需百分比的位置
        float resultPitch = startPitch + delta * percent * getSpeedPercent();

        // 确保返回值在 -90 ~90 范围内
        if (resultPitch >90) {
            resultPitch =90; // 超过90时固定为90
        } else if (resultPitch < -90) {
            resultPitch = -90; //低于-90时固定为-90
        }

        return resultPitch;
    }

    public void setTarget(float targetYaw, float targetPitch) {
        lastUpdateTime = System.currentTimeMillis();
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
    }

    public void setTarget(Result result) {
        lastUpdateTime = System.currentTimeMillis();
        this.targetYaw = result.yaw();
        this.targetPitch = result.pitch();
    }

    public boolean shouldUpdate() {
        return System.currentTimeMillis() - lastUpdateTime < 250;
    }

    public void updateRotation() {
        if (InputSimulator.isInventoryOpen()) return;

        float yaw = fun(InputSimulator.getPlayerYaw(), getYawByPercent(InputSimulator.getPlayerYaw(), targetYaw, (0.024f + (ToolList.getInstance().random.nextFloat() / 100f - 0.005f)) * 2 * 4));
        float pitch = getPitchByPercent(InputSimulator.getPlayerPitch(), targetPitch, (0.01f + (ToolList.getInstance().random.nextFloat() / 400f - 0.0025f / 2f)) * 4 * 2);

        InputSimulator.setPlayerPitch(pitch);
        InputSimulator.setPlayerYaw(yaw);
    }

    private float fun(float start, float target) {
        // 计算 target 与 start 的差值
        float delta = target - start;

        // 将差值调整到 [-180, 180] 范围
        delta = normalizeAngle(delta);

        // 返回与 target 等价且最接近 start 的值
        float result = start + delta;
        return result;
    }

    private float normalizeAngle(float angle) {
        // 将角度调整到 [-360, 360] 范围
        angle = angle % 360;

        // 进一步调整到 [-180, 180] 范围
        if (angle > 180) {
            angle -= 360;
        } else if (angle < -180) {
            angle += 360;
        }

        return angle;
    }

    public record Result(float yaw, float pitch) {
        public void updateToAimHelper(AimHelper aimHelper) {
            aimHelper.setTarget(yaw, pitch);
            InputSimulator.updateAimHelper(aimHelper);
        }
    }

    public static Result getYawPitchByBlockPos(BlockPos bp) {
        double playerX = ToolList.mc.player.getX();
        double playerY = ToolList.mc.player.getY() + ToolList.mc.player.getEyeHeight();
        double playerZ = ToolList.mc.player.getZ();

        double blockCenterX = bp.getX() + 0.5;
        double blockCenterY = bp.getY() + 0.5;
        double blockCenterZ = bp.getZ() + 0.5;

        // 计算目标位置的偏移量
        double deltaX = blockCenterX - playerX;
        double deltaY = blockCenterY - playerY;
        double deltaZ = blockCenterZ - playerZ;

        // 计算 yaw 和 pitch
        float yaw = (float) (Math.atan2(deltaZ, deltaX) * (180 / Math.PI)) - 90; // Yaw
        float pitch = (float) -(Math.atan2(deltaY, Math.sqrt(deltaX * deltaX + deltaZ * deltaZ)) * (180 / Math.PI)); // Pitch

        return new Result(yaw, pitch);
    }

    public static Result getYawPitchByDoublePos(double x, double y, double z) {
        double playerX = ToolList.mc.player.getX();
        double playerY = ToolList.mc.player.getY() + ToolList.mc.player.getEyeHeight();
        double playerZ = ToolList.mc.player.getZ();

        // 计算目标位置的偏移量
        double deltaX = x - playerX;
        double deltaY = y - playerY;
        double deltaZ = z - playerZ;

        // 计算 yaw 和 pitch
        float yaw = (float) (Math.atan2(deltaZ, deltaX) * (180 / Math.PI)) - 90; // Yaw
        float pitch = (float) -(Math.atan2(deltaY, Math.sqrt(deltaX * deltaX + deltaZ * deltaZ)) * (180 / Math.PI)); // Pitch

        return new Result(yaw, pitch);
    }
}
