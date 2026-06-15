package pers.XiaoShadiao.skydiao.eventbuslistener.macro;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.ObjectArrayPriorityQueue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.pathfinder.PathFinder;
import pers.XiaoShadiao.skydiao.utils.playerinput.AimHelper;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CarnivalFruitDigger extends AbstractListener implements IMacro, PathFinder.ICustomPathfinderConfig {

    public static final BlockPos corner1 = new BlockPos(-106, 72, 19);
    public static final BlockPos corner2 = new BlockPos(-112, 72, 25);

    public static final BlockPos[] startPos = {
            // -111/-107 72 24/20
            new BlockPos(-111, 72, 24),
            new BlockPos(-107, 72, 20),
            new BlockPos(-111, 72, 20),
            new BlockPos(-107, 72, 24),
    };
    public static final List<BlockPos> startPosList = Arrays.asList(startPos);

    public static final ObjectArrayPriorityQueue<SlotInfo> digQueue = new ObjectArrayPriorityQueue<>(49, Comparator.comparingInt(SlotInfo::getRealPriority).reversed());

    public final SlotInfo[][] slots = new SlotInfo[7][7];

    public static final String START_COMMAND = "/selectnpcoption carnival_pirateman r_2_1";

    private SlotInfo currentSlot;
    private boolean isSlotInited;
    private boolean isAlertTriggered;

    public static final Pattern bombsPattern = Pattern.compile("MINES! There are (\\d+) bombs hidden nearby.");

    public static final Vec3 npcPos = new Vec3(-107, 74, 28);

    @Override
    public String getListenerName() {
        return "CarnvialFruitDigger";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientStartTick);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::onUnload);
    }

    private void onUnload(Minecraft mc, ClientLevel level) {
        isSlotInited = false;
        currentSlot = null;
        isAlertTriggered = false;
    }

    public int tickToUpdate = 0;

    private void onClientStartTick(Minecraft mc) {
        if(!ConfigManager.carnivalAutoFruitDigger.getValue()) {
            isSlotInited = false;
            currentSlot = null;
            return;
        }

        if(mc.player == null || mc.level == null) {
            tickToUpdate = 0;
            return;
        }
        if(isAlertTriggered && MacroManagerListener.pathFinderExecutor.isRunning()) {
            MacroManagerListener.pathFinderExecutor.stopExecution();
            return;
        }
        if(isAlertTriggered) return;

        if(currentSlot != null) {
            if(currentSlot.isDigged()) {
                tickToUpdate++;
                if(tickToUpdate >= (mc.isSingleplayer() ? 20 * 10 : 10)) {
                    currentSlot = getNextSlot();
                    tickToUpdate = 0;
                }
            }
        } else {
            tickToUpdate = 0;
        }

        if(isHoldingShovel()) {
            if(isSlotInited && currentSlot == null) {
                activeThisMacro();
                currentSlot = getNextSlot();
            }
            if(isSlotInited) isSlotInited = false;
        }

        if(currentSlot != null) {
            if(!currentSlot.isDigged() && !MacroManagerListener.pathFinderExecutor.isRunning()) {
                PathFinder.registerConfig(this);
                MacroManagerListener.pathFinderExecutor.startExecution(new Vec3(currentSlot.pos), false);
            }
            if(currentSlot.isDigged() && MacroManagerListener.pathFinderExecutor.isRunning()) {
                MacroManagerListener.pathFinderExecutor.stopExecution();
            }
        }
    }

    private void onLastRender(LevelRenderContext context) {
        if(currentSlot != null && mc.level != null) {
            RenderUtils.WorldRender wr1 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_FILL);
            RenderUtils.WorldRender wr2 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_LINE);

            RenderUtils.renderESP(wr1, currentSlot.pos, 0 ,1, 0, 1, true);
            RenderUtils.renderESP(wr2, currentSlot.pos, 0 ,1, 0, 1, false);

//            for (int i = 0; i < 7; i++) {
//                for (int j = 0; j < 7; j++) {
//                    SlotInfo s = slots[i][j];
//                    if(s != null && s.isKnownBombCount) {
//                        RenderUtils.renderESP(wr1, s.pos, 1 ,1, Mth.clamp(s.bombs / 5f, 0f, 1f), 1, true);
//                    }
//                }
//            }

            wr1.finishDraw();
            wr2.finishDraw();
        }
    }

    private void onChat(Component component, boolean b) {
        String msg = ToolList.getInstance().deleteColorCode(component.getString());

        if(!ConfigManager.carnivalAutoFruitDigger.getValue()) return;
        if(msg.contains("You earned") && msg.contains("Carnival Tokens!")) {
            if(!isAlertTriggered) {
                ToolList.addThreadedTask(() -> {
                    Thread.sleep(100);
                    if(mc.player != null && mc.player.distanceToSqr(npcPos) > 64) return null;
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
        } else if ("[NPC] Carnival Pirateman: Would ye like to do some Fruit Digging?".equals(msg)) {
            ToolList.addThreadedTask(() -> {
                Thread.sleep(1000);
                ToolList.sendChatMessage(START_COMMAND);
                return null;
            });
        } else if ("[NPC] Carnival Pirateman: Here's yer shovel, then.".equals(msg)) {
            fillSlots();
            isSlotInited = true;
        } else if("debug next".equals(msg)) {
            tickToUpdate = 20 * 10;
        } else if(currentSlot != null) {
            if("MINES! There is 1 bomb hidden nearby.".equals(msg)) {
                setBombCount(currentSlot, 1);
            } else {
                Matcher matcher = bombsPattern.matcher(msg);
                if (matcher.find()) {
                    try {
                        int bombs = Integer.parseInt(matcher.group(1));
                        setBombCount(currentSlot, bombs);
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public boolean isHoldingShovel() {
        if(mc.player == null) return false;
        return "Carnival Shovel".equals(ToolList.getInstance().deleteColorCode(mc.player.getInventory().getSelectedItem().getHoverName().getString()));
    }

    private void fillSlots() {
        currentSlot = null;
        digQueue.clear();
        BlockPos.betweenClosedStream(corner1, corner2)
                .map(BlockPos::immutable)
                .sorted(Comparator.comparingInt(Vec3i::getX).thenComparing(Vec3i::getZ))
                .forEach(new Consumer<>() {
                    int i = 0;
                    public void accept(BlockPos pos) {
                        SlotInfo temp = new SlotInfo(pos);
                        slots[i / 7][i % 7] = temp;
                        digQueue.enqueue(temp);
                        i++;
                    }
                });
    }

    private SlotInfo getNextSlot() {
        calcPriorities();
        if(!digQueue.isEmpty()) {
            digQueue.changed();
            System.out.println("Get from queue");
            return digQueue.dequeue();
        } else {
            List<SlotInfo> list = getNonDiggedSlots();
            System.out.println("Get fron array");
            if(list.isEmpty()) return null; else return list.getFirst();
        }
    }

    private List<SlotInfo> getNonDiggedSlots() {
        return Arrays.stream(slots).flatMap(Arrays::stream).filter(s -> !s.isDigged()).toList();
    }

    private List<SlotInfo> getSlotsAround(SlotInfo slot) {
        List<SlotInfo> slots0 = new ArrayList<>();
        int[] slotIndex = getSlotIndex(slot);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if(i == 0 && j == 0) continue;
                int x = i + slotIndex[0];
                int y = j + slotIndex[1];
                if(x < 0 || x >= 7 || y < 0 || y >= 7) continue;
                slots0.add(slots[x][y]);
            }
        }
        return slots0;
    }

    // AI生成 (
    private void calcPriorities() {
        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
                SlotInfo slot = slots[i][j];
                
                // 如果已经挖掘过了，则优先级设为最低
                if (slot.isDigged()) {
                    slot.priority = Integer.MIN_VALUE;
                    continue;
                }
                
                int priority = 0;
                
                // 获取周围的格子
                List<SlotInfo> aroundSlots = getSlotsAround(slot);
                
                // 1. 检查是否可以确定安全：如果周围任意一个已知数字格子表明该格子安全
                boolean isDefinitelySafe = false;
                boolean isDefinitelyDangerous = false;
                
                for (SlotInfo aroundSlot : aroundSlots) {
                    // 如果周围有个已知数字的格子
                    if (aroundSlot.isKnownBombCount && aroundSlot.isDigged()) {
                        // 计算周围未挖掘格子的数量
                        List<SlotInfo> aroundAroundSlots = getSlotsAround(aroundSlot);
                        int hiddenAroundCount = 0;
                        for (SlotInfo aroundAroundSlot : aroundAroundSlots) {
                            if (!aroundAroundSlot.isDigged()) {
                                hiddenAroundCount++;
                            }
                        }
                        
                        // 如果周围格子显示雷数为0，那么它周围的所有未挖掘格子都是安全的
                        if (aroundSlot.bombs == 0 && aroundAroundSlots.contains(slot)) {
                            isDefinitelySafe = true;
                            break;
                        }
                        
                        // 如果周围格子显示雷数等于其周围未挖掘格子总数，
                        // 那么它周围所有未挖掘的格子都是雷
                        else if (aroundSlot.bombs == hiddenAroundCount && aroundAroundSlots.contains(slot)) {
                            isDefinitelyDangerous = true;
                            break;
                        }
                    }
                }
                
                // 根据确定性判断设置优先级
                if (isDefinitelySafe) {
                    priority = Integer.MAX_VALUE - 100000; // 极高优先级，但保留一个比它更高的值给特殊情况
                } else if (isDefinitelyDangerous) {
                    priority = Integer.MIN_VALUE + 1; // 极低优先级，但不是最低，因为已挖掘的是最低
                } else {
                    // 2. 对于不确定的格子，使用推断逻辑
                    
                    // 计算潜在威胁
                    int minPossibleBombs = 0; // 周围提示的最小雷数
                    int maxPossibleBombs = 0; // 周围提示的最大雷数
                    int safeClues = 0; // 提示安全的线索数
                    int dangerClues = 0; // 提示危险的线索数
                    
                    for (SlotInfo aroundSlot : aroundSlots) {
                        if (aroundSlot.isKnownBombCount && aroundSlot.isDigged()) {
                            // 检查这个格子对当前slot的潜在影响
                            List<SlotInfo> aroundAroundSlots = getSlotsAround(aroundSlot);
                            int hiddenAroundCount = 0;
                            for (SlotInfo aroundAroundSlot : aroundAroundSlots) {
                                if (!aroundAroundSlot.isDigged() && !aroundAroundSlot.pos.equals(slot.pos)) {
                                    hiddenAroundCount++;
                                }
                            }
                            
                            // 如果当前格子在aroundSlot的邻域内
                            if (aroundAroundSlots.contains(slot)) {
                                int otherHiddenCount = hiddenAroundCount; // aroundSlot周围除当前slot外的隐藏格子数
                                
                                // aroundSlot周围总共有aroundSlot.bombs个雷
                                // 如果otherHiddenCount为0且aroundSlot.bombs > 0，则当前slot必为雷
                                if (otherHiddenCount == 0 && aroundSlot.bombs > 0) {
                                    priority = Integer.MIN_VALUE + 1; // 确定是雷，极低优先级
                                    break;
                                }
                                // 如果aroundSlot.bombs为0，则当前slot安全
                                else if (aroundSlot.bombs == 0) {
                                    safeClues++; // 收集安全线索
                                }
                                // 如果aroundSlot.bombs <= otherHiddenCount + 1，
                                // 则当前slot可能安全
                                else if (aroundSlot.bombs <= otherHiddenCount) {
                                    safeClues++; // 这种情况下当前slot可能是安全的
                                } else {
                                    // aroundSlot.bombs > otherHiddenCount，当前slot必须是雷
                                    dangerClues++; // 收集危险线索
                                }
                            }
                        }
                    }
                    
                    if (priority != Integer.MIN_VALUE + 1) { // 如果不是已确定的危险
                        // 基于线索数量计算优先级
                        priority = safeClues * 500 - dangerClues * 1000;
                        
                        // 3. 一般启发式规则
                        
                        // 优先挖掘角落和边缘
                        if ((i == 0 && j == 0) || (i == 0 && j == 6) || (i == 6 && j == 0) || (i == 6 && j == 6)) {
                            // 角落格子
                            priority += 100;
                        } else if (i == 0 || i == 6 || j == 0 || j == 6) {
                            // 边缘格子
                            priority += 50;
                        }
                        
                        // 基于起始位置的特殊处理
                        if (startPosList.contains(slot.pos)) {
                            priority += 30;
                        }
                        
                        // 检查周围隐藏格子数量
                        int hiddenNeighbors = 0;
                        for (SlotInfo aroundSlot : aroundSlots) {
                            if (!aroundSlot.isDigged()) {
                                hiddenNeighbors++;
                            }
                        }
                        
                        // 增加周围隐藏格子较多的格子的优先级（这些可能更容易推理）
                        priority += hiddenNeighbors * 10;
                    }
                }
                
                // 设置最终优先级
                slot.priority = priority;
            }
        }
    }

    private int[] getSlotIndex(SlotInfo slot) {
        for (int i = 0; i < slots.length; i++) {
            for (int j = 0; j < slots[i].length; j++) {
                if(slots[i][j] == slot) {
                    return new int[] {i, j};
                }
            }
        }
        throw new AssertionError("Slot not found");
    }

    @Override
    public boolean isMacroActive() {
        return isHoldingShovel();
    }

    @Override
    public boolean onMacroCheck(PositionInfo beforeTP, PositionInfo afterTP) {
        boolean flag = afterTP.position().distanceToSqr(-105.50, 73.00, 30.50) < 0.01;
        if(!flag) {
            currentSlot = null;
            isSlotInited = false;
            MacroManagerListener.pathFinderExecutor.stopExecution();
            isAlertTriggered = true;
        }
        return flag;
    }

    @Override
    public void onMacroUnload() {
        currentSlot = null;
        isSlotInited = false;
        MacroManagerListener.pathFinderExecutor.stopExecution();
    }

    @Override
    public boolean onMacroCheck(int beforeSlot, int afterSlot) {
        return true;
    }

    @Override
    public String getMacroName() {
        return "CFD";
    }

    private void setBombCount(SlotInfo slot, int count) {
        slot.bombs = count;
        slot.isKnownBombCount = true;
    }

    @Override
    public boolean isAllowBreak() {
        return true;
    }

    @Override
    public boolean isAllowPlace() {
        return false;
    }

    @Override
    public boolean shouldUnregister() {
        return !isHoldingShovel();
    }

    @Override
    public int getDepth() {
        return 100;
    }

    @Override
    public long getTimeout() {
        return 50;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldStopWhenRecieveS08() {
        return false;
    }

    @Override
    public boolean minerChooseBetterTool() {
        return false;
    }

    public static class SlotInfo {

        public final BlockPos pos;
        public int bombs = 0;
        public int priority = 0;
        public boolean isKnownBombCount = false;

        public SlotInfo(BlockPos pos) {
            this.pos = pos;
        }

        public boolean isDigged() {
            if(mc.level == null) return true;
            return mc.level.getBlockState(pos).getBlock() != Blocks.SAND && (!ToolList.getInstance().isDevEnvironment() || mc.level.getBlockState(pos).getBlock() != Blocks.YELLOW_STAINED_GLASS);
        }

        public int getRealPriority() {
            return priority + (startPosList.contains(pos) ? 1000 : 0);
        }

        public String toString() {
            return pos + " " + priority;
        }

    }

}