package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 字符串替换：把 text 里所有 from 换成 to；from 为空时原样返回。 */
public final class StrReplaceNode extends AbstractNode {

    public static final AbstractNodeFactory<StrReplaceNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "str_replace";
        }

        @Override
        public StrReplaceNode createNode(String id, Map<String, Object> literals) {
            return new StrReplaceNode(id, literals);
        }
    };

    private StrReplaceNode(String id, Map<String, Object> literals) {
        super(id, "str_replace", literals);
    }

    @Override
    public String label() {
        return "替换";
    }

    @Override
    public String execute(NodeContext ctx) {
        String text = ctx.inputString(this, "text", "");
        String from = ctx.inputString(this, "from", "");
        String to = ctx.inputString(this, "to", "");
        ctx.setOutput(id, "value", from.isEmpty() ? text : text.replace(from, to));
        return "out";
    }
}