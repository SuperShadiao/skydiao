package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface MixinGameRendererAccessor {

    @Accessor("guiRenderer")
    GuiRenderer skydiao$getGuiRenderer();

}
