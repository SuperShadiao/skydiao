package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface MixinAbstractContainerScreenPosGetter {

    @Accessor("leftPos")
    public int getLeftPos();
    @Accessor("topPos")
    public int getTopPos();

}
