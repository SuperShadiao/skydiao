package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;

import java.util.List;

@Mixin(GuiGraphics.class)
public class MixinTooltipScroll {

    @Final
    @Shadow
    private Matrix3x2fStack pose;

    @WrapMethod(method = "renderTooltip")
    public void drawTooltip(Font font, List<ClientTooltipComponent> list, int i, int j, ClientTooltipPositioner clientTooltipPositioner, @Nullable Identifier identifier, Operation<Void> original) {
        int scroll = AbstractListener.tooltipScrollController.getOffsetScroll(font, list);
        pose.pushMatrix();
        pose.translate(0, scroll);

        original.call(font, list, i, j, clientTooltipPositioner, identifier);

        pose.popMatrix();
    }

}
