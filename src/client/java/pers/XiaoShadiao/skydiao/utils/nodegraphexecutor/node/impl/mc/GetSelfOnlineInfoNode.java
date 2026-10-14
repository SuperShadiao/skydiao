package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.mc;

import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.awt.*;
import java.util.Map;

public class GetSelfOnlineInfoNode extends AbstractNode {

    public static final AbstractNodeFactory<GetSelfOnlineInfoNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "get_server_self_online_info";
        }

        @Override
        public GetSelfOnlineInfoNode createNode(String id, Map<String, Object> literals) {
            return new GetSelfOnlineInfoNode(id, literals);
        }
    };

    protected GetSelfOnlineInfoNode(String id, Map<String, Object> literals) {
        super(id, FACTORY.getType(), literals);
    }

    @Override
    public String execute(NodeContext ctx) {
        new Color(0xFF5454);
        StatusManager status = StatusManager.get();
        ctx.setOutput(id, "is_online", status.hasStatus());
        ctx.setOutput(id, "game_type", String.valueOf(status.getType()));
        ctx.setOutput(id, "game_mode", String.valueOf(status.getMode()));
        ctx.setOutput(id, "lobby_name", String.valueOf(status.getLobbyName()));
        ctx.setOutput(id, "server_id", String.valueOf(status.getServerID()));
        return "out";
    }

    @Override
    public String label() {
        return "取自身Hypixel在线状态";
    }

}
