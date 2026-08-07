package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.keybinds.KeyBindsManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.Objects;

public class FreecamAndFreelook extends AbstractListener {

    private boolean isFreecam = false;
    private boolean isFreelook = false;

    private boolean isHoldingFreelook = false;

    private CameraEntity cameraEntity;

    @Override
    public String getListenerName() {
        return "FreecamAndFreelook";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(Minecraft mc) {
        if(mc.player == null || mc.level == null) {
            cameraEntity = null;
            isFreelook = false;
            isFreecam = false;
            return;
        }
        boolean toggled = false;
        while (KeyBindsManager.toggleFreecam.consumeClick()) {
            if(!isFreelook) {
                isFreecam = !isFreecam;
                if(isFreecam) {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] 已开启Freecam"));
                } else {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §c已关闭Freecam"));
                }
                toggled = true;
            } else {
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §c请先关闭Freelook再打开Freecam"));
            }
        }
        while (KeyBindsManager.toggleFreelook.consumeClick()) {
            if(!isFreecam) {
                isFreelook = !isFreelook;
                if(isFreelook) {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] 已开启Freelook"));
                } else {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §c已关闭Freelook"));
                }
                toggled = true;
            } else {
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §c请先关闭Freecam再打开Freelook"));
            }
        }

        if(KeyBindsManager.pressFreelook.isDown() && !isHoldingFreelook) {
            isHoldingFreelook = true;
            isFreelook = true;
            toggled = true;
        } else if(!KeyBindsManager.pressFreelook.isDown() && isHoldingFreelook) {
            isHoldingFreelook = false;
            isFreelook = false;
            toggled = true;
        }

        if(toggled) {
            if(!isFreelook && !isFreecam) {
                // mc.setCameraEntity(mc.player);
                cameraEntity = null;
                return;
            }
            cameraEntity = new CameraEntity(mc.level, mc.player.getGameProfile());
            // mc.setCameraEntity(cameraEntity);
            if(isFreelook) {
                cameraEntity.setCameraType(CameraEntity.CameraType.FREELOOK);
            } else if(isFreecam) {
                cameraEntity.setCameraType(CameraEntity.CameraType.FREECAM);
            }
            cameraEntity.setPos(mc.player.position());
            cameraEntity.setXRot(mc.player.getXRot());
            cameraEntity.setYRot(mc.player.getYRot());
            cameraEntity.setOldPosAndRot();
            cameraEntity.tick();
        }
        if(cameraEntity != null) {
            if(cameraEntity.cameraType == CameraEntity.CameraType.FREECAM) {
                Vec3 offset = Vec3.ZERO;
                Vec3 forward = cameraEntity.getForward().horizontal().normalize();
                if(mc.options.keyUp.isDown()) {
                    offset = offset.add(forward);
                }
                if(mc.options.keyDown.isDown()) {
                    offset = offset.add(forward.reverse());
                }
                if(mc.options.keyLeft.isDown()) {
                    offset = offset.add(forward.reverse().rotateClockwise90());
                }
                if(mc.options.keyRight.isDown()) {
                    offset = offset.add(forward.rotateClockwise90());
                }
                if(mc.options.keyJump.isDown()) {
                    offset = offset.add(0, 0.5, 0);
                }
                if(mc.options.keyShift.isDown()) {
                    offset = offset.add(0, -0.5, 0);
                }

                cameraEntity.setOldPos();
                Vec3 position = cameraEntity.position();
                double value = ConfigManager.freecamFlySpeed.getValue();
                cameraEntity.setPos(position.add(offset.multiply(value, value, value)));
                cameraEntity.tick();
            } else if(cameraEntity.cameraType == CameraEntity.CameraType.FREELOOK) {
                cameraEntity.setOldPos();
                cameraEntity.setPos(mc.player.position());
                cameraEntity.tick();
            }
        }
    }

    public boolean handlePlayerTurn(double xo, double yo) {
        if(cameraEntity == null) {
            return false;
        }
        cameraEntity.turn(xo, yo);
        return true;
    }

    public CameraEntity getCameraEntity() {
        return cameraEntity;
    }

    public static class CameraEntity extends AbstractClientPlayer {

        private final LocalPlayer player = Objects.requireNonNull(mc.player);
        private CameraType cameraType;

        public CameraType getCameraType() {
            return cameraType;
        }

        public enum CameraType {
            FREECAM,
            FREELOOK
        }
        public CameraEntity(ClientLevel level, GameProfile gameProfile) {
            super(level, gameProfile);
        }
        public void setCameraType(CameraType cameraType) {
            this.cameraType = cameraType;
        }

        @Override
        public @NonNull Inventory getInventory() {
            return player.getInventory();
        }

        @Override
        protected void setOldPos() {
            super.setOldPos();
        }

        @Override
        public void setOldRot() {
            super.setOldRot();
        }

        @Override
        public void tick() {
            super.tick();
//            double xd = this.getX() - this.xo;
//            double zd = this.getZ() - this.zo;
//            float sideDist = (float)(xd * xd + zd * zd);
//            float yBodyRotT = this.yBodyRot;
//            if (sideDist > 0.0025000002F) {
//                float walkDirection = (float) Mth.atan2(zd, xd) * (180.0F / (float)Math.PI) - 90.0F;
//                float diffBetweenDirectionAndFacing = Mth.abs(Mth.wrapDegrees(this.getYRot()) - walkDirection);
//                if (95.0F < diffBetweenDirectionAndFacing && diffBetweenDirectionAndFacing < 265.0F) {
//                    yBodyRotT = walkDirection - 180.0F;
//                } else {
//                    yBodyRotT = walkDirection;
//                }
//            }
//
//            if (this.attackAnim > 0.0F) {
//                yBodyRotT = this.getYRot();
//            }
//
//            ProfilerFiller profiler = Profiler.get();
//            profiler.push("headTurn");
//            this.tickHeadTurn(yBodyRotT);
//            profiler.pop();
//            profiler.push("rangeChecks");
//
//            while (this.getYRot() - this.yRotO < -180.0F) {
//                this.yRotO -= 360.0F;
//            }
//
//            while (this.getYRot() - this.yRotO >= 180.0F) {
//                this.yRotO += 360.0F;
//            }
//
//            while (this.yBodyRot - this.yBodyRotO < -180.0F) {
//                this.yBodyRotO -= 360.0F;
//            }
//
//            while (this.yBodyRot - this.yBodyRotO >= 180.0F) {
//                this.yBodyRotO += 360.0F;
//            }
//
//            while (this.getXRot() - this.xRotO < -180.0F) {
//                this.xRotO -= 360.0F;
//            }
//
//            while (this.getXRot() - this.xRotO >= 180.0F) {
//                this.xRotO += 360.0F;
//            }
//
//            while (this.yHeadRot - this.yHeadRotO < -180.0F) {
//                this.yHeadRotO -= 360.0F;
//            }
//
//            while (this.yHeadRot - this.yHeadRotO >= 180.0F) {
//                this.yHeadRotO += 360.0F;
//            }
        }
    }
}
