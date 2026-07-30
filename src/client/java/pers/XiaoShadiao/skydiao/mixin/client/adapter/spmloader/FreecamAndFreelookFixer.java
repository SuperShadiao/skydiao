package pers.XiaoShadiao.skydiao.mixin.client.adapter.spmloader;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;

@Mixin(targets = "com.micaftic.morpher.util.CameraUtil")
public class FreecamAndFreelookFixer {

    @WrapMethod(method = "isFirstPerson")
    private static boolean isFirstPerson(AnimatableEntity<? extends Entity> animatableEntity, Operation<Boolean> original) {
        if (AbstractListener.freecamAndFreelook.getCameraEntity() != null) {
            return false;
        }
        return original.call(animatableEntity);
    }

}
