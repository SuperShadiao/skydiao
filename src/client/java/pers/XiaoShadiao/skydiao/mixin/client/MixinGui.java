package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.resources.SplashManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.mircosoftaccount.XSDSafeSession;
import pers.XiaoShadiao.skydiao.utils.screen.XSDSplashManager;

@Mixin(Gui.class)
public class MixinGui {

    @Mutable
    @Final
    @Shadow
    private SplashManager splashManager;

    @Inject(method = "<init>", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/Gui;splashManager:Lnet/minecraft/client/resources/SplashManager;",
            shift = At.Shift.AFTER))
    private void skydiao$installSplashManager(Minecraft minecraft, Hud hud, GuiRenderState guiRenderState, CallbackInfo ci) {
        if (minecraft.getUser() instanceof XSDSafeSession user) {
            this.splashManager = new XSDSplashManager(user);
        }
    }

    @WrapMethod(method = "setScreen")
    public void skydiao$setScreen(Screen screen, Operation<Void> original) {
        original.call(CustomFabricEvents.REPLACE_SCREEN.invoker().replaceScreen(screen));
    }

}
