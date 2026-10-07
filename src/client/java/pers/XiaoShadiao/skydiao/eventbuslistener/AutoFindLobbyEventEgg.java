package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.pathfinderv2.BlockHelper;
import pers.XiaoShadiao.skydiao.utils.playerinput.AimHelper;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class AutoFindLobbyEventEgg extends AbstractListener {

    private final Map<String, List<BlockPos>> points = new HashMap<>();
    private boolean inited = false;

    private boolean start = true;
    private List<BlockPos> remainPoints = new ArrayList<>();
    private long flagTime = 0;
    private boolean tipped = false;

    @Override
    public String getListenerName() {
        return "AutoFindLobbyEventEgg";
    }

    @Override
    public void registerListeners() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((_, _) -> {
            tipped = false;
            init();
        });
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);

        init();
    }

    private void onStartTick(Minecraft mc) {

        if(StatusManager.get().isInLobby() && points.containsKey(StatusManager.get().getType())) {
            if(!tipped) {
                tipped = true;
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §e当前大厅存在可以寻找的彩蛋, 可以使用/skydiao autofindeventegg start开始一键寻找"));
            }
        }

        if(!start || mc.player == null || mc.level == null) {
            start = false;
            remainPoints.clear();
            return;
        }

        if(remainPoints.isEmpty()) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c当前大厅不存在需要寻找的彩蛋。"));
            stopFind();
            return;
        }

        remainPoints.sort(Comparator.comparingDouble(pos -> mc.player.blockPosition().distSqr(pos)));

        BlockPos target = remainPoints.getFirst();
        if(!MacroManagerListener.pathFinderV2Executor.isRunning()) {
            if(mc.player.blockPosition().distManhattan(target) > 3) {
                if (BlockHelper.hasCollision(target)) {
                    Optional<BlockPos> first = BlockPos.betweenClosedStream(target.offset(-1, 0, -1), target.offset(1, 1, 1))
                            .map(BlockPos::immutable)
                            .filter(pos -> !BlockHelper.hasCollision(pos) && !BlockHelper.hasCollision(pos.above()))
                            .findFirst();
                    if(first.isPresent()) {
                        target = first.get();
                    }
                }
                if (BlockHelper.hasCollision(target.above())) {
                    Optional<BlockPos> first = BlockPos.betweenClosedStream(target.offset(-1, -1, -1), target.offset(1, 0, 1))
                            .map(BlockPos::immutable)
                            .filter(pos -> !BlockHelper.hasCollision(pos) && !BlockHelper.hasCollision(pos.above()))
                            .findFirst();
                    if(first.isPresent()) {
                        target = first.get();
                    }
                }
                MacroManagerListener.pathFinderV2Executor.startExecution(target);
            } else {
                out:for(int i : new int[] {1, 2}) {
                    for (BlockPos pos : BlockPos.betweenClosed(target.offset(-i, -i, -i), target.offset(i, i, i))) {
                        if (mc.level.getBlockState(pos).is(Blocks.PLAYER_HEAD)) {
                            AimHelper.getYawPitchByVec3(pos.getBottomCenter().add(0, pos.getY() > mc.player.getY() ? 0 : -0.3, 0)).updateToAimHelper(new AimHelper());

                            InputSimulator.tryNoViewChangeControlMoveTo(pos, 0.5);
                            if (mc.hitResult instanceof BlockHitResult blockHitResult
                                    && blockHitResult.getBlockPos().equals(pos)) {
                                InputSimulator.pressRightClick();
                            }
                            break out;
                        }
                    }
                }
                if(System.currentTimeMillis() - flagTime > 2000) {
                    remainPoints.removeFirst();
                    flagTime = System.currentTimeMillis();
                    if(!remainPoints.isEmpty()) {
                        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e下一个位置: " + remainPoints.getFirst()));
                        XSDHUD.bigTitle.updateTitleMsg("§e下一个位置: " + remainPoints.getFirst(), 5000);
                    }
                }
            }
        } else {
            flagTime = System.currentTimeMillis();
        }
    }

    public void startFind() {
        flagTime = System.currentTimeMillis();
        if(!StatusManager.get().isInLobby()) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c请在大厅中使用该彩蛋寻找功能!"));
            return;
        }

        if(!mc.player.getMainHandItem().isEmpty()) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c手里不能拿东西, 请切换为空手。"));
            return;
        }

        List<BlockPos> blockPos = points.get(StatusManager.get().getType());
        if(blockPos == null || blockPos.isEmpty()) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c当前大厅不存在需要寻找的彩蛋。"));
            return;
        }
        start = true;
        remainPoints = new ArrayList<>(blockPos);
    }

    public void stopFind() {
        start = false;
        remainPoints.clear();
        MacroManagerListener.pathFinderV2Executor.stopExecution(true);
        InputSimulator.releaseAllKey();
    }

    private void init() {
        if (inited) return;

        ToolList.addThreadedTask(() -> {
            synchronized (this) {
                if (inited) return;

                try(InputStream is = ToolList.getInstance().makeReqToURL("https://xiaoshadiao.club/hypixel_event_poses.json")) {
                    JsonObject jsonObject = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();

                    String currentEvent = jsonObject.get("currentEvent").getAsString();

                    if(!currentEvent.isEmpty()) {
                        JsonObject eventPoses = jsonObject.getAsJsonObject("events").getAsJsonObject(currentEvent);

                        List<BlockPos> poses = new ArrayList<>();
                        for (Map.Entry<String, JsonElement> entry : eventPoses.entrySet()) {
                            JsonArray posArray = entry.getValue().getAsJsonArray();
                            for (JsonElement pos : posArray) {
                                int x = pos.getAsJsonArray().get(0).getAsInt();
                                int y = pos.getAsJsonArray().get(1).getAsInt();
                                int z = pos.getAsJsonArray().get(2).getAsInt();
                                poses.add(new BlockPos(x, y, z));
                            }
                            points.put(entry.getKey(), poses);
                        }
                    } else {
                        points.clear();
                    }

                    inited = true;
                } catch (Exception e) {
                    logger.catching(e);
                }

            }
        }, null);
    }

}
