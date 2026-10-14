package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import com.google.gson.Gson;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 对象相等：能当数字看的按数值比（"1" 与 1 相等），其余按内容 / 结构比。 */
public final class ObjEqualsNode extends AbstractNode {

    private static final Gson GSON = new Gson();

    public static final AbstractNodeFactory<ObjEqualsNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "obj_equals";
        }

        @Override
        public ObjEqualsNode createNode(String id, Map<String, Object> literals) {
            return new ObjEqualsNode(id, literals);
        }
    };

    private ObjEqualsNode(String id, Map<String, Object> literals) {
        super(id, "obj_equals", literals);
    }

    @Override
    public String label() {
        return "对象相等";
    }

    @Override
    public String execute(NodeContext ctx) {
        Object a = ctx.input(this, "a", null);
        Object b = ctx.input(this, "b", null);
        ctx.setOutput(id, "value", looseEquals(a, b));
        return "out";
    }

    /** 两侧都能当数字看就按数值比，否则退回文本形式的相等判断；同包其它节点（如列表查找）也复用 */
    static boolean looseEquals(Object a, Object b) {
        if (a == null || b == null) return a == b;
        Double x = numOrNull(a);
        Double y = numOrNull(b);
        if (x != null && y != null) return x.doubleValue() == y.doubleValue();
        return structText(a).equals(structText(b));
    }

    /** 结构化值（List / Set / Map 等）走 JSON，和网页端 fmt 的表现对齐 */
    private static String structText(Object v) {
        if (v == null) return "null";
        if (v instanceof CharSequence || v instanceof Number || v instanceof Boolean) return String.valueOf(v);
        return GSON.toJson(v);
    }

    private static Double numOrNull(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof CharSequence cs) {
            try {
                return Double.parseDouble(cs.toString().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
