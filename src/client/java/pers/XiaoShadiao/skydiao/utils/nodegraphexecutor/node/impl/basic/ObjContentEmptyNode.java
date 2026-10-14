package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

/** 对象内容是否为空：空值 / 纯空白文本 / 空 List·Set·Map / 空数组都算空。 */
public final class ObjContentEmptyNode extends AbstractNode {

    public static final AbstractNodeFactory<ObjContentEmptyNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "obj_empty";
        }

        @Override
        public ObjContentEmptyNode createNode(String id, Map<String, Object> literals) {
            return new ObjContentEmptyNode(id, literals);
        }
    };

    private ObjContentEmptyNode(String id, Map<String, Object> literals) {
        super(id, "obj_empty", literals);
    }

    @Override
    public String label() {
        return "对象内容为空";
    }

    @Override
    public String execute(NodeContext ctx) {
        ctx.setOutput(id, "value", contentIsEmpty(ctx.input(this, "obj", null)));
        return "out";
    }

    private static boolean contentIsEmpty(Object v) {
        if (v == null) return true;
        if (v instanceof CharSequence cs) return cs.toString().trim().isEmpty();
        if (v instanceof Collection<?> c) return c.isEmpty();   // List / Set 等
        if (v instanceof Map<?, ?> m) return m.isEmpty();
        if (v.getClass().isArray()) return Array.getLength(v) == 0;
        return false;
    }
}
