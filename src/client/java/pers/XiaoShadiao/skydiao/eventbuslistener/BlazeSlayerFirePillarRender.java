package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class BlazeSlayerFirePillarRender extends AbstractListener {

    private static final Pattern firePillarPattern = Pattern.compile("\\ds \\d hits");

    public enum BlazeShield {
        ASHEN(Color.GRAY, "BURSTFIRE_DAGGER"),
        SPIRIT(Color.WHITE, "BURSTMAW_DAGGER"),
        AURIC(Color.YELLOW, "BURSTFIRE_DAGGER"),
        CRYSTAL(Color.CYAN, "BURSTMAW_DAGGER"),;

        public final Color color;
        public final String swordName;

        BlazeShield(Color color, String swordName) {
            this.color = color;
            this.swordName = swordName;
        }

        public static BlazeShield getShield(String name) {
            for (BlazeShield shield : BlazeShield.values()) {
                if (name.contains(shield.name())) {
                    return shield;
                }
            }
            return null;
        }

        public static BlazeShield fromItem(ItemStack item) {
            List<Component> tooltipLines = item.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
            for (BlazeShield shield : BlazeShield.values()) {
                if (tooltipLines.stream().anyMatch(line -> ToolList.getInstance().deleteColorCode(line.getString()).equalsIgnoreCase("Attuned: " + shield.name()))) {
                    return shield;
                }
            }
            return null;
        }

        public BlazeShield switchShield() {
            return switch (this) {
                case ASHEN -> AURIC;
                case CRYSTAL -> SPIRIT;
                case SPIRIT -> CRYSTAL;
                case AURIC -> ASHEN;
            };
        }

        public boolean isSameSword(BlazeShield shield) {
            return this.swordName.equals(shield.swordName);
        }

    }

    @Override
    public String getListenerName() {
        return "BlazeSlayerFirePillarRender";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if(mc.level == null || mc.player == null || !"crimson_isle".equals(StatusManager.get().getMode())) return;

        RenderUtils.WorldRender wrLine = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        RenderUtils.WorldRender wrFill = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_FILL);

        if(ConfigManager.blazeSlayerFirePillarESP.getValue()) {
            for (ArmorStand armorStand : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(26))) {
                if (armorStand.getCustomName() != null && firePillarPattern.matcher(ToolList.getInstance().deleteColorCode(armorStand.getName().getString())).matches()) {
                    BlockPos pos = armorStand.blockPosition();
                    RenderUtils.renderESP(wrLine, pos, 1, 0, 0, 1, false);
                    RenderUtils.renderESP(wrFill, pos, 1, 0, 0, 1, true);
                    RenderUtils.renderTrace(wrLine, pos, 1, 0, 0, 1);
                }
            }
        }
        if(ConfigManager.blazeSlayerShieldColorESP.getValue()) {
            Stream.of(
                    mc.level.getEntitiesOfClass(Blaze.class, mc.player.getBoundingBox().inflate(26)),
                    mc.level.getEntitiesOfClass(ZombifiedPiglin.class, mc.player.getBoundingBox().inflate(26)),
                    mc.level.getEntitiesOfClass(WitherSkeleton.class, mc.player.getBoundingBox().inflate(26))
            ).flatMap(Collection::stream).forEach(entity -> {
                E2AMappingListener.MobInfo mobInfo = e2AMappingListener.getMobInfo(entity);
                if(mobInfo != null && mobInfo.armorStandForBoss != null) {
                    String name = ToolList.getInstance().deleteColorCode(mobInfo.armorStandForBoss.getName().getString());
                    BlazeShield shield = BlazeShield.getShield(name);
                    if(shield != null) {
                        RenderUtils.renderESP(wrLine, entity, shield.color.getRed() / 255.0f, shield.color.getGreen() / 255.0f, shield.color.getBlue() / 255.0f, 1, false);

                        if (BlazeShield.fromItem(mc.player.getMainHandItem()) == shield) {
                            RenderUtils.renderESP(wrFill, entity, shield.color.getRed() / 255.0f, shield.color.getGreen() / 255.0f, shield.color.getBlue() / 255.0f, 1, true);
                        }
                    }
                }
            });
        }


        wrFill.finishDraw();
        wrLine.finishDraw();
    }

}
