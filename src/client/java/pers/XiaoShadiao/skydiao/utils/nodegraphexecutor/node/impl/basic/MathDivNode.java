package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 除法：a / b。 */
public final class MathDivNode extends AbstractNode {

    public static final AbstractNodeFactory<MathDivNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "math_div";
        }

        @Override
        public MathDivNode createNode(String id, Map<String, Object> literals) {
            return new MathDivNode(id, literals);
        }
    };

    private MathDivNode(String id, Map<String, Object> literals) {
        super(id, "math_div", literals);
    }

    @Override
    public String label() {
        return "除法";
    }

    @Override
    public String execute(NodeContext ctx) {
        double a = ctx.inputNumber(this, "a", 0);
        double b = ctx.inputNumber(this, "b", 0);
        ctx.setOutput(id, "value", a / b);
        return "out";
    }
}