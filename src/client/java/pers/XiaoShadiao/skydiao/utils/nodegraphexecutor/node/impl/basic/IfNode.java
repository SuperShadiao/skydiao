package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 如果判断：按条件真假走「是」/「否」分支。 */
public final class IfNode extends AbstractNode {

    public static final AbstractNodeFactory<IfNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "if";
        }

        @Override
        public IfNode createNode(String id, Map<String, Object> literals) {
            return new IfNode(id, literals);
        }
    };

    private IfNode(String id, Map<String, Object> literals) {
        super(id, "if", literals);
    }

    @Override
    public String label() {
        return "如果判断";
    }

    @Override
    public String execute(NodeContext ctx) {
        boolean cond = ctx.inputBool(this, "cond", false);
        return cond ? "true" : "false";
    }
}