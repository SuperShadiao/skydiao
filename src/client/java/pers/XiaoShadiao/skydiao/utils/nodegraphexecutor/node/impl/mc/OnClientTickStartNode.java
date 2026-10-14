package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.mc;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

public class OnClientTickStartNode extends AbstractNode {

    public static final AbstractNodeFactory<OnClientTickStartNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "on_tick_start";
        }

        @Override
        public OnClientTickStartNode createNode(String id, Map<String, Object> literals) {
            return new OnClientTickStartNode(id, literals);
        }
    };

    protected OnClientTickStartNode(String id, Map<String, Object> literals) {
        super(id, FACTORY.getType(), literals);
    }

    @Override
    public String execute(NodeContext ctx) {
        return "out";
    }

    @Override
    public boolean isEntry() {
        return true;
    }

    @Override
    public String label() {
        return "每刻执行";
    }

}
