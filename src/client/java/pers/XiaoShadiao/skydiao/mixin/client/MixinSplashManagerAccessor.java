package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.SplashManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Gui.class)
public interface MixinSplashManagerAccessor {

    @Accessor("splashManager")
    public void setSplashManager(SplashManager splashManager);

}
