package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 开头匹配：text.startsWith(prefix)，结果为布尔值。 */
public final class StrStartsWithNode extends AbstractNode {

    public static final AbstractNodeFactory<StrStartsWithNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "str_starts_with";
        }

        @Override
        public StrStartsWithNode createNode(String id, Map<String, Object> literals) {
            return new StrStartsWithNode(id, literals);
        }
    };

    private StrStartsWithNode(String id, Map<String, Object> literals) {
        super(id, "str_starts_with", literals);
    }

    @Override
    public String label() {
        return "开头匹配";
    }

    @Override
    public String execute(NodeContext ctx) {
        String text = ctx.inputString(this, "text", "");
        String prefix = ctx.inputString(this, "prefix", "");
        ctx.setOutput(id, "value", text.startsWith(prefix));
        return "out";
    }
}
