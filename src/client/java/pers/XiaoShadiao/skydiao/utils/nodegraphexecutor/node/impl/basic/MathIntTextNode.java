package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 到整数文本：向零取整后输出文本。 */
public final class MathIntTextNode extends AbstractNode {

    public static final AbstractNodeFactory<MathIntTextNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "math_int_text";
        }

        @Override
        public MathIntTextNode createNode(String id, Map<String, Object> literals) {
            return new MathIntTextNode(id, literals);
        }
    };

    private MathIntTextNode(String id, Map<String, Object> literals) {
        super(id, "math_int_text", literals);
    }

    @Override
    public String label() {
        return "到整数文本";
    }

    @Override
    public String execute(NodeContext ctx) {
        double d = ctx.inputNumber(this, "a", 0);
        // 非有限值（NaN / 无穷）当 0，与网页端一致
        long v = Double.isFinite(d) ? (long) d : 0L;
        ctx.setOutput(id, "value", String.valueOf(v));
        return "out";
    }
}
