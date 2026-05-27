package pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming;

import com.google.gson.JsonElement;
import net.minecraft.core.BlockPos;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming.op.IOperation;

import java.util.ArrayList;
import java.util.List;

public class ExecuteNode implements Cloneable {

    public List<IOperation<JsonElement>> ops = new ArrayList<>();
    public BlockPos pos = BlockPos.ZERO;
    public boolean isTemp;

    @Override
    public ExecuteNode clone() {
        try {
            ExecuteNode clone = (ExecuteNode) super.clone();
            clone.ops = ops.stream().map(IOperation::clone).toList();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
