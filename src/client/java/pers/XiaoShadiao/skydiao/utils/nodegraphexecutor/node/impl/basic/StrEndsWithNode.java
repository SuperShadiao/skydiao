package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 结尾匹配：text.endsWith(suffix)，结果为布尔值。 */
public final class StrEndsWithNode extends AbstractNode {

    public static final AbstractNodeFactory<StrEndsWithNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "str_ends_with";
        }

        @Override
        public StrEndsWithNode createNode(String id, Map<String, Object> literals) {
            return new StrEndsWithNode(id, literals);
        }
    };

    private StrEndsWithNode(String id, Map<String, Object> literals) {
        super(id, "str_ends_with", literals);
    }

    @Override
    public String label() {
        return "结尾匹配";
    }

    @Override
    public String execute(NodeContext ctx) {
        String text = ctx.inputString(this, "text", "");
        String suffix = ctx.inputString(this, "suffix", "");
        ctx.setOutput(id, "value", text.endsWith(suffix));
        return "out";
    }
}
