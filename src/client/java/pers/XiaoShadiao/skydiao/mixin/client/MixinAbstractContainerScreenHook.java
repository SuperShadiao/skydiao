package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;

@Mixin(AbstractContainerScreen.class)
public class MixinAbstractContainerScreenHook extends Screen {

    protected MixinAbstractContainerScreenHook(Component component) {
        super(component);
    }

    @Inject(method = "extractContents", at = @At("TAIL"))
    public void /*renderContents*/extractContents(GuiGraphicsExtractor guiGraphics, int i, int j, float f, CallbackInfo ci) {
        AbstractListener.inventoryItemFilter.postRender(this, guiGraphics, i, j, f);
    }

}
