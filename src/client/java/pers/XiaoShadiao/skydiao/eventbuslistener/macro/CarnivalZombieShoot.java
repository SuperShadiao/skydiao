package pers.XiaoShadiao.skydiao.eventbuslistener.macro;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.AimHelper;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CarnivalZombieShoot extends AbstractListener implements IMacro {

    public static final BlockPos corner1 = new BlockPos(-122, 68, 35);
    public static final BlockPos corner2 = new BlockPos(-91, 76, 64);

    public static final Vec3 npcPos = new Vec3(-103.5, 70, 38.5);

    public static final BoundingBox area = BoundingBox.fromCorners(corner1, corner2);

    public static final String START_COMMAND = "/selectnpcoption carnival_cowboy r_2_1";
    private boolean isAlertTriggered;

    private int rightClickTick = 0;

    private Target currentTarget = new Target(Vec3.ZERO, Type.LEATHER);

    private record Target(Vec3 pos, Type type) {
    }

    public enum Type {

        LEATHER(1),
        IRON(2),
        GOLD(3),
        LAMP(4),
        DIAMOND(5),
        ;

        private final int priority;
        Type(int priority) {
            this.priority = priority;
        }
        public int getPriority() {
            return priority;
        }

    }

    private static final List<BlockPos> lampPoses = List.of(
            // BlockPos{x=-118, y=76, z=39}, BlockPos{x=-119, y=77, z=42}, BlockPos{x=-101, y=70, z=44}, BlockPos{x=-119, y=75, z=45}, BlockPos{x=-118, y=76, z=49}, BlockPos{x=-117, y=76, z=52}, BlockPos{x=-115, y=77, z=55}, BlockPos{x=-112, y=76, z=58}, BlockPos{x=-109, y=75, z=60}, BlockPos{x=-96, y=76, z=61}, BlockPos{x=-106, y=77, z=61}, BlockPos{x=-102, y=75, z=62}, BlockPos{x=-99, y=77, z=62}
            new BlockPos(-118, 76, 39),
            new BlockPos(-119, 77, 42),
            // new BlockPos(-101, 70, 44), SPAWN
            new BlockPos(-119, 75, 45),
            new BlockPos(-118, 76, 49),
            new BlockPos(-117, 76, 52),
            new BlockPos(-115, 77, 55),
            new BlockPos(-112, 76, 58),
            new BlockPos(-109, 75, 60),
            new BlockPos(-96, 76, 61),
            new BlockPos(-106, 77, 61),
            new BlockPos(-102, 75, 62),
            new BlockPos(-99, 77, 62)
    );

    @Override
    public String getListenerName() {
        return "CarnivalZombieShoot";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientStartTick);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::onUnload);
    }

    private void onUnload(Minecraft mc, ClientLevel level) {
        isAlertTriggered = false;
        rightClickTick = 0;
    }

    private void onClientStartTick(Minecraft mc) {
        if(!ConfigManager.carnivalAutoShootZombie.getValue()) {
            return;
        }

        if(mc.player == null || mc.level == null) {
            return;
        }

        if(isAlertTriggered && MacroManagerListener.pathFinderExecutor.isRunning()) {
            MacroManagerListener.pathFinderExecutor.stopExecution();
            return;
        }
        if(isAlertTriggered) return;

        if(!isHoldingShooter()) return;

        activeThisMacro();
        List<Target> list = new ArrayList<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if(entity instanceof Zombie zombie && !zombie.isInvisible() && AABB.of(area).contains(zombie.position())) {
                Item item = zombie.getItemBySlot(EquipmentSlot.HEAD).getItem();

                double multiply = (zombie.isBaby() ? Mth.clampedLerp(ToolList.getInstance().random.nextDouble(), 1.5, 2) : 1) * 1.5 * ConfigManager.carnivalAutoShootZombieOffset.getValue();
                Vec3 position = zombie.getEyePosition().add(zombie.getForward().horizontal().normalize().multiply(multiply, multiply, multiply)).add(0, 0.3, 0);

                if(item == Items.LEATHER_HELMET) {
                    list.add(new Target(position, Type.LEATHER));
                } else if(item == Items.IRON_HELMET) {
                    list.add(new Target(position, Type.IRON));
                } else if(item == Items.GOLDEN_HELMET) {
                    list.add(new Target(position, Type.GOLD));
                } else if(item == Items.DIAMOND_HELMET) {
                    list.add(new Target(position, Type.DIAMOND));
                }
            }
        }

        for (BlockPos lampPos : lampPoses) {
            if (isRedstoneLampLit(lampPos)) {
                list.add(new Target(new Vec3(lampPos.getX() + 0.5, lampPos.getY() + 0.8, lampPos.getZ() + 0.5), Type.LAMP));
            }
        }

        Target temp2 = list.stream().min(
                Comparator.<Target>comparingInt(target -> target.type.getPriority())
                        .reversed()
                        .thenComparingDouble(target -> target.pos.distanceToSqr(currentTarget.pos))
        ).orElse(currentTarget);
        if(currentTarget.pos.distanceTo(temp2.pos) > 0.75) {
            rightClickTick = 5;
        }
        currentTarget = temp2;

        AimHelper.getYawPitchByVec3(currentTarget.pos).updateToAimHelper(new AimHelper(3));

        if(rightClickTick > 0) rightClickTick--;
        if(rightClickTick == 0) {
            InputSimulator.pressRightClick();
        } else {
            InputSimulator.releaseRightClick();
        }

    }

    private void onLastRender(LevelRenderContext context) {
        if(mc.level != null && isHoldingShooter()) {
            RenderUtils.WorldRender wr1 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_FILL);
            RenderUtils.WorldRender wr2 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_LINE);

            for (BlockPos blockPos : lampPoses) {
                if(isRedstoneLampLit(blockPos)) {
                    RenderUtils.renderESP(wr1, blockPos, 1 ,1, 0, 1, true);
                    RenderUtils.renderESP(wr2, blockPos, 1 ,1, 0, 1, false);
                }
            }

            wr1.finishDraw();
            wr2.finishDraw();
        }
    }

    private void onChat(Component component, boolean b) {
        String msg = ToolList.getInstance().deleteColorCode(component.getString());

        if(!ConfigManager.carnivalAutoShootZombie.getValue()) return;
        if(msg.contains("You earned") && msg.contains("Carnival Tokens!")) {
            InputSimulator.releaseRightClick();
            if(!isAlertTriggered) {
                ToolList.addThreadedTask(() -> {
                    Thread.sleep(100);
                    InputSimulator.releaseRightClick();

                    if(mc.player != null && mc.player.distanceToSqr(npcPos) > 64) return null;
                    MacroManagerListener.pathFinderExecutor.startExecution(new Vec3(-103, 70, 38), false);

                    do {
                        Thread.sleep(100);
                    } while(MacroManagerListener.pathFinderExecutor.isRunning());

                    AimHelper aimHelper = new AimHelper();
                    int i = 0;
                    while(i < 50) {
                        AimHelper.getYawPitchByDoublePos(npcPos.x, npcPos.y, npcPos.z).updateToAimHelper(aimHelper);
                        i++;
                        Thread.sleep(10);
                    }
                    InputSimulator.singleLeftClick();
                    return null;
                });
            } else {
                isAlertTriggered = false;
            }
        } else if ("[NPC] Carnival Cowboy: Wouldja like to play Zombie Shootout?".equals(msg)) {
            ToolList.addThreadedTask(() -> {
                Thread.sleep(1000);
                ToolList.sendChatMessage(START_COMMAND);
                return null;
            });
        }
    }

    public boolean isHoldingShooter() {
        if(mc.player == null) return false;
        return "Carnival Dart Tube".equals(ToolList.getInstance().deleteColorCode(mc.player.getInventory().getSelectedItem().getHoverName().getString()));
    }

    private boolean isRedstoneLampLit(BlockPos blockPos) {
        if(mc.level == null) return false;
        BlockState blockState = mc.level.getBlockState(blockPos);
        return blockState.getBlock() == Blocks.REDSTONE_LAMP && blockState.getValue(RedstoneTorchBlock.LIT);
    }

    @Override
    public boolean isMacroActive() {
        return isHoldingShooter();
    }

    @Override
    public boolean onMacroCheck(PositionInfo beforeTP, PositionInfo afterTP) {
        boolean flag = afterTP.position().distanceToSqr(-96.50, 70.00, 37.50) < 0.01;
        if(!flag) {
            MacroManagerListener.pathFinderExecutor.stopExecution();
            isAlertTriggered = true;
        }
        return flag;
    }

    @Override
    public void onMacroUnload() {
        MacroManagerListener.pathFinderExecutor.stopExecution();
    }

    @Override
    public boolean onMacroCheck(int beforeSlot, int afterSlot) {
        return true;
    }

    @Override
    public String getMacroName() {
        return "CZS";
    }

}