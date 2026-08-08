package pers.XiaoShadiao.skydiao.appendage.eventbuslistener;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.appendage.utils.posrecord.RecordPos;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

//APPEND
public class EditingPListListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "editingplist";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if(RecordPos.plist==null) return;
        if(RecordPos.plist.positions().isEmpty()) return;
        for(Vec3 pos:RecordPos.plist.positions()){
            RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_FILL);
            RenderUtils.renderCircle(wr, pos, 1f, 36);
            wr.finishDraw();
        }


    }

}
