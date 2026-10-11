package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.mc;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/**
 * 入口节点：收到聊天消息时触发。
 *
 * <p>触发方（{@code NodeGraphTriggerListener}）把消息放进事件参数，本节点把它写到输出端口
 * {@link #PARAM_MSG}，下游用值连线读取即可拿到聊天内容。
 */
public class OnChatMessageNode extends AbstractNode {

    /** 事件参数名，同时也是输出端口 id（与 skydiao_extensions.json 里的 out 端口一致） */
    public static final String PARAM_MSG = "msg";

    public static final AbstractNodeFactory<OnChatMessageNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "on_chat_message";
        }

        @Override
        public OnChatMessageNode createNode(String id, Map<String, Object> literals) {
            return new OnChatMessageNode(id, literals);
        }
    };

    protected OnChatMessageNode(String id, Map<String, Object> literals) {
        super(id, FACTORY.getType(), literals);
    }

    @Override
    public String label() {
        return "收到聊天";
    }

    @Override
    public boolean isEntry() {
        return true;
    }

    @Override
    public String execute(NodeContext ctx) {
        // 把触发方带进来的聊天消息写到输出端口，供下游用值连线读取
        ctx.setOutput(id, PARAM_MSG, ctx.param(PARAM_MSG, ""));
        return "out";
    }
}
