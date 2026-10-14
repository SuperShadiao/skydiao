package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.mc;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

public class OnLevelChangeNode extends AbstractNode {

    public static final AbstractNodeFactory<OnLevelChangeNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "on_level_change";
        }

        @Override
        public OnLevelChangeNode createNode(String id, Map<String, Object> literals) {
            return new OnLevelChangeNode(id, getType(), literals);
        }
    };

    protected OnLevelChangeNode(String id, String type, Map<String, Object> literals) {
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
        return "当世界变化时";
    }

}
