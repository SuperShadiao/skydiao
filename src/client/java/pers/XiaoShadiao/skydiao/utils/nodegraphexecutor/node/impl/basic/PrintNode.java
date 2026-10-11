package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/** 输出：把文本打印到日志。 */
public final class PrintNode extends AbstractNode {

    public static final AbstractNodeFactory<PrintNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "print";
        }

        @Override
        public PrintNode createNode(String id, Map<String, Object> literals) {
            return new PrintNode(id, literals);
        }
    };

    private PrintNode(String id, Map<String, Object> literals) {
        super(id, "print", literals);
    }

    @Override
    public String label() {
        return "输出";
    }

    @Override
    public String execute(NodeContext ctx) {
        ctx.print(fmt(ctx.input(this, "text", "Hello")));
        return "out";
    }

    private static String fmt(Object v) {
        return v == null ? "null" : String.valueOf(v);
    }
}