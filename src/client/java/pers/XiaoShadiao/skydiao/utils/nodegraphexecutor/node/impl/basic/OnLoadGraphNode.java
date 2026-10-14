package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

public class OnLoadGraphNode extends AbstractNode {

    public static final AbstractNodeFactory<OnLoadGraphNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "on_nodegraph_load";
        }

        @Override
        public OnLoadGraphNode createNode(String id, Map<String, Object> literals) {
            return new OnLoadGraphNode(id, getType(), literals);
        }
    };

    protected OnLoadGraphNode(String id, String type, Map<String, Object> literals) {
        super(id, type, literals);
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
        return "当节点图被加载时";
    }

}
