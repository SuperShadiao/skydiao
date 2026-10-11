package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.executor.NodeExecException;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 修改变量：把值写入变量表。 */
public final class SetVarNode extends AbstractNode {

    public static final AbstractNodeFactory<SetVarNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "set_var";
        }

        @Override
        public SetVarNode createNode(String id, Map<String, Object> literals) {
            return new SetVarNode(id, literals);
        }
    };

    private SetVarNode(String id, Map<String, Object> literals) {
        super(id, "set_var", literals);
    }

    @Override
    public String label() {
        return "修改变量";
    }

    @Override
    public String execute(NodeContext ctx) {
        String name = ctx.inputString(this, "name", null);
        if (name == null || name.isBlank()) {
            throw new NodeExecException("E_EMPTY_VAR_NAME", "变量名不得为空", Map.of("port", "name"));
        }
        Object value = ctx.input(this, "value", "0");
        ctx.setVar(name, value);
        return "out";
    }
}