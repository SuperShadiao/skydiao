package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.hud.DynamicSpotHUD;
import pers.XiaoShadiao.skydiao.hud.StarRailNotification;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResurrectionItemListener extends AbstractListener implements IDungeonListener {

    private int spiritCD = 0;
    private int bonzoCD = 0;
    private int phoenixCD = 0;

    private final DynamicSpotCooldown dynamicSpotCooldown = new DynamicSpotCooldown();

    @Override
    public String getListenerName() {
        return "ResurrectionItemListener";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
        ClientTickEvents.START_CLIENT_TICK.register((client) -> {
            if (spiritCD > 0) spiritCD--;
            if (bonzoCD > 0) bonzoCD--;
            if (phoenixCD > 0) phoenixCD--;
        });
    }

    private void onChat(Component component, boolean b) {
        if (!StatusManager.get().isInDungeon()) return;

        String msg = ToolList.getInstance().deleteColorCode(component.getString());

        if (msg.matches("^Second Wind Activated! Your Spirit Mask saved your life!$")) {
            sendDungeonF7ChatMessage(ConfigManager.dungeonSpiritMaskTriggered.getValue());
            if (!displayTitleStarRail("Spirit复活甲已触发!")) displayTitle("Spirit");
            spiritCD = 30 * 20;
            dynamicSpotCooldown.updateTempPart(DynamicSpotHUD.textPart(null, "⚠ Spirit", 0xFFFF4444, null));
            DynamicSpotHUD.replaceIdlePart(DynamicSpotHUD.USERNAME_PART_ID, dynamicSpotCooldown, dynamicSpotCooldown);
        } else if (msg.matches("^Your (?:. )?Bonzo's Mask saved your life!$")) {
            sendDungeonF7ChatMessage(ConfigManager.dungeonBonzoTriggered.getValue());
            if (!displayTitleStarRail("Bonzo复活甲已触发!")) displayTitle("Bonzo");
            bonzoCD = 300 * 20;
            dynamicSpotCooldown.updateTempPart(DynamicSpotHUD.textPart(null, "⚠ Bonzo", 0xFFFF4444, null));
            DynamicSpotHUD.replaceIdlePart(DynamicSpotHUD.USERNAME_PART_ID, dynamicSpotCooldown, dynamicSpotCooldown);
            if(mc.player != null && mc.level != null) {
                for (Component line : mc.player.getItemBySlot(EquipmentSlot.HEAD).getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL)) {
                    Matcher matcher = Pattern.compile("Cooldown: (\\d+)s").matcher(ToolList.getInstance().deleteColorCode(line.getString()));
                    if(matcher.find()) {
                        String cd = matcher.group(1);
                        bonzoCD = Integer.parseInt(cd) * 20 + 20;
                    }
                }
            }
        } else if (msg.matches("^Your Phoenix Pet saved you from certain death!$")) {
            sendDungeonF7ChatMessage(ConfigManager.dungeonPhoenixTriggered.getValue());
            if (!displayTitleStarRail("Phoenix复活甲已触发!")) displayTitle("Phoenix");
            phoenixCD = 60 * 20;
            dynamicSpotCooldown.updateTempPart(DynamicSpotHUD.textPart(null, "⚠ Phoenix", 0xFFFF4444, null));
            DynamicSpotHUD.replaceIdlePart(DynamicSpotHUD.USERNAME_PART_ID, dynamicSpotCooldown, dynamicSpotCooldown);
        }
    }

    private void displayTitle(String title) {
        XSDHUD.bigTitle.updateTitleMsg(title, 1000);
    }

    private boolean displayTitleStarRail(String title) {
        return addStarRailNotification(title, StarRailNotification.Type.warning);
    }

    @Override
    public int getFloor() {
        return 7;
    }

    public int getSpiritCD() {
        return spiritCD;
    }

    public int getBonzoCD() {
        return bonzoCD;
    }

    public int getPhoenixCD() {
        return phoenixCD;
    }

    private class DynamicSpotCooldown implements Supplier<DynamicSpotHUD.IdlePart>, BooleanSupplier {

        private DynamicSpotHUD.IdlePart tempPart = null;
        private long lastUpdateTime = 0;

        @Override
        public boolean getAsBoolean() {
            return getPhoenixCD() <= 0 && getBonzoCD() <= 0 && getSpiritCD() <= 0;
        }

        @Override
        public DynamicSpotHUD.IdlePart get() {
            if(tempPart != null) {
                if(System.currentTimeMillis() - lastUpdateTime < 4000) {
                    return tempPart;
                } else {
                    tempPart = null;
                }
            }
            DynamicSpotHUD.IdlePart idlePart;
            if(getPhoenixCD() > 0 && (getPhoenixCD() < getBonzoCD() || getBonzoCD() <= 0) && (getPhoenixCD() < getSpiritCD() || getSpiritCD() <= 0)) {
                idlePart = DynamicSpotHUD.textPart(null, "Phoenix " + (getPhoenixCD() / 20 + 1) + "s", 0xFFFFFFFF, null);
            } else {
                if(getBonzoCD() > 0 && (getBonzoCD() < getSpiritCD() || getSpiritCD() <= 0)) {
                    idlePart = DynamicSpotHUD.textPart(null, "Bonzo " + (getBonzoCD() / 20 + 1) + "s", 0xFFFFFFFF, null);
                } else {
                    idlePart = DynamicSpotHUD.textPart(null, "Spirit " + (getSpiritCD() / 20 + 1) + "s", 0xFFFFFFFF, null);
                }
            }
            return DynamicSpotHUD.iconPart(Identifier.fromNamespaceAndPath("skydiao", "cooldown"), DynamicSpotHUD.statusIcon("cooldown"), 0xFFFFFFFF, idlePart);
        }

        private void updateTempPart(DynamicSpotHUD.IdlePart idlePart) {
            tempPart = idlePart;
            lastUpdateTime = System.currentTimeMillis();
        }

    }

}
