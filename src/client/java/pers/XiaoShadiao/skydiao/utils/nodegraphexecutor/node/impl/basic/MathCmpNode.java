package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.executor.NodeExecException;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.List;
import java.util.Map;

/** 比较：按运算符比较两数，非法运算符直接抛错。 */
public final class MathCmpNode extends AbstractNode {

    public static final AbstractNodeFactory<MathCmpNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "math_cmp";
        }

        @Override
        public MathCmpNode createNode(String id, Map<String, Object> literals) {
            return new MathCmpNode(id, literals);
        }
    };

    private MathCmpNode(String id, Map<String, Object> literals) {
        super(id, "math_cmp", literals);
    }

    @Override
    public String label() {
        return "比较";
    }

    @Override
    public String execute(NodeContext ctx) {
        double a = ctx.inputNumber(this, "a", 0);
        double b = ctx.inputNumber(this, "b", 0);
        String op = ctx.inputString(this, "op", ">");
        boolean r;
        if (op.equals(">")) r = a > b;
        else if (op.equals("<")) r = a < b;
        else if (op.equals(">=")) r = a >= b;
        else if (op.equals("<=")) r = a <= b;
        else if (op.equals("==")) r = a == b;
        else if (op.equals("!=")) r = a != b;
        else throw new NodeExecException("E_BAD_OPERATOR",
                "运算符不合法：「" + op + "」，只支持 > < >= <= == !=",
                Map.of("port", "op", "value", op, "allowed", List.of(">", "<", ">=", "<=", "==", "!=")));
        ctx.setOutput(id, "value", r);
        return "out";
    }
}