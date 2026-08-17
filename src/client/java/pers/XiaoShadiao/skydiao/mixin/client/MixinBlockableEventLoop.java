package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.CrashReport;
import net.minecraft.util.thread.BlockableEventLoop;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Supplier;

@Mixin(BlockableEventLoop.class)
public class MixinBlockableEventLoop {

    @Nullable @Shadow
    private static volatile Supplier<CrashReport> delayedCrash;

    @WrapMethod(method = "throwDelayedException")
    public void throwDelayedException(Operation<Void> original) throws Throwable {
        try {
            original.call();
        } catch (Throwable e) {
            delayedCrash = null;
            throw e;
        }
    }

}
