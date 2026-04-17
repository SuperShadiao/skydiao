package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LegacyRandomSource.class)
public class MixinRandomClassCrash {

    @WrapMethod(method = "next")
    public int next(int i, Operation<Integer> original) {
        boolean flag = Minecraft.getInstance() == null || Minecraft.getInstance().isSingleplayer() || Minecraft.getInstance().level == null;
        while(true) {
            try {
                return original.call(i);
            } catch (Exception e) {
                if(flag) throw e;
                e.printStackTrace();
            }
        }
    }

    @WrapMethod(method = "setSeed")
    public void setSeed(long l, Operation<Void> original) {
        boolean flag = Minecraft.getInstance() == null || Minecraft.getInstance().isSingleplayer() || Minecraft.getInstance().level == null;
        while(true) {
            try {
                original.call(l);
                return;
            } catch (Exception e) {
                if(flag) throw e;
                e.printStackTrace();
            }
        }
    }

}
