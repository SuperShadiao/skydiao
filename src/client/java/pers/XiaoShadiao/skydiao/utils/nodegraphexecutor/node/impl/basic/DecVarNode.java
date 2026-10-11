package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.executor.NodeExecException;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 变量自减：变量值 -1 后写回并输出。 */
public final class DecVarNode extends AbstractNode {

    public static final AbstractNodeFactory<DecVarNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "dec_var";
        }

        @Override
        public DecVarNode createNode(String id, Map<String, Object> literals) {
            return new DecVarNode(id, literals);
        }
    };

    private DecVarNode(String id, Map<String, Object> literals) {
        super(id, "dec_var", literals);
    }

    @Override
    public String label() {
        return "变量自减";
    }

    @Override
    public String execute(NodeContext ctx) {
        String name = ctx.inputString(this, "name", null);
        if (name == null || name.isBlank()) {
            throw new NodeExecException("E_EMPTY_VAR_NAME", "变量名不得为空", Map.of("port", "name"));
        }
        double n = ctx.numVar(name) - 1;
        ctx.setVar(name, n);
        ctx.setOutput(id, "value", n);
        return "out";
    }
}