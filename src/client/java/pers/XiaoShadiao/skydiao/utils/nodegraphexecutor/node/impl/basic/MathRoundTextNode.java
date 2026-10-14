package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 四舍五入到文本：四舍五入到整数后输出文本。 */
public final class MathRoundTextNode extends AbstractNode {

    public static final AbstractNodeFactory<MathRoundTextNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "math_round_text";
        }

        @Override
        public MathRoundTextNode createNode(String id, Map<String, Object> literals) {
            return new MathRoundTextNode(id, literals);
        }
    };

    private MathRoundTextNode(String id, Map<String, Object> literals) {
        super(id, "math_round_text", literals);
    }

    @Override
    public String label() {
        return "四舍五入到文本";
    }

    @Override
    public String execute(NodeContext ctx) {
        double d = ctx.inputNumber(this, "a", 0);
        // 非有限值（NaN / 无穷）当 0，与网页端一致
        long v = Double.isFinite(d) ? Math.round(d) : 0L;
        ctx.setOutput(id, "value", String.valueOf(v));
        return "out";
    }
}
