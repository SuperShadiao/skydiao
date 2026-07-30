package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.FreecamAndFreelook;

@Mixin(Options.class)
public class MixinOption {

    @WrapMethod(method = "getCameraType")
    private CameraType getCameraType(Operation<CameraType> original) {
        FreecamAndFreelook.CameraEntity entity = AbstractListener.freecamAndFreelook.getCameraEntity();
        if(entity != null && entity.getCameraType() == FreecamAndFreelook.CameraEntity.CameraType.FREELOOK) return CameraType.THIRD_PERSON_BACK;
        return original.call();
    }

}
