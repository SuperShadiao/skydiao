package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.keybinds.KeyBindsManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AutoClickerListener extends AbstractListener {

    private boolean leftAutoClickingEnabled = false;
    private boolean rightAutoClickingEnabled = false;

    private boolean isLeftAutoClicking = false;
    private boolean isRightAutoClicking = false;

    private long nextLeftClickTime = 0L;
    private long nextRightClickTime = 0L;

    private boolean breakKeyHeld = false;

    @Override
    public String getListenerName() {
        return "AutoClickerListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientStartTick);
        CustomFabricEvents.MOUSE_BUTTON_EVENT.register(this::onMouseClick);
        load();
    }

    private long clickIntervalMillis() {
        int min = Math.max(1, Math.min(40, ConfigManager.autoClickerMinCps.getValue()));
        int max = Math.max(min, Math.min(40, ConfigManager.autoClickerMaxCps.getValue()));
        int cps = min;
        if (max > min) cps = min + ToolList.getInstance().random.nextInt(max - min + 1);
        return Math.max(1L, (long) (1000.0D / cps));
    }

    private boolean holdingBreakKey() {
        if (!ConfigManager.autoClickerBreakBlocks.getValue()) return false;
        if (mc.hitResult == null) return false;
        return mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
    }

    private void setAttackKey(boolean pressed) {
        com.mojang.blaze3d.platform.InputConstants.Key key =
                com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(0);
        net.minecraft.client.KeyMapping.set(key, pressed);
    }

    private void pressAttackKey() {
        InputSimulator.singleLeftClick();
    }

    private boolean onMouseClick(long windows, CustomFabricEvents.ClickType clickType, int state) {
        if(mc.gui.screen() == null) {
            boolean flag = false;
            if (isRightAutoClickingEnabled()) {
                if (clickType == CustomFabricEvents.ClickType.RIGHT) {
                    isRightAutoClicking = state == 1;
                    if(isRightAutoClicking) {
                        flag = true;
                        nextRightClickTime = System.currentTimeMillis() + clickIntervalMillis();
                        InputSimulator.singleRightClick();
                    }
                }
            }
            if (isLeftAutoClickingEnabled()) {
                if (clickType == CustomFabricEvents.ClickType.LEFT) {
                    isLeftAutoClicking = state == 1;
                    if(isLeftAutoClicking) {
                        flag = true;
                        nextLeftClickTime = System.currentTimeMillis() + clickIntervalMillis();
                        InputSimulator.singleLeftClick();
                    }
                }
            }
            return flag;
        }
        return false;
    }

    private void onClientStartTick(Minecraft mc) {
        while(KeyBindsManager.leftAutoClickerSwap.consumeClick()) {
            leftAutoClickingEnabled = !leftAutoClickingEnabled;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e左键连点已" + (leftAutoClickingEnabled ? "§a启用§e, 长按左键即可开始连点" : "§c禁用")));
        }
        while(KeyBindsManager.rightAutoClickerSwap.consumeClick()) {
            rightAutoClickingEnabled = !rightAutoClickingEnabled;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e右键连点已" + (rightAutoClickingEnabled ? "§a启用§e, 长按右键即可开始连点" : "§c禁用")));
        }
        if(isRightAutoClickingEnabled()) {
            if(isRightAutoClicking) {
                if (System.currentTimeMillis() >= nextRightClickTime) {
                    nextRightClickTime = System.currentTimeMillis() + clickIntervalMillis();
                    InputSimulator.singleRightClick();
                }
            }
        } else {
            nextRightClickTime = 0L;
            isRightAutoClicking = false;
        }
        if(isLeftAutoClickingEnabled()) {
            if(isLeftAutoClicking) {
                if (holdingBreakKey()) {
                    if (!breakKeyHeld) {
                        setAttackKey(true);
                        breakKeyHeld = true;
                    }
                } else {
                    if (breakKeyHeld) {
                        setAttackKey(false);
                        breakKeyHeld = false;
                    }
                    if (System.currentTimeMillis() >= nextLeftClickTime) {
                        nextLeftClickTime = System.currentTimeMillis() + clickIntervalMillis();
                        InputSimulator.singleLeftClick();
                    }
                }
            } else {
                nextLeftClickTime = 0L;
                isLeftAutoClicking = false;
                if (breakKeyHeld) {
                    setAttackKey(false);
                    breakKeyHeld = false;
                }
            }
        } else {
            nextLeftClickTime = 0L;
            isLeftAutoClicking = false;
            if (breakKeyHeld) {
                setAttackKey(false);
                breakKeyHeld = false;
            }
        }
    }

    private boolean isLeftAutoClickingEnabled() {
        if(leftAutoClickingEnabled) return true;
        if(mc.player == null) return false;
        String itemUUID = ToolList.getInstance().tryGetSkyblockItemUUID(mc.player.getMainHandItem());
        if(!ToolList.getInstance().stringHasContext(itemUUID)) return ConfigManager.autoClickerVanilla.getValue();
        for(AutoClickItemEntry entry : itemConfig) {
            if(entry.itemUUID().equals(itemUUID)) {
                return entry.leftClick();
            }
        }
        return false;
    }

    private boolean isRightAutoClickingEnabled() {
        if(rightAutoClickingEnabled) return true;
        if(mc.player == null) return false;
        String itemUUID = ToolList.getInstance().tryGetSkyblockItemUUID(mc.player.getMainHandItem());
        if(!ToolList.getInstance().stringHasContext(itemUUID)) return ConfigManager.autoClickerVanilla.getValue();
        for(AutoClickItemEntry entry : itemConfig) {
            if(entry.itemUUID().equals(itemUUID)) {
                return entry.rightClick();
            }
        }
        return false;
    }

    private static final File autoClickItemConfig = ConfigManager.getCustomConfigFileName("autoClickItemList.json");

    private List<AutoClickItemEntry> itemConfig = new ArrayList<>();

    private record AutoClickItemEntry(@NotNull String itemUUID, boolean leftClick, boolean rightClick) {

        private JsonObject toJson() {
            JsonObject jo = new JsonObject();
            jo.addProperty("itemUUID", itemUUID);
            jo.addProperty("leftClick", leftClick);
            jo.addProperty("rightClick", rightClick);
            return jo;
        }

        private static AutoClickItemEntry fromJson(JsonObject jsonObject) {
            return new AutoClickItemEntry(jsonObject.get("itemUUID").getAsString(), jsonObject.get("leftClick").getAsBoolean(), jsonObject.get("rightClick").getAsBoolean());
        }

    }

    private void load() {
        try {
            List<AutoClickItemEntry> list = new ArrayList<>();
            String s = FileUtils.readFileToString(autoClickItemConfig, StandardCharsets.UTF_8);
            JsonArray ja = JsonParser.parseString(s).getAsJsonArray();
            for (JsonElement je : ja) {
                list.add(AutoClickItemEntry.fromJson(je.getAsJsonObject()));
            }
            itemConfig = list;
        } catch (Throwable _) {}
    }

    private void save() {
        JsonArray ja = new JsonArray();
        for (AutoClickItemEntry entry : itemConfig) {
            ja.add(entry.toJson());
        }
        try {
            FileUtils.writeStringToFile(autoClickItemConfig, ja.toString(), StandardCharsets.UTF_8);
        } catch (Throwable _) {}
    }

    public void add(boolean leftClick, boolean rightClick) {
        if(mc.player == null) return;
        String itemUUID = ToolList.getInstance().tryGetSkyblockItemUUID(mc.player.getMainHandItem());
        if(!ToolList.getInstance().stringHasContext(itemUUID)) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c无法对没有UUID的物品进行设置连点状态!"));
            return;
        }

        itemConfig.removeIf(itemEntry -> itemEntry.itemUUID().equals(itemUUID));
        itemConfig.add(new AutoClickItemEntry(itemUUID, leftClick, rightClick));
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §a已设置物品§e" + ToolList.getInstance().tryGetSkyblockItemId(mc.player.getMainHandItem()) + " (" + itemUUID + ")§a 的连点状态"));
        save();
    }

    public void remove() {
        if(mc.player == null) return;
        String itemUUID = ToolList.getInstance().tryGetSkyblockItemUUID(mc.player.getMainHandItem());
        if(!ToolList.getInstance().stringHasContext(itemUUID)) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c无法对没有UUID的物品进行设置连点状态!"));
            return;
        }

        remove(itemUUID);
    }

    public void remove(String itemUUID) {
        if (itemConfig.removeIf(itemEntry -> itemEntry.itemUUID().equals(itemUUID))) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §a已移除物品§e" + itemUUID + "§a的连点状态"));
        }
        save();
    }

}
