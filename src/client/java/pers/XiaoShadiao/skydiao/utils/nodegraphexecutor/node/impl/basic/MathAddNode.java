package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 加法：a + b。 */
public final class MathAddNode extends AbstractNode {

    public static final AbstractNodeFactory<MathAddNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "math_add";
        }

        @Override
        public MathAddNode createNode(String id, Map<String, Object> literals) {
            return new MathAddNode(id, literals);
        }
    };

    private MathAddNode(String id, Map<String, Object> literals) {
        super(id, "math_add", literals);
    }

    @Override
    public String label() {
        return "加法";
    }

    @Override
    public String execute(NodeContext ctx) {
        double a = ctx.inputNumber(this, "a", 0);
        double b = ctx.inputNumber(this, "b", 0);
        ctx.setOutput(id, "value", a + b);
        return "out";
    }
}