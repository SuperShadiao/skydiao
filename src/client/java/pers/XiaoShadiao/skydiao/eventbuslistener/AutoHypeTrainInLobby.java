package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.customsounds.CustomSounds;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.AimHelper;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.PathRenderer;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

public class AutoHypeTrainInLobby extends AbstractListener {

    private boolean tipped = false;
    private boolean isDrivingTrain = false;

    private final Queue<BlockPos> path = new ConcurrentLinkedDeque<>();

    private BlockPos current = null;
    private BlockPos tail = null;

    private BlockPos[] area;

    @Override
    public void run() {
        while(true) {
            try {
                executeThread();
            } catch(Exception e) {
                e.printStackTrace();
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }
    }

    protected void executeThread() {
        if(!ConfigManager.autoHypeTrain.getValue()) return;
        for (int i = 0; i < 5; i++) {
            if(findNextPath()) break;
        }
    }

    @Override
    public String getListenerName() {
        return "监听器_自动高速火车";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onTick);
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::onUnload);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private int lastItemIndex;

    public void onTick(Minecraft mc) {
        if(!ConfigManager.autoHypeTrain.getValue()) return;
        if(mc.player == null) return;
        if (isDrivingTrain) {
            if (current != null && mc.player.distanceToSqr(Vec3.atCenterOf(current)) < 25) {
                setPassed();
            }

            if (mc.player.getVehicle() == null) {
                isDrivingTrain = false;
                current = null;
                tail = null;
                path.clear();
                area = null;
                // if (Config管理.getConfigKeyValue("autolobbytrainplaysoundaftercrash") == 1) {
                if(ConfigManager.autoHypeTrainManbaOut.getValue()) {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §c坠机了 :("));
                    ToolList.getInstance().playSound(CustomSounds.MANBA_OUT);
                }
            }
        }

        if(mc.player.getInventory().getSelectedSlot() != lastItemIndex) {
            lastItemIndex = mc.player.getInventory().getSelectedSlot();
            ItemStack itemStack = mc.player.getMainHandItem();
            Item item = itemStack.getItem();
            if (item  == Items.RAIL && (itemStack.getDisplayName().getString().contains("Hype Train Gadget") || itemStack.getDisplayName().getString().contains("高速火车"))) {
                if(!tipped) {
                    tipped = true;
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] 你当前已开启自动驾驶高速火车, 请在空旷地面上抬头右键小道具以使功能正常工作"));
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §e请不要在大厅边缘使用小道具, 否则可能会出图导致坠毁!"));
                }
            }
        }


        if(isDrivingTrain) {
            if (current == null) {
                if (!path.isEmpty()) {
                    current = path.poll();
                } else {
                    return;
                }
            }

            AimHelper.getYawPitchByBlockPos(current).updateToAimHelper(aimHelper);
        }
    }

    private void onChat(Component component, boolean b) {
        if(!ConfigManager.autoHypeTrain.getValue()) return;
        if(isDrivingTrain) return;
        String removedColorMessage = ToolList.getInstance().deleteColorCode(component.getString());
        if (b) {
            if(removedColorMessage.equals("搭乘高速火车玩家人数：1") || removedColorMessage.equals("Players riding your Hype Train: 1")) {
                ToolList.printChatMessage(Component.literal("§a[小沙雕] 星穹列车, 启动!"));
                isDrivingTrain = true;
                initArea();
            }
        }
    }

    private void onUnload(Minecraft minecraft, ClientLevel level) {
        isDrivingTrain = false;
        current = null;
        tail = null;
        path.clear();
        area = null;
    }

    public void debugSetDrivingTrain(boolean b) {
        isDrivingTrain = b;
    }

    private void initArea() {
        area = BlockPos.betweenClosedStream(BlockPos.containing(mc.player.getX() + 40, mc.player.getY(), mc.player.getZ() + 40), BlockPos.containing(mc.player.getX() - 40, mc.player.getY() + 45, mc.player.getZ() - 40)).map(BlockPos::immutable).toArray(BlockPos[]::new);
    }

    public boolean findNextPath() {
        if (isDrivingTrain) {
            if(path.size() > 5) return true;
            BlockPos now = tail;

            if (now == null) {
                now = mc.player.blockPosition();
            }

            BlockPos target = area[ToolList.getInstance().random.nextInt(area.length)];

            if (BlockPos.betweenClosedStream(new BlockPos(target.getX() - 5, target.getY() - 5, target.getZ() - 5), new BlockPos(target.getX() + 5, target.getY() + 5, target.getZ() + 5)).anyMatch(pos -> !mc.level.getBlockState(pos).isAir())) return false;

            for (BlockPos mutableBlockPos : BlockPos.betweenClosed(new BlockPos(now.getX() - 2, now.getY() - 2, now.getZ() - 2), new BlockPos(now.getX() + 2, now.getY() + 2, now.getZ() + 2))) {
                for (int offset = 0; offset < 5; offset++) {
                    HitResult hitResult = ToolList.getInstance().predictPlayerAimBlock(new Vec3(target.getX() + 0.5, target.getY() + 0.5 - offset, target.getZ() + 0.5), new Vec3(mutableBlockPos.getX(), mutableBlockPos.getY(), mutableBlockPos.getZ()));
                    if (hitResult instanceof BlockHitResult && hitResult.getType() == HitResult.Type.BLOCK) {
                        return false;
                    }
                }
            }

            path.add(target);
            tail = target;
        }
        return true;
    }

    private final AimHelper aimHelper = new AimHelper(0.8);

    public void setPassed() {
        current = path.poll();
    }

    private void onLastRender(LevelRenderContext context) {
        if(isDrivingTrain) {
            List<BlockPos> positions = new ArrayList<>(path);
            if(current != null) positions.addFirst(current);
            PathRenderer.renderPath(context, positions, 0, 255, 0, false, false);

            boolean first = true;
            RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_LINE);

            for(BlockPos pos : positions) {
                RenderUtils.renderESP(wr, pos, first ? 1 : 0, 1, 0, 1, false);
                first = false;
            }
        }
    }

}
