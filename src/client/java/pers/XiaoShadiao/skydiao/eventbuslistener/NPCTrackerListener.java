package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.neu.NeuItemRepository;

import java.util.Objects;
import java.util.Optional;

public class NPCTrackerListener extends AbstractListener {

    private String currentTrackingNPC = null;
    private BlockPos targetPos = null;

    private boolean tempDisable = false;

    @Override
    public String getListenerName() {
        return "NPCTrackerListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onTick);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::onUnload);
    }

    private void onUnload(Minecraft mc, ClientLevel level) {
        tempDisable = false;
    }

    private void onTick(Minecraft mc) {
        if (currentTrackingNPC == null || targetPos == null || mc.player == null || mc.level == null) {
            return;
        }

        if(tempDisable) return;

        if(!Objects.equals(getTargetIslandType(), StatusManager.get().getMode())) {
            tempDisable = true;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e目标 §b" + getDisplayName() + " §e位于§a " + getTargetIslandType() + " §e岛屿, 前往对应岛屿以开始追踪"));
            return;
        }

        if (!MacroManagerListener.pathFinderV2Executor.isRunning()) {
            MacroManagerListener.pathFinderV2Executor.startExecution(targetPos);
            MacroManagerListener.pathFinderV2Executor.pauseExecution();
        } else if (mc.player.blockPosition().distManhattan(targetPos) <= 5) {
            MacroManagerListener.pathFinderV2Executor.stopExecution();
            ToolList.printChatMessage(Component.literal(
                    "§a[小沙雕] §e已到达 §b" + getDisplayName() + " §e附近"));
            currentTrackingNPC = null;
            targetPos = null;
        }
    }

    public void startTrackingNPC(String npcName) {
        Optional<NeuItemRepository.NeuItem> optional = NeuItemRepository.INSTANCE.find(npcName);
        if (optional.isEmpty()) {
            ToolList.printChatMessage(Component.literal(
                    "§a[小沙雕] §c未找到NPC数据: " + npcName));
            return;
        }

        NeuItemRepository.NeuItem npc = optional.get();
        if (!npc.hasLocation()) {
            ToolList.printChatMessage(Component.literal(
                    "§a[小沙雕] §cNPC §b" + npc.cleanName() + " §c没有坐标数据，无法追踪"));
            return;
        }

        if (MacroManagerListener.pathFinderV2Executor.isRunning()) {
            MacroManagerListener.pathFinderV2Executor.stopExecution();
        }

        currentTrackingNPC = npc.internalName();
        targetPos = new BlockPos(npc.x(), npc.y(), npc.z());

        ToolList.printChatMessage(Component.literal(
                "§a[小沙雕] §e追踪NPC: §b" + npc.cleanName()));
        ToolList.printChatMessage(Component.literal(
                "§a[小沙雕] §7位置: §f" + npc.locationText()));
        ToolList.printChatMessage(Component.literal(
                "§a[小沙雕] §e可使用 /skydiao tracknpc cancel 取消追踪"));
    }

    public void cancelTrackingNPC() {
        if (currentTrackingNPC != null && MacroManagerListener.pathFinderV2Executor.isRunning()) {
            MacroManagerListener.pathFinderV2Executor.stopExecution();
        }
        currentTrackingNPC = null;
        targetPos = null;
    }

    public String getDisplayName() {
        if (currentTrackingNPC == null) {
            return "";
        }
        return NeuItemRepository.INSTANCE.find(currentTrackingNPC)
                .map(NeuItemRepository.NeuItem::cleanName)
                .orElse(currentTrackingNPC);
    }

    public @Nullable String getTargetIslandType() {
        if (currentTrackingNPC == null) {
            return null;
        }
        return NeuItemRepository.INSTANCE.find(currentTrackingNPC)
                .map(NeuItemRepository.NeuItem::island)
                .orElse(null);
    }

}
