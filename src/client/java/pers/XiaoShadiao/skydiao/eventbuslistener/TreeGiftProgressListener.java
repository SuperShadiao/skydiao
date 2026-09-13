package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.hud.CustomBossbar;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.awt.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TreeGiftProgressListener extends AbstractForagingListener {

    private static final Pattern TREE_GIFT_PATTERN = Pattern.compile("(.*) TREE (\\d+)%");

    @Override
    public String getListenerName() {
        return "TreeGiftProgressListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
    }

    private void onStartTick(Minecraft mc) {
        if (mc.player == null || mc.level == null || !ConfigManager.treeProgress.getValue() || !isInForagingArea()) {
            return;
        }

        List<ArmorStand> list = mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(10));
        ArmorStand treeProgress = list.stream().filter(armorStand -> TREE_GIFT_PATTERN.matcher(ToolList.getInstance().deleteColorCode(armorStand.getName().getString())).matches()).findFirst().orElse(null);
        if(treeProgress != null) {
            for (ArmorStand armorStand : list) {
                if(ToolList.getInstance().deleteColorCode(armorStand.getName().getString()).contains(mc.getUser().getName())) {
                    if(armorStand.distanceTo(treeProgress) < 2) {
                        XSDHUD.customBossbar.addCustomElement(treeProgress.getUUID(), (_) -> {
                            CustomBossbar.AnimationManager animationManager = new CustomBossbar.AnimationManager(null, null);
                            return animationManager.setHealthUpdater(am -> {
                                Matcher matcher = TREE_GIFT_PATTERN.matcher(ToolList.getInstance().deleteColorCode(treeProgress.getName().getString()));
                                if(matcher.find()) {
                                    if(ToolList.getInstance().isEntityOnWorld(treeProgress)) {
                                        am.currentHealth = 100 - Integer.parseInt(matcher.group(2));
                                        am.bossName = treeProgress.getName();
                                    } else {
                                        am.currentHealth = 0;
                                        am.bossName = Component.literal("Tree");
                                    }
                                    am.maxHealth = 100;
                                    am.setHealthColor(switch (matcher.group(1)) {
                                        case "HELIX" -> new Color(0xFF6A00);
                                        case "MANGROVE" -> new Color(0x168F00);
                                        default -> new Color(0x6AA100);
                                    });
                                }
                            });
                        });
                    }
                }
            }
        }
    }

}
