package pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.IMacro;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming.op.IOperation;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming.op.OperationSendCommand;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.keybinds.KeyBindsManager;
import pers.XiaoShadiao.skydiao.utils.PageSwitchCallback;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;
import pers.XiaoShadiao.skydiao.utils.tab.TabReader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class EasyFarmingScriptListener extends AbstractListener implements IMacro {

    private static final File nodeConfigFile = ConfigManager.getCustomConfigFileName("farmingNode.json");

    private static String notEditing() {
        return "§a[小沙雕] §c你没有正在编辑的节点! 使用§e/skydiaofarmingscript editnearestnode§c来选择你最近的一个节点来编辑!";
    }

    private static String tryStartInEditing() {
        return "§a[小沙雕] §c请先使用§e/skydiaofarmingscript exitedit§c来退出节点编辑模式!";
    }

    public void addNode(ExecuteNode node) {
        if (!isInGarden()) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c你需要在Garden才可以编辑节点!"));
            return;
        }

        ToolList.printChatMessage(Component.literal("§a[小沙雕] 已成功添加新节点" + node.pos + "! 若不给该节点添加操作, 则视为结束节点, 脚本会发出提醒并自动停止. 使用§e/skydiaofarmingscript addoperation§a来添加节点操作"));
        executeNodes.add(node);
        save();
    }

    public ExecuteNode getCurrentWorking() {
        return currentWorking;
    }

    public ExecuteNode getCurrentEditing() {
        return currentEditing;
    }

    public void removeCurrentEditingNode() {
        if (currentEditing == null) {
            ToolList.printChatMessage(Component.literal(notEditing()));
            return;
        }
        executeNodes.remove(currentEditing);
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §c已删除当前编辑的节点"));
        editNearestNode();
        save();
    }

    private List<ExecuteNode> executeNodes = new ArrayList<>();

    private ExecuteNode currentWorking;
    private ExecuteNode currentEditing;
    private boolean ended;

    private long lastSendCommandTime;
    private long lastGetCheckedTime;

    private int currentHandItemIndex;

    private boolean enabled;

    private boolean renderNodes = true;

    private BlockPos groupStartPos = null;
    private BlockPos groupEndPos = null;

    public long getNodeExecMinDelay() {
        return nodeExecMinDelay;
    }

    public long getNodeExecMaxDelay() {
        return nodeExecMaxDelay;
    }

    private long nodeExecMinDelay = 100;
    private long nodeExecMaxDelay = 200;

    private int aimBlockTick = 0;
    private int receiveFarmingExpMsgTick;
    private int invalidMoveTick;

    @Override
    public String getListenerName() {
        return "EasyFarmingScriptListener";
    }

    @Override
    public void registerListeners() {
        load();
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::onUnload);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
        CustomFabricEvents.MOUSE_BUTTON_EVENT.register(this::onMouseButton);
        ScreenEvents.AFTER_INIT.register(this::onScreenAfterInit);
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
    }

    private boolean onPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        // 由于Skber SH SBA三巨头将物品栏上方信息变成了滚木, 所以需要抓包才能获取
        if(packet instanceof ClientboundSystemChatPacket(Component content, boolean overlay)) {
            if (overlay && ToolList.getInstance().deleteColorCode(content.getString()).contains("Farming")) {
                receiveFarmingExpMsgTick = 0;
            }
        }
        return false;
    }

    private boolean onMouseButton(long windwos, MouseButtonInfo mouseButtonInfo, int state) {
        if (mouseButtonInfo.button() == 0 && isHoldingMouseMat()) {
            lastSendCommandTime = System.currentTimeMillis();
        }
        return false;
    }

    private void onLastRender(LevelRenderContext context) {
        if (!isInGarden() || (!renderNodes && currentEditing == null && groupStartPos == null && groupEndPos == null))
            return;
        RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        RenderUtils.WorldRender wr2 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_FILL);
        for (ExecuteNode node : executeNodes) {
            if (node == currentEditing) {
                RenderUtils.renderESP(wr, node.pos, 0, 1, 0, 1, false);
                RenderUtils.renderTrace(wr, node.pos, 0, 1, 0, 1);
            } else if (groupStartPos != null && groupEndPos != null && isNodeInRange(node, getBlockRange(groupStartPos, groupEndPos))) {
                RenderUtils.renderESP(wr, node.pos, 0, 1, 1, 1, false);
            } else if (node.isTemp) {
                RenderUtils.renderESP(wr, node.pos, 1, 0, 0.5f, 1, false);
            } else if (node == currentWorking) {
                RenderUtils.renderESP(wr, node.pos, 1, 0, 0, 1, false);
            } else {
                RenderUtils.renderESP(wr, node.pos, 1, 1, 0, 1, false);
            }
        }

        if (groupStartPos != null && groupEndPos == null || groupStartPos == null && groupEndPos != null) {
            RenderUtils.renderESP(wr, Objects.requireNonNullElseGet(groupStartPos, () -> groupEndPos), 0, 1, 1, 1, false);
        } else if (groupStartPos != null) {
            RenderUtils.renderESP(wr, groupStartPos, 1, 1, 1, 1, false);
            RenderUtils.renderESP(wr2, groupStartPos, 1, 1, 1, 1, true);

            RenderUtils.renderESP(
                    wr,
                    Math.min(groupStartPos.getX(), groupEndPos.getX()),
                    Math.min(groupStartPos.getY(), groupEndPos.getY()),
                    Math.min(groupStartPos.getZ(), groupEndPos.getZ()),
                    Math.max(groupStartPos.getX(), groupEndPos.getX()) + 1,
                    Math.max(groupStartPos.getY(), groupEndPos.getY()) + 1,
                    Math.max(groupStartPos.getZ(), groupEndPos.getZ()) + 1,
                    1, 1, 1, 1, false
            );

        }
        wr2.finishDraw();
        wr.finishDraw();
    }

    private void onUnload(Minecraft mc, ClientLevel level) {
        currentWorking = currentEditing = null;
        groupStartPos = null;
        groupEndPos = null;
        enabled = false;
        if (isInGarden()) save();
    }

    private boolean isHoldingMouseMat() {
        return mc.player != null && ToolList.getInstance().deleteColorCode(mc.player.getInventory().getSelectedItem().getHoverName().getString()).equals("Squeaky Mousemat");
    }

    private void startCurrentActions() {
        ToolList.addThreadedTask(() -> {
            Thread.sleep(ToolList.getInstance().random.nextLong(nodeExecMaxDelay - nodeExecMinDelay) + nodeExecMinDelay);
            InputSimulator.unpressAllKey();
            if (enabled) {
                for (IOperation<?> op : currentWorking.ops) {
                    op.op();
                    if (op instanceof OperationSendCommand) {
                        lastSendCommandTime = System.currentTimeMillis();
                    }
                }
            } else {
                InputSimulator.unpressAllKey();
            }
            return null;
        });
    }

    private void onStartClientTick(Minecraft mc) {
        if (mc.player == null) return;

        if (mc.options.keyUse.isDown() && isHoldingMouseMat()) {
            lastSendCommandTime = System.currentTimeMillis();
        }
        if (mc.hitResult instanceof BlockHitResult blockHitResult && blockHitResult.getType() == HitResult.Type.BLOCK) {
            aimBlockTick = 5;
        }

        boolean toggled = false;
        if (!SleepActions.actionDoing && enabled && currentHandItemIndex != mc.player.getInventory().getSelectedSlot() && currentWorking != null) {
            ended = true;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e手上物品发生变化, 已自动停止脚本"));
        }
        while (KeyBindsManager.toggleFarmingScript.consumeClick() || (ended && enabled)) {
            enabled = !enabled;
            ended = false;
            toggled = true;
            getAutoFarmingConfig();
        }
        if (enabled && currentEditing != null) {
            enabled = false;
            ToolList.printChatMessage(Component.literal(tryStartInEditing()));
        } else if (!isInGarden() && enabled && toggled) {
            enabled = false;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c请在Garden开启农业脚本!"));
        } else if (System.currentTimeMillis() - lastGetCheckedTime < 2000 && enabled && toggled) {
            enabled = false;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c检测到刚才疑似被Check, 请稍等片刻再启用!"));
        }
        if (toggled) {
            if (enabled) {
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §e农业脚本已§a开启§e, 前往任意一个节点以开始执行"));
            } else {
                if (currentWorking != null) {
                    ExecuteNode tempNode0 = currentWorking.clone();
                    tempNode0.isTemp = true;
                    tempNode0.pos = BlockPos.containing(mc.player.position());
                    executeNodes.add(tempNode0);
                    for (int i = -5; i < 6; i++) {
                        if (i == 0) continue;
                        tempNode0 = currentWorking.clone();
                        tempNode0.isTemp = true;
                        tempNode0.pos = BlockPos.containing(mc.player.position()).offset(i, 0, 0);
                        executeNodes.add(tempNode0);
                        tempNode0 = currentWorking.clone();
                        tempNode0.isTemp = true;
                        tempNode0.pos = BlockPos.containing(mc.player.position()).offset(0, 0, i);
                        executeNodes.add(tempNode0);
                    }
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §e农业脚本已§c关闭§e, 已在你停止的位置创建了临时节点, 继承上个执行节点的操作, 重新开启脚本后可靠近该临时节点继续执行"));
                } else {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §e农业脚本已§c关闭"));
                }
                currentWorking = null;
                InputSimulator.unpressAllKey();
            }
        }


        if (enabled) {
            if(!SleepActions.actionDoing && currentWorking != null) {
                if (aimBlockTick > 0) {
                    aimBlockTick--;
                    receiveFarmingExpMsgTick++;
                    if (receiveFarmingExpMsgTick > 40) {
                        MacroManagerListener.mml.triggerAlert("准星瞄准了方块, 但未获得Farming XP!");
                        receiveFarmingExpMsgTick = -40;
                    }
                } else {
                    receiveFarmingExpMsgTick = Math.min(receiveFarmingExpMsgTick, 0);
                }
            }
            if(receiveFarmingExpMsgTick < 0) receiveFarmingExpMsgTick++;
            getNearestNode().ifPresent(node -> {
                if (BlockPos.containing(mc.player.position()).equals(node.pos)) {
                    if (currentWorking != node) {
                        currentWorking = node;
                        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e到达节点" + node.pos + ", 执行节点操作" + node.ops.stream().map(IOperation::toChatString).toList()));
                        activeThisMacro();
                        receiveFarmingExpMsgTick = -40;
                        invalidMoveTick = -20;
                        currentHandItemIndex = mc.player.getInventory().getSelectedSlot();
                        if (node.ops.isEmpty()) {
                            InputSimulator.unpressAllKey();
                            XSDHUD.bigTitle.updateTitleMsg("§e已到达节点尽头!", 5000, SoundEvents.WITHER_SPAWN);
                            ended = true;
                        } else {
                            startCurrentActions();
                        }
                        executeNodes.removeIf(n -> n.isTemp);
                    }
                }
            });
            if(!SleepActions.actionDoing && currentWorking != null) {
                if(InputSimulator.backward || InputSimulator.forward || InputSimulator.left || InputSimulator.right) {
                    Vec3 movement = mc.player.getDeltaMovement();
                    // if(ToolList.getInstance().random.nextInt(30) == 15) ToolList.printChatMessage(Component.literal("§a[小沙雕] §e[DEBUG] " + movement));
                    if(Math.abs(movement.x()) < 0.02 && Math.abs(movement.z()) < 0.02 && mc.player.onGround()) {
                        invalidMoveTick++;
                        if(invalidMoveTick > 10) {
                            invalidMoveTick = -20;
                            MacroManagerListener.mml.triggerAlert("任意方向键已按下, 但是玩家没有移动!");
                        }
                    } else {
                        invalidMoveTick = 0;
                    }
                }
            }
            autoActions();
            bonusListener();
        }
    }

    private Map<String, Integer> farmingToolIndex = null;
    // private ArrayList<Integer> autoPestsConfig = null;
    private ArrayList<Integer> autoLoadoutConfig = null;
    private boolean autoSprayonatorConfig = false;

    private void getAutoFarmingConfig() {
        farmingToolIndex = FarmingUtils.getFarmingToolIndex();
//        autoPestsConfig = FarmingUtils.strConfToIntArr(ConfigManager.autoKillPests.getValue(),
//                "自动杀害虫", 3);
        autoLoadoutConfig = FarmingUtils.strConfToIntArr(ConfigManager.autoChangeLo.getValue(),
                "自动切换装备", 4);
        autoSprayonatorConfig = ConfigManager.autoSprayonator.getValue();
    }

    private void autoActions() {
        autoKillPest();
        autoChangeLoadout();
        autoSpray();
    }

    private void autoKillPest() {
        if (/*autoPestsConfig == null || */!ConfigManager.halfAutoKillPests.getValue() || SleepActions.actionDoing || !FarmingUtils.hasPests()) return;
//        int vacuum = farmingToolIndex.getOrDefault("vacuum", -1);
//        if (vacuum == -1) return;
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §b害虫已生成, 正在等待人工杀虫..."));
        XSDHUD.bigTitle.updateTitleMsg("§e害虫已生成, 正在进行预操作...", 6000, SoundEvents.WITHER_SPAWN);

        SleepActions kpest = SleepActions.builder()
                .addSleep(1000)
                .addAction(InputSimulator::unpressAllKey, 300);

        if (!FarmingUtils.withPetType("kpest") && autoLoadoutConfig != null)
            kpest.addAction(() -> FarmingUtils.changeLoadout(autoLoadoutConfig.get(2)), 500);

        if (ConfigManager.fsGardenMoonFlowerMode.getValue()) {
            executeDayNightSwitch(kpest, false);
        }


        kpest.addAction(InputSimulator::unpressAllKey)
                .addAction(ctx -> {
                    ctx.put("lastSelectedSlot", mc.player.getInventory().getSelectedSlot());
                    // InputSimulator.switchItem(vacuum);
                }, 300, true)
                .addAction(() -> ToolList.sendChatMessage("/setspawn"), 200);

        if(ConfigManager.fsTPToPest.getValue()) kpest.addAction(ctx -> {
                    List<String> pestPlots = FarmingUtils.getPestPlots();
                    if(!pestPlots.isEmpty()) {
                        String plot = pestPlots.getFirst();

                        if(ToolList.getInstance().fetchScoreboardLinesNoColor().stream().noneMatch(line -> line.endsWith("Plot - " + plot) || line.contains("Plot - " + plot + " "))) {
                            ToolList.sendChatMessage("/tptoplot " + plot);
                            ctx.put("executedTpToSlot", Boolean.TRUE);
                        } else {
                            ctx.put("executedTpToSlot", Boolean.FALSE);
                        }
                    }
                }, 200);
//                .addAction(FarmingUtils::tpToPestPlot, 1000, true)
//                .addAction(InputSimulator::pressRightClick, 300);
//
//        for (int i = 0; i < autoPestsConfig.get(0); i++)
//            kpest.addAction(() -> InputSimulator.setForward(true), autoPestsConfig.get(1))
//                    .addAction(() -> InputSimulator.setForward(false), autoPestsConfig.get(2));
//
//        kpest.addAction(InputSimulator::unpressAllKey, 200)
//                .addAction(ctx -> InputSimulator.switchItem((int) ctx.get("lastSelectedSlot")), 500, true)

        kpest.addAction(ctx -> {
                    if((Boolean) ctx.get("executedTpToSlot")) {
                        XSDHUD.bigTitle.updateTitleMsg("§b预操作完成, 请手动杀虫!", 6000, SoundEvents.WITHER_SPAWN);
                    } else {
                        XSDHUD.bigTitle.updateTitleMsg("§b预操作完成, 请手动杀虫 | 害虫位于当前Plot, 不进行传送", 6000, SoundEvents.WITHER_SPAWN);
                    }
                })
                .addAction(() -> {
                    while (FarmingUtils.hasPests()) {
                        try {
                            SleepActions.flagAntiMacro();
                            Thread.sleep(100);
                        } catch (InterruptedException _) {}
                    }
                }, 0, true)
                .addAction(() -> XSDHUD.bigTitle.updateTitleMsg("§e即将返回原来的点位...", 4000), 500, true)

                .addAction(ctx -> InputSimulator.switchItem((int) ctx.get("lastSelectedSlot")), 300)
                .addAction(() -> ToolList.sendChatMessage("/warp garden"), 500, true);


        kpest.addAction(() -> {
            try {
                if(!mc.player.onGround()) {
                    while(!mc.player.onGround()) {
                        InputSimulator.setShift(true);
                        Thread.sleep(200);
                    }
                    InputSimulator.setShift(false);
                } else {
                    Thread.sleep(200);
                }
            } catch (InterruptedException _) {}
        }, 200);

        if (ConfigManager.fsGardenMoonFlowerMode.getValue() && ConfigManager.fsGardenMoonFlowerModeKeepNightFarming.getValue()) {
            executeDayNightSwitch(kpest, true);
        }

        kpest.addAction(this::startCurrentActions, 2000)
                .run();
    }

    private void executeDayNightSwitch(SleepActions sleepActions, boolean night) {
        sleepActions.addAction(ctx -> {
                    this.pageSwitchCallback = new PageSwitchCallback();
                    ToolList.sendChatMessage("/desk");
                    ctx.put("screen", this.pageSwitchCallback.getScreen());
                }, 600)
                .addAction(ctx -> {
                    Screen screen = (Screen) ctx.get("screen");
                    if (!(screen instanceof ContainerScreen)) return;
                    ChestMenu menu = ((ContainerScreen) screen).getMenu();
                    Container container = menu.getContainer();
                    if (!(container instanceof SimpleContainer)) return;
                    if (mc.gameMode != null && mc.player != null) {
                        mc.execute(() -> mc.gameMode.handleContainerInput(menu.containerId, 50, 0, ContainerInput.PICKUP, mc.player));
                        this.pageSwitchCallback = new PageSwitchCallback();
                        ctx.put("screen", this.pageSwitchCallback.getScreen());
                    }
                }, 600)
                .addAction(ctx -> {
                    Screen screen = (Screen) ctx.get("screen");
                    if (!(screen instanceof ContainerScreen)) return;
                    ChestMenu menu = ((ContainerScreen) screen).getMenu();
                    Container container = menu.getContainer();
                    if (!(container instanceof SimpleContainer)) return;
                    if (mc.gameMode != null && mc.player != null) {
                        mc.execute(() -> mc.gameMode.handleContainerInput(menu.containerId, night ? 13 : 11, 0, ContainerInput.PICKUP, mc.player));
                    }
                }, 500)
                .addAction(() -> {
                    if (mc.screen != null) mc.screen.onClose();
                }, 600);
    }

    private void autoChangeLoadout() {
        if (autoLoadoutConfig == null || SleepActions.actionDoing) return;

        int target = -1;
        boolean hasPests = FarmingUtils.hasPests();
        FarmingUtils.ColdDown colddownReady = FarmingUtils.cooldownReady(autoLoadoutConfig.get(3));
        if (colddownReady.ready() && !FarmingUtils.withPetType("pest"))
            target = 1;
        else if (hasPests && !FarmingUtils.withPetType("kpest"))
            target = 2;
        else if (!colddownReady.ready() && !hasPests && !FarmingUtils.withPetType("farm"))
            target = 0;
        if (target == -1) return;

        ToolList.printChatMessage(Component.literal("§a[小沙雕] §b当前害虫CD " + colddownReady.tabTime() + "s小于设置值" + colddownReady.configTime() + "s, 准备自动切换装备"));
        final int targetIndex = target;
        SleepActions actions = SleepActions.builder()
                .addSleep(1000)
                .addAction(InputSimulator::unpressAllKey, 300)
                .addAction(() -> FarmingUtils.changeLoadout(autoLoadoutConfig.get(targetIndex)), 500);

        if (ConfigManager.fsGardenMoonFlowerMode.getValue() && !ConfigManager.fsGardenMoonFlowerModeKeepNightFarming.getValue()) {
            executeDayNightSwitch(actions, true);
        }

        actions.addAction(this::startCurrentActions, 2000)
                .run();
    }

    private void autoSpray() {
        if (!autoSprayonatorConfig || SleepActions.actionDoing ||
                TabReader.findLineWith("Spray: None").isEmpty()) return;
        Integer spray = farmingToolIndex.getOrDefault("sprayonator", -1);
        if (spray == -1) return;
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §b准备自动喷洒药剂"));

        SleepActions.builder()
                .addSleep(1000)
                .addAction(InputSimulator::unpressAllKey, 300)
                .addAction(ctx -> {
                    ctx.put("lastSelectedSlot", mc.player.getInventory().getSelectedSlot());
                    InputSimulator.switchItem(spray);
                }, 300, true)
                .addAction(InputSimulator::pressRightClick, 100)
                .addAction(InputSimulator::unpressAllKey, 300)
                .addAction(ctx -> InputSimulator.switchItem((int) ctx.get("lastSelectedSlot")), 300, true)
                .addAction(this::startCurrentActions, 5000)
                .run();
    }

    private long lastShowTime = System.currentTimeMillis();

    private void bonusListener() {
        if (!ConfigManager.gardenBonusPrompt.getValue()) return;
        long now = System.currentTimeMillis();
        if (now - lastShowTime < 11000) return;
        String bonus = TabReader.findLineStartsWith("Bonus:").orElse(null);
        lastShowTime = now;
        if ("Bonus: INACTIVE".equals(bonus))
            XSDHUD.bigTitle.updateTitleMsg("Bonus过期了", 5000, SoundEvents.WITHER_SPAWN);
    }

    @Override
    public boolean isMacroActive() {
        return currentWorking != null;
    }

    @Override
    public boolean onMacroCheck(PositionInfo beforeTP, PositionInfo afterTP) {
        if (SleepActions.antiMarco() || System.currentTimeMillis() - lastSendCommandTime < 5000) return true;
        if (System.currentTimeMillis() - lastSendCommandTime < 5000) return true;
        ToolList.addThreadedTask(() -> {
            Thread.sleep(1500);
            ended = true;
            lastGetCheckedTime = System.currentTimeMillis();
            return null;
        });
        return false;
    }

    @Override
    public boolean onMacroCheck(int beforeSlot, int afterSlot) {
        if (SleepActions.antiMarco()) return true;
        ToolList.addThreadedTask(() -> {
            Thread.sleep(1500);
            ended = true;
            lastGetCheckedTime = System.currentTimeMillis();
            return null;
        });
        return false;
    }

    @Override
    public String getMacroName() {
        return "Farming Script";
    }

    public void editNearestNode() {
        getNearestNode().ifPresentOrElse(this::setCurrentEditing, () -> setCurrentEditing(null));
    }

    public @NotNull Optional<ExecuteNode> getNearestNode() {
        if (mc.player == null) return Optional.empty();
        return executeNodes.stream().min(Comparator.comparingDouble(node -> mc.player.distanceToSqr(node.pos.getX() + 0.5, node.pos.getY() + 0.5, node.pos.getZ() + 0.5)));
    }

    public void setCurrentEditing(ExecuteNode node) {
        if (!isInGarden()) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c你需要在Garden才可以编辑节点!"));
            return;
        }
        if (node == null) {
            groupStartPos = null;
            groupEndPos = null;
            save();
        }
        currentEditing = node;
        ToolList.printChatMessage(Component.literal(node == null ? "§a[小沙雕] 已退出编辑节点模式, 按下按键§e" + KeyBindsManager.toggleFarmingScript.getTranslatedKeyMessage().getString() + "§a可切换脚本开关 (可在控制设置里设置快捷键)" : "§a[小沙雕] §e当前正在编辑的节点已标记为§a绿色"));
    }

    public void addOperationToCurrentEditing(IOperation<?> op) {
        if (currentEditing == null) {
            ToolList.printChatMessage(Component.literal(notEditing()));
            return;
        }
        currentEditing.ops.add((IOperation<JsonElement>) op);
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e已添加操作" + op.toChatString() + "到当前节点"));
        save();
        listOperation();
    }

    public void removeOperationFromCurrentNode(int index) {
        if (currentEditing == null) {
            ToolList.printChatMessage(Component.literal(notEditing()));
            return;
        }
        if (index < 0 || index >= currentEditing.ops.size()) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c操作序号无效, 请输入§e/skydiaofarmingscript listoperation§c查看当前节点操作序号"));
            listOperation();
            return;
        }
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e已从当前节点删除操作" + currentEditing.ops.remove(index).toChatString()));
        save();
        listOperation();
    }

    public void cloneCurrentEditing() {
        if (currentEditing == null || mc.player == null) {
            ToolList.printChatMessage(Component.literal(notEditing()));
            return;
        }

        ExecuteNode clone = currentEditing.clone();
        clone.pos = BlockPos.containing(mc.player.position());
        ToolList.printChatMessage(Component.literal("§a[小沙雕] 已克隆当前编辑节点到你所在位置" + clone.pos + "!"));
        executeNodes.add(clone);
        setCurrentEditing(clone);
        save();
    }

    public void listOperation() {
        if (currentEditing == null) {
            ToolList.printChatMessage(Component.literal(notEditing()));
            return;
        }
        ToolList.printChatMessage(Component.literal("§a[小沙雕] 当前节点有" + currentEditing.ops.size() + "个操作"));
        int i = 0;
        for (IOperation<?> op : currentEditing.ops) {
            i++;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §f" + i + " - §e" + op.toChatString()));
        }
    }

    public void groupStart() {
        if (mc.player == null) return;
        BlockPos position = BlockPos.containing(mc.player.position());
        ToolList.printChatMessage(Component.literal(
                "§a[小沙雕] 标记" + position + "为选区开始坐标"));
        this.groupStartPos = position;
    }

    public void groupEnd() {
        if (mc.player == null) return;
        BlockPos position = BlockPos.containing(mc.player.position());
        ToolList.printChatMessage(Component.literal(
                "§a[小沙雕] 标记" + position + "为选区结束坐标"));
        this.groupEndPos = position;
    }

    private boolean checkGroupPosValid() {
        if (groupStartPos == null || groupEndPos == null) {
            ToolList.printChatMessage(Component.literal(
                    "§a[小沙雕] §c未指定" + (groupStartPos == null ? "开始" : "终止") + "坐标"));
            return false;
        }
        return true;
    }

    private record BlockRange(int minX, int minY, int minZ,
                              int maxX, int maxY, int maxZ) {
    }

    private BlockRange getBlockRange(BlockPos a, BlockPos b) {
        return new BlockRange(
                Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ())
        );
    }

    private boolean isNodeInRange(ExecuteNode node, BlockRange range) {
        return node.pos.getX() >= range.minX && node.pos.getX() <= range.maxX
                && node.pos.getY() >= range.minY && node.pos.getY() <= range.maxY
                && node.pos.getZ() >= range.minZ && node.pos.getZ() <= range.maxZ;
    }

    public void groupDelete() {
        if (!this.checkGroupPosValid()) return;
        BlockRange blockRange = getBlockRange(this.groupStartPos, this.groupEndPos);

        int nodeCountOld = this.executeNodes.size();
        this.executeNodes.removeIf(node -> isNodeInRange(node, blockRange) && !node.isTemp);
        ToolList.printChatMessage(Component.literal(
                "§a[小沙雕] 移除共" + (nodeCountOld - this.executeNodes.size()) + "个节点"));
        save();
    }

    public void groupClone() {
        if (!this.checkGroupPosValid()) return;
        BlockRange blockRange = getBlockRange(this.groupStartPos, this.groupEndPos);

        if (mc.player == null) return;
        BlockPos pos = BlockPos.containing(mc.player.position());

        if (ToolList.getInstance().isXiaoShadiao()) {
            ToolList.printChatMessage(Component.literal("======="));
            for (ExecuteNode node : this.executeNodes) {
                ToolList.printChatMessage(Component.literal(node.pos.toString()));
            }
            ToolList.printChatMessage(Component.literal("======="));
        }

        List<ExecuteNode> tempContainer = new ArrayList<>();
        for (ExecuteNode node : this.executeNodes) {
            if (node.isTemp || !isNodeInRange(node, blockRange)) continue;
            BlockPos subtract = node.pos.subtract(this.groupStartPos);

            ExecuteNode newNode = node.clone();
            newNode.pos = pos.offset(subtract);
            tempContainer.add(newNode);
        }
        this.executeNodes.addAll(tempContainer);

        ToolList.printChatMessage(Component.literal(
                "§a[小沙雕] 成功从选区开始处复制共" + tempContainer.size() + "个节点"));
        save();
    }

    public void load() {
        try {
            JsonObject jo5 = JsonParser.parseString(FileUtils.readFileToString(nodeConfigFile, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray ops = jo5.getAsJsonArray("ops");
            List<ExecuteNode> temp = new ArrayList<>();
            for (JsonElement op : ops) {
                JsonObject jo = op.getAsJsonObject();
                ExecuteNode node = new ExecuteNode();
                node.pos = BlockPos.of(jo.get("pos").getAsLong());
                JsonArray ja = jo.get("operations").getAsJsonArray();
                for (JsonElement op2 : ja) {
                    JsonObject jo2 = op2.getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : jo2.entrySet()) {
                        IOperation<JsonElement> op3 = (IOperation<JsonElement>) Class.forName(entry.getKey()).newInstance();
                        op3.readFromJson(entry.getValue());
                        node.ops.add(op3);
                    }
                }
                temp.add(node);
            }
            executeNodes = temp;
            renderNodes = jo5.get("renderNodes").getAsBoolean();
            nodeExecMinDelay = jo5.get("nodeExecMinDelay").getAsLong();
            nodeExecMaxDelay = jo5.get("nodeExecMaxDelay").getAsLong();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void save() {
        JsonObject jo = new JsonObject();

        JsonArray ja = new JsonArray();

        for (ExecuteNode node : executeNodes) {
            if (node.isTemp) continue;
            JsonObject jo2 = new JsonObject();

            jo2.addProperty("pos", node.pos.asLong());

            JsonArray ja2 = new JsonArray();
            for (IOperation<?> op : node.ops) {
                JsonObject jo3 = new JsonObject();
                jo3.add(op.getClass().getName(), op.saveAsJson());
                ja2.add(jo3);
            }

            jo2.add("operations", ja2);
            ja.add(jo2);
        }
        jo.add("ops", ja);
        jo.addProperty("renderNodes", renderNodes);
        jo.addProperty("nodeExecMinDelay", nodeExecMinDelay);
        jo.addProperty("nodeExecMaxDelay", nodeExecMaxDelay);

        try {
            FileUtils.writeStringToFile(nodeConfigFile, jo.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isInGarden() {
        return "garden".equals(StatusManager.get().getMode());
    }

    public void toggleRenderNode() {
        renderNodes = !renderNodes;
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e已切换渲染节点为" + (renderNodes ? "§a开" : "§c关")));
        save();
    }

    public void setNodeExecMinDelay(long delay) {
        if (nodeExecMaxDelay < delay) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c设置的最小值不能超过你设置的最大值" + nodeExecMaxDelay + "ms!"));
            return;
        }
        nodeExecMinDelay = delay;
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e已设置节点执行全局最小延迟为" + delay + "ms!"));
        save();
    }

    public void setNodeExecMaxDelay(long delay) {
        if (nodeExecMinDelay > delay) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c设置的最大值不能低于你设置的最小值" + nodeExecMinDelay + "ms!"));
            return;
        }
        nodeExecMaxDelay = delay;
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e已设置节点执行全局最大延迟为" + delay + "ms!"));
        save();
    }

    private PageSwitchCallback pageSwitchCallback = new PageSwitchCallback();

    private void onScreenAfterInit(Minecraft minecraft, Screen screen, int i, int i1) {
        if(pageSwitchCallback != null) {
            pageSwitchCallback.setScreen(screen);
            pageSwitchCallback = null;
        }
    }

}
