package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(GuiRenderer.class)
public interface MixinGuiRendererAccessor {

    @Accessor("vertexBuffer")
    StagedVertexBuffer skydiao$getVertexBuffer();

    @Accessor("draws")
    List<?> skydiao$getDraws();

    @Accessor("renderState")
    GuiRenderState skydiao$getRenderState();

    @Accessor("firstDrawIndexAfterBlur")
    void skydiao$setFirstDrawIndexAfterBlur(int value);

}
