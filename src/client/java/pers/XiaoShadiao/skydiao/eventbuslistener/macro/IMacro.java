package pers.XiaoShadiao.skydiao.eventbuslistener.macro;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;

public interface IMacro {

    public boolean isMacroActive();

    /**
     * @return 返回true以取消Macro报警
     */
    public boolean onMacroCheck(PositionInfo beforeTP, PositionInfo afterTP);

    public boolean onMacroCheck(int beforeSlot, int afterSlot);

    public String getMacroName();

    public default void onMacroUnload() {};

    public record PositionInfo(Vec3 position, float yaw, float pitch, Vec3 velocity) {

        public @NotNull String toString() {
            // return "[" + position.x + ", " + position.y + ", " + position.z + ", y=" + yaw + ", p=" + pitch + ", v=" + velocity + "]";
            return String.format("[%.2f, %.2f, %.2f], y=%.2f, p=%.2f, v=[%.2f, %.2f, %.2f]", position.x, position.y, position.z, yaw, pitch, velocity.x, velocity.y, velocity.z);
        }

    }

    public default void activeThisMacro() {
        AbstractListener.mml.addActiveMacro(this);
    }

}
