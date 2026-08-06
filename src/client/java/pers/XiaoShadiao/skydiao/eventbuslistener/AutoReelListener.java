package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.AimHelper;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

public class AutoReelListener extends AbstractListener {

    private final AimHelper aimHelper = new AimHelper();
    private long lastRightClickTime;
    private int delayTick;
    private boolean shouldAimGround;

    @Override
    public String getListenerName() {
        return "AutoReelListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(Minecraft mc) {
        if(mc.level == null || mc.player == null || !ConfigManager.autoReel.getValue()) return;

        if(ToolList.getInstance().tryGetSkyblockItemId(mc.player.getItemHeldByArm(HumanoidArm.RIGHT)).endsWith("_LASSO")) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if(entity instanceof Leashable leashableEntity) {
                    Leashable.LeashData leashData = leashableEntity.getLeashData();
                    if (leashData != null && leashData.leashHolder == mc.player) {;
                        for (Entity entity2 : mc.level.getEntities(entity, entity.getBoundingBox().inflate(2, 10, 2))) {
                            if(entity2 instanceof ArmorStand armorStand) {
                                if(entity2.getName().getString().equals("                    ")) {
                                    if (ConfigManager.autoReelAutoAim.getValue()) (shouldAimGround ? AimHelper.getYawPitchByVec3(mc.player.position().add(mc.player.getForward().horizontal().normalize().scale(0.2))) : AimHelper.getYawPitchByEntityEye(entity)).updateToAimHelper(aimHelper);
                                }
                                if(armorStand.getName().getString().contains("REEL")) {
                                    if(System.currentTimeMillis() - lastRightClickTime > 2000) {
                                        delayTick++;
                                        shouldAimGround = mc.hitResult instanceof EntityHitResult && mc.hitResult.getType() == HitResult.Type.ENTITY;
                                        if(delayTick > (shouldAimGround ? 18 : 8)) {
                                            delayTick = 0;
                                            lastRightClickTime = System.currentTimeMillis();
                                            InputSimulator.singleRightClick();
                                        }
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        delayTick = 0;
        shouldAimGround = false;
    }

}
