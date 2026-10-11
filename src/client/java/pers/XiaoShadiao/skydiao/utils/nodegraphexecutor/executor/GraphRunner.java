package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.executor;

import org.apache.logging.log4j.Logger;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 执行器：从入口节点出发沿 exec 边跑完整张图。
 * 行为对齐网页端 runGraph：不留等待、出错即停、记录执行轨迹。
 *
 * <p>日志走 log4j：节点 IO 输出用 info，可恢复的问题（无入口、步数超限）用 warn，
 * 执行出错用 error。
 */
public final class GraphRunner {

    /** 步数上限，防止死循环把线程拖死（对应网页的 guard > 500） */
    public static final int MAX_STEPS = 500;

    private final NodeGraph graph;
    private final Logger logger;
    private final List<String> trace = new ArrayList<>();

    public GraphRunner(NodeGraph graph, Logger logger) {
        this.graph = graph;
        this.logger = logger;
    }

    /** 成功执行完的节点 id 序列（不含出错节点） */
    public List<String> trace() {
        return List.copyOf(trace);
    }

    /**
     * 从这张图的第一个入口节点跑一次。
     *
     * @return 成功返回 null；失败返回可序列化的错误（{@link NodeGraphError#toJson()} 即可回放高亮）
     */
    public NodeGraphError run() {
        AbstractNode entry = graph.entry();
        if (entry == null) {
            logger.warn("没有入口节点（isEntry() 为 true 的节点），无法执行。");
            return new NodeGraphError("E_NO_ENTRY", "没有入口节点",
                    Map.of(), null, 0, List.of());
        }
        return runFrom(entry);
    }

    /**
     * 从指定节点开始跑。
     * 事件触发时按 type 挑出入口节点，就从它起跑；同一次运行共用一个变量表。
     */
    public NodeGraphError runFrom(AbstractNode start) {
        return runFrom(start, Map.of());
    }

    /**
     * 从指定节点开始跑，并带上本次触发的事件参数。
     *
     * @param params 事件参数，入口节点用 {@link NodeContext#param} 取出后写到输出端口
     */
    public NodeGraphError runFrom(AbstractNode start, Map<String, Object> params) {
        trace.clear();

        // 节点 IO 输出（print 等）走 info，诊断信息由本类按级别打印
        NodeContext ctx = new NodeContext(graph, logger::info, params);
        AbstractNode cur = start;
        int step = 0;

        while (cur != null) {
            if (++step > MAX_STEPS) {
                logger.warn("执行步数超过 {}，可能存在死循环，已中断。", MAX_STEPS);
                break;
            }
            String next;
            try {
                next = graph.runNode(cur, ctx);
            } catch (NodeExecException e) {
                return buildError(e, cur, step);
            }
            trace.add(cur.id);
            cur = next == null ? null : graph.nextExec(cur.id, next);
        }
        return null;
    }

    private NodeGraphError buildError(NodeExecException e, AbstractNode fallback, int step) {
        // 出错节点可能是被值连线间接求值到的上游节点
        AbstractNode origin = e.nodeId() != null ? graph.node(e.nodeId()) : null;
        if (origin == null) origin = fallback;

        NodeGraphError error = new NodeGraphError(e.code, e.getMessage(), e.detail,
                ref(origin), step, List.copyOf(trace));
        String label = origin != null ? origin.label() : e.code;
        logger.error("[{}] {}", label, e.getMessage());
        logger.error("错误详情(JSON): {}", error.toJson());
        return error;
    }

    private static NodeGraphError.NodeRef ref(AbstractNode node) {
        return node == null ? null : new NodeGraphError.NodeRef(node.id, node.type, node.label());
    }
}