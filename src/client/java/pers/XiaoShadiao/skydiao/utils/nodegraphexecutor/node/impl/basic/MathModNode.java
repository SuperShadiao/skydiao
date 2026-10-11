package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 取余：a % b。 */
public final class MathModNode extends AbstractNode {

    public static final AbstractNodeFactory<MathModNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "math_mod";
        }

        @Override
        public MathModNode createNode(String id, Map<String, Object> literals) {
            return new MathModNode(id, literals);
        }
    };

    private MathModNode(String id, Map<String, Object> literals) {
        super(id, "math_mod", literals);
    }

    @Override
    public String label() {
        return "取余";
    }

    @Override
    public String execute(NodeContext ctx) {
        double a = ctx.inputNumber(this, "a", 0);
        double b = ctx.inputNumber(this, "b", 0);
        ctx.setOutput(id, "value", a % b);
        return "out";
    }
}