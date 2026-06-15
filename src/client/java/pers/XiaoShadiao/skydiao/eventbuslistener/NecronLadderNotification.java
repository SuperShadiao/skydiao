package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.ToolList;

public class NecronLadderNotification extends AbstractListener {
    @Override
    public String getListenerName() {
        return "NecronLadderNotification";
    }

    @Override
    public void registerListeners() {
        ScreenEvents.AFTER_INIT.register(this::afterScreenInit);
    }

    private void afterScreenInit(Minecraft mc, Screen screen, int scaledWidth, int scaledHeight) {
        if(!ConfigManager.necronLadderNotification.getValue()) return;
        if(screen instanceof ContainerScreen cs) {
            ScreenEvents.afterExtract(cs).register(new Listener());
        }
    }

    static class Listener implements ScreenEvents.AfterExtract {

        private boolean triggered = false;

        @Override
        public void afterExtract(Screen screen, GuiGraphicsExtractor drawContext, int mouseX, int mouseY, float tickDelta) {
            if(triggered) return;
            if(screen instanceof ContainerScreen cs) {
                ChestMenu menu = cs.getMenu();
                String chestName = ToolList.getInstance().deleteColorCode(cs.getTitle().getString());
                boolean chooseScreenFlag = chestName.matches("(Master )? ?Catacombs - Floor VII");
                boolean claimScreenFlag = chestName.equals("Bedrock");
                for (Slot slot : menu.slots) {
                    boolean hasNecronHandleLore = slot.getItem().getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL).stream().anyMatch(c -> ToolList.getInstance().deleteColorCode(c.getString()).contains("Necron's Handle"));
                    if(ToolList.getInstance().isDevEnvironment()) {
                        hasNecronHandleLore |= slot.getItem().getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL).stream().anyMatch(c -> ToolList.getInstance().deleteColorCode(c.getString()).contains("Storm the Fish"));
                    }
                    boolean conditionOnChooseScreen = chooseScreenFlag && Items.PLAYER_HEAD.equals(slot.getItem().getItem()) && hasNecronHandleLore;
                    boolean conditionOnClaimScreen = (claimScreenFlag || (ToolList.getInstance().isDevEnvironment() && chestName.equals("Obsidian"))) && (Items.STICK.equals(slot.getItem().getItem()) || ToolList.getInstance().isDevEnvironment()) && hasNecronHandleLore;

                    if (conditionOnChooseScreen || conditionOnClaimScreen) {
                        triggered = true;
                        XSDHUD.bigTitle.updateTitleMsg("§aNecron Ladder!", 5000);
                        return;
                    }
                }
            }
        }
    }
}
