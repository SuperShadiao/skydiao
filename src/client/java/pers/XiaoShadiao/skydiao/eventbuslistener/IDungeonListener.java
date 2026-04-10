package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

public interface IDungeonListener {

    public abstract int getFloor();

    public default boolean isInMasterDungeonFloor() {
        return (StatusManager.get().isInDungeon() && ToolList.getInstance().fetchScoreboardLinesNoColor().stream().anyMatch(line -> line.contains("The Catacombs (M")));
    }

    public default boolean isInCorrectDungeon() {
        return (StatusManager.get().isInDungeon() && ToolList.getInstance().fetchScoreboardLinesNoColor().stream()
                .anyMatch(line -> line.contains("The Catacombs (M" + getFloor()) || line.contains("The Catacombs (F" + getFloor())));
    }

    public default boolean isSoloingDungeon() {
        return (StatusManager.get().isInDungeon() && ToolList.getInstance().fetchScoreboardLinesNoColor().stream()
                .anyMatch(line -> line.trim().equalsIgnoreCase("Solo")));
    }

    public default void sendDungeonF7ChatMessage(String message) {
        if(ConfigManager.dungeonf7msgbot.getValue()) {
            if(ToolList.getInstance().isDevEnvironment()) {
                ToolList.printChatMessage(Component.literal(message));
            } else {
                ToolList.getInstance().log.info(message);
            }
            if(ToolList.mc.player != null && !message.trim().isEmpty() && !isSoloingDungeon()) ToolList.mc.player.connection.sendChat((ToolList.getInstance().isDevEnvironment() ? "/achat" : "/pc") + " [SkyDiao] " + message);
        }
    }

}
