package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 乘法：a * b。 */
public final class MathMulNode extends AbstractNode {

    public static final AbstractNodeFactory<MathMulNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "math_mul";
        }

        @Override
        public MathMulNode createNode(String id, Map<String, Object> literals) {
            return new MathMulNode(id, literals);
        }
    };

    private MathMulNode(String id, Map<String, Object> literals) {
        super(id, "math_mul", literals);
    }

    @Override
    public String label() {
        return "乘法";
    }

    @Override
    public String execute(NodeContext ctx) {
        double a = ctx.inputNumber(this, "a", 0);
        double b = ctx.inputNumber(this, "b", 0);
        ctx.setOutput(id, "value", a * b);
        return "out";
    }
}