package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 逻辑取反：true -> false，false -> true。 */
public final class LogicNotNode extends AbstractNode {

    public static final AbstractNodeFactory<LogicNotNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "logic_not";
        }

        @Override
        public LogicNotNode createNode(String id, Map<String, Object> literals) {
            return new LogicNotNode(id, literals);
        }
    };

    private LogicNotNode(String id, Map<String, Object> literals) {
        super(id, "logic_not", literals);
    }

    @Override
    public String label() {
        return "取反";
    }

    @Override
    public String execute(NodeContext ctx) {
        boolean cond = ctx.inputBool(this, "cond", false);
        ctx.setOutput(id, "value", !cond);
        return "out";
    }
}