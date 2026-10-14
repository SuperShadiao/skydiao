package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 对象是否为空：只判断是否为 null，空文本 / 空 List·Set·Map 都不算空。 */
public final class ObjNullNode extends AbstractNode {

    public static final AbstractNodeFactory<ObjNullNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "obj_null";
        }

        @Override
        public ObjNullNode createNode(String id, Map<String, Object> literals) {
            return new ObjNullNode(id, literals);
        }
    };

    private ObjNullNode(String id, Map<String, Object> literals) {
        super(id, "obj_null", literals);
    }

    @Override
    public String label() {
        return "对象为空";
    }

    @Override
    public String execute(NodeContext ctx) {
        ctx.setOutput(id, "value", ctx.input(this, "obj", null) == null);
        return "out";
    }
}
