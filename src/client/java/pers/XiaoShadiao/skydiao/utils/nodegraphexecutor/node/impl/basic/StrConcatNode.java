package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 字符串拼接：a + b。 */
public final class StrConcatNode extends AbstractNode {

    public static final AbstractNodeFactory<StrConcatNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "str_concat";
        }

        @Override
        public StrConcatNode createNode(String id, Map<String, Object> literals) {
            return new StrConcatNode(id, literals);
        }
    };

    private StrConcatNode(String id, Map<String, Object> literals) {
        super(id, "str_concat", literals);
    }

    @Override
    public String label() {
        return "拼接";
    }

    @Override
    public String execute(NodeContext ctx) {
        String a = ctx.inputString(this, "a", "");
        String b = ctx.inputString(this, "b", "");
        ctx.setOutput(id, "value", a + b);
        return "out";
    }
}