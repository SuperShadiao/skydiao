package pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.IMacro;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming.op.IOperation;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming.op.OperationPressMove;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming.op.OperationSendCommand;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.keybinds.KeyBindsManager;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class EasyFarmingScriptListener extends AbstractListener implements IMacro {

    private static final File nodeConfigFile = new File(ConfigManager.config_folder, "farmingNode.json");

    private static String notEditing() {
        return "§a[小沙雕] §c你没有正在编辑的节点! 使用§e/skydiaofarmingscript editnearestnode§c来选择你最近的一个节点来编辑!";
    }

    private static String tryStartInEditing() {
        return "§a[小沙雕] §c请先使用§e/skydiaofarmingscript exitedit§c来退出节点编辑模式!";
    }

    public void addNode(ExecuteNode node) {
        if(!isInGarden()) {
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
        if(currentEditing == null) {
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

    private int currentHandItemIndex;

    private boolean enabled;

    private boolean renderNodes = true;

    @Override
    public String getListenerName() {
        return "EasyFarmingScriptListener";
    }

    @Override
    public void registerListeners() {
        load();
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(this::onUnload);
        WorldRenderEvents.END_MAIN.register(this::onLastRender);
        CustomFabricEvents.MOUSE_BUTTON_EVENT.register(this::onMouseButton);
    }

    private boolean onMouseButton(long windwos, MouseButtonInfo mouseButtonInfo, int state) {
        if(mouseButtonInfo.button() == 1 && isHoldingMouseMat()) {
            lastSendCommandTime = System.currentTimeMillis();
        }
        return false;
    }

    private void onLastRender(WorldRenderContext context) {
        if(!isInGarden() || (!renderNodes && currentEditing == null)) return;
        RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        for (ExecuteNode node : executeNodes) {
            if(node == currentEditing) {
                RenderUtils.renderESP(wr, node.pos, 0, 1, 0, 1, false);
                RenderUtils.renderTrace(wr, node.pos, 0, 1, 0, 1);
            } else if(node.isTemp) {
                RenderUtils.renderESP(wr, node.pos, 1, 0, 0.5f, 1, false);
            } else if(node == currentWorking) {
                RenderUtils.renderESP(wr, node.pos, 1, 0, 0, 1, false);
            } else {
                RenderUtils.renderESP(wr, node.pos, 1, 1, 0, 1, false);
            }
        }
    }

    private void onUnload(Minecraft mc, ClientLevel level) {
        currentWorking = currentEditing = null;
        enabled = false;
        if(isInGarden()) save();
    }

    private boolean isHoldingMouseMat() {
        return mc.player != null && ToolList.getInstance().deleteColorCode(mc.player.getInventory().getSelectedItem().getHoverName().getString()).equals("Squeaky Mousemat");
    }

    private void onStartClientTick(Minecraft mc) {
        if(mc.player == null) return;

        if(mc.options.keyUse.isDown() && isHoldingMouseMat()) {
            lastSendCommandTime = System.currentTimeMillis();
        }

        boolean toggled = false;
        if(enabled && currentHandItemIndex != mc.player.getInventory().getSelectedSlot() && currentWorking != null) {
            ended = true;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e手上物品发生变化, 已自动停止脚本"));
        }
        while (KeyBindsManager.toggleFarmingScript.consumeClick() || (ended && enabled)) {
            enabled = !enabled;
            ended = false;
            toggled = true;
        }
        if(enabled && currentEditing != null) {
            enabled = false;
            ToolList.printChatMessage(Component.literal(tryStartInEditing()));
        } else if(!isInGarden() && enabled && toggled) {
            enabled = false;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c请在Garden开启农业脚本!"));
        } if(toggled) {
            if(enabled) {
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §e农业脚本已§a开启§e, 前往任意一个节点以开始执行"));
            } else {
                if(currentWorking != null) {
                    ExecuteNode tempNode0 = currentWorking.clone();
                    tempNode0.isTemp = true;
                    tempNode0.pos = BlockPos.containing(mc.player.position());
                    executeNodes.add(tempNode0);
                    for(int i = -5; i < 6; i++) {
                        if(i == 0) continue;
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


        if(enabled) {
            getNearestNode().ifPresent(node -> {
                if(BlockPos.containing(mc.player.position()).equals(node.pos)) {
                    if(currentWorking != node) {
                        currentWorking = node;
                        InputSimulator.unpressAllKey();
                        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e到达节点" + node.pos + ", 执行节点操作" + node.ops.stream().map(IOperation::toChatString).toList()));
                        activeThisMacro();
                        currentHandItemIndex = mc.player.getInventory().getSelectedSlot();
                        if(node.ops.isEmpty()) {
                            XSDHUD.bigTitle.updateTitleMsg("§e已到达节点尽头!", 5000, SoundEvents.WITHER_SPAWN);
                            ended = true;
                        } else {
                            for (IOperation<?> op : node.ops) {
                                op.op();
                                if(op instanceof OperationSendCommand) {
                                    lastSendCommandTime = System.currentTimeMillis();
                                }
                            }
                        }
                        executeNodes.removeIf(n -> n.isTemp);
                    }
                }
            });
        }
    }

    @Override
    public boolean isMacroActive() {
        return currentWorking != null;
    }

    @Override
    public boolean onMacroCheck(PositionInfo beforeTP, PositionInfo afterTP) {
        if(System.currentTimeMillis() - lastSendCommandTime < 5000) return true;
        ToolList.addThreadedTask(() -> {
            Thread.sleep(5000);
            ended = true;
            return null;
        });
        return false;
    }

    @Override
    public boolean onMacroCheck(int beforeSlot, int afterSlot) {
        ToolList.addThreadedTask(() -> {
            Thread.sleep(5000);
            ended = true;
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
        if(mc.player == null) return Optional.empty();
        return executeNodes.stream().min(Comparator.comparingDouble(node -> mc.player.distanceToSqr(node.pos.getX() + 0.5, node.pos.getY() + 0.5, node.pos.getZ() + 0.5)));
    }

    public void setCurrentEditing(ExecuteNode node) {
        if(!isInGarden()) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c你需要在Garden才可以编辑节点!"));
            return;
        }
        if(currentEditing != null && node == null) save();
        currentEditing = node;
        ToolList.printChatMessage(Component.literal(node == null ? "§a[小沙雕] 已退出编辑节点模式, 按下按键§e" + KeyBindsManager.toggleFarmingScript.getTranslatedKeyMessage().getString() + "§a可切换脚本开关 (可在控制设置里设置快捷键)" : "§a[小沙雕] §e当前正在编辑的节点已标记为§a绿色"));
    }

    public void addOperationToCurrentEditing(IOperation<?> op) {
        if(currentEditing == null) {
            ToolList.printChatMessage(Component.literal(notEditing()));
            return;
        }
        currentEditing.ops.add((IOperation<JsonElement>) op);
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e已添加操作" + op.toChatString() + "到当前节点"));
        save();
        listOperation();
    }
    public void removeOperationFromCurrentNode(int index) {
        if(currentEditing == null) {
            ToolList.printChatMessage(Component.literal(notEditing()));
            return;
        }
        if(index < 0 || index >= currentEditing.ops.size()) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c操作序号无效, 请输入§e/skydiaofarmingscript listoperation§c查看当前节点操作序号"));
            listOperation();
            return;
        }
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e已从当前节点删除操作" + currentEditing.ops.remove(index).toChatString()));
        save();
        listOperation();
    }

    public void cloneCurrentEditing() {
        if(currentEditing == null || mc.player == null) {
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
        if(currentEditing == null) {
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
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void save() {
        JsonObject jo = new JsonObject();

        JsonArray ja = new JsonArray();

        for (ExecuteNode node : executeNodes) {
            if(node.isTemp) continue;
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
        save();
    }
}
