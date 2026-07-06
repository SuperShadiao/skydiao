package pers.XiaoShadiao.skydiao.mixin.client.adapter.firmament;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import moe.nea.firmament.events.JoinServerEvent;
import org.spongepowered.asm.mixin.Mixin;
import pers.XiaoShadiao.skydiao.utils.ToolList;

@Mixin(targets = "moe.nea.firmament.features.misc.ModAnnouncer")
public class ModAnnouncerRemover1 {
    @WrapMethod(method = "onServerJoin")
    private void onServerJoin(JoinServerEvent event, Operation<Void> original) {
        ToolList.getInstance().log.info("已拦截Firmament的告状行为 (Mixin)");
    }
}
