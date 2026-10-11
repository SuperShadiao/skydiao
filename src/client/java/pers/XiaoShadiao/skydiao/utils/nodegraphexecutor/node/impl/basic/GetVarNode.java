package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.executor.NodeExecException;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 读取变量：取变量表里的值并写出。 */
public final class GetVarNode extends AbstractNode {

    public static final AbstractNodeFactory<GetVarNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "get_var";
        }

        @Override
        public GetVarNode createNode(String id, Map<String, Object> literals) {
            return new GetVarNode(id, literals);
        }
    };

    private GetVarNode(String id, Map<String, Object> literals) {
        super(id, "get_var", literals);
    }

    @Override
    public String label() {
        return "读取变量";
    }

    @Override
    public String execute(NodeContext ctx) {
        String name = ctx.inputString(this, "name", null);
        if (name == null || name.isBlank()) {
            throw new NodeExecException("E_EMPTY_VAR_NAME", "变量名不得为空", Map.of("port", "name"));
        }
        ctx.setOutput(id, "value", ctx.getVar(name));
        return "out";
    }
}