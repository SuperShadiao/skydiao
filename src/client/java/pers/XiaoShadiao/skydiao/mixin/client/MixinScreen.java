package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;

@Mixin(Screen.class)
public class MixinScreen {

    @Inject(method = "extractBackground", at = @At("TAIL"))
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        CustomFabricEvents.AFTER_BACKGROUND_RENDER.invoker().render((Screen) (Object) this, graphics, mouseX, mouseY, a);
    }

}
