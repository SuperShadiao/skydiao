package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 是否包含：text.contains(sub)，结果为布尔值。 */
public final class StrContainsNode extends AbstractNode {

    public static final AbstractNodeFactory<StrContainsNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "str_contains";
        }

        @Override
        public StrContainsNode createNode(String id, Map<String, Object> literals) {
            return new StrContainsNode(id, literals);
        }
    };

    private StrContainsNode(String id, Map<String, Object> literals) {
        super(id, "str_contains", literals);
    }

    @Override
    public String label() {
        return "是否包含";
    }

    @Override
    public String execute(NodeContext ctx) {
        String text = ctx.inputString(this, "text", "");
        String sub = ctx.inputString(this, "sub", "");
        ctx.setOutput(id, "value", text.contains(sub));
        return "out";
    }
}