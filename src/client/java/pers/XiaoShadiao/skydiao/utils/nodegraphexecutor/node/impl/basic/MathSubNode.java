package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 减法：a - b。 */
public final class MathSubNode extends AbstractNode {

    public static final AbstractNodeFactory<MathSubNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "math_sub";
        }

        @Override
        public MathSubNode createNode(String id, Map<String, Object> literals) {
            return new MathSubNode(id, literals);
        }
    };

    private MathSubNode(String id, Map<String, Object> literals) {
        super(id, "math_sub", literals);
    }

    @Override
    public String label() {
        return "减法";
    }

    @Override
    public String execute(NodeContext ctx) {
        double a = ctx.inputNumber(this, "a", 0);
        double b = ctx.inputNumber(this, "b", 0);
        ctx.setOutput(id, "value", a - b);
        return "out";
    }
}