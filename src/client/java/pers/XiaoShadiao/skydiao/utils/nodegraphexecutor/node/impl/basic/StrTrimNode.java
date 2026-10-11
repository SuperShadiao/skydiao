package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 字符串去首尾空：text.trim()。 */
public final class StrTrimNode extends AbstractNode {

    public static final AbstractNodeFactory<StrTrimNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "str_trim";
        }

        @Override
        public StrTrimNode createNode(String id, Map<String, Object> literals) {
            return new StrTrimNode(id, literals);
        }
    };

    private StrTrimNode(String id, Map<String, Object> literals) {
        super(id, "str_trim", literals);
    }

    @Override
    public String label() {
        return "去首尾空";
    }

    @Override
    public String execute(NodeContext ctx) {
        String text = ctx.inputString(this, "text", "");
        ctx.setOutput(id, "value", text.trim());
        return "out";
    }
}