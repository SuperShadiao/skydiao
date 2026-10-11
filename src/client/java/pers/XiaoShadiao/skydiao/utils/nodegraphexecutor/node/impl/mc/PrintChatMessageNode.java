package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.mc;

import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

public class PrintChatMessageNode extends AbstractNode {

    public static final AbstractNodeFactory<PrintChatMessageNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "print_chat_message";
        }

        @Override
        public PrintChatMessageNode createNode(String id, Map<String, Object> literals) {
            return new PrintChatMessageNode(id, literals);
        }
    };

    protected PrintChatMessageNode(String id, Map<String, Object> literals) {
        super(id, FACTORY.getType(), literals);
    }

    @Override
    public String execute(NodeContext ctx) {
        String msg = ctx.inputString(this, "msg", null);
        if(msg != null) {
            ToolList.printChatMessage(Component.literal(msg));
        }
        return "out";
    }

}
