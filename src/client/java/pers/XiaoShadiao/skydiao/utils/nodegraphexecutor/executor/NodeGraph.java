package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.executor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点图：节点 + 边。可由网页端导出的 JSON 直接构造。
 *
 * 导出的 JSON 结构（与 nodegraph.html 的 nodes/edges 一致）：
 * <pre>
 * {
 *   "nodes": [ { "id": "n1", "type": "start", "x": 40, "y": 240, "literals": {} } ],
 *   "edges": [ { "id": "e1", "kind": "exec",
 *                "from": { "node": "n1", "port": "out" },
 *                "to":   { "node": "n2", "port": "in"  } } ]
 * }
 * </pre>
 */
public final class NodeGraph {

    /** 一条边。kind 为 "exec"（执行流）或 "value"（值传递）。 */
    public static final class Edge {
        public final String kind;
        public final String fromNode, fromPort;
        public final String toNode, toPort;

        public Edge(String kind, String fromNode, String fromPort, String toNode, String toPort) {
            this.kind = kind;
            this.fromNode = fromNode;
            this.fromPort = fromPort;
            this.toNode = toNode;
            this.toPort = toPort;
        }
    }

    private final Map<String, AbstractNode> nodes = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();

    // ---------- 构造 ----------

    public void addNode(AbstractNode node) {
        nodes.put(node.id, node);
    }

    public void addEdge(Edge edge) {
        edges.add(edge);
    }

    /**
     * 解析网页端导出的图。
     * 遇到注册表里没有的类型会直接抛 {@link NodeExecException}（E_UNKNOWN_TYPE），
     * 而不是像网页端那样静默丢弃 —— 宿主执行器宁可失败也不要跑错。
     */
    public static NodeGraph fromJson(JsonObject root) {
        NodeGraph graph = new NodeGraph();

        JsonArray nodeArr = root.getAsJsonArray("nodes");
        if (nodeArr != null) {
            for (JsonElement el : nodeArr) {
                JsonObject o = el.getAsJsonObject();
                String id = asString(o.get("id"));
                String type = asString(o.get("type"));
                Map<String, Object> literals = new LinkedHashMap<>();
                JsonObject lit = o.getAsJsonObject("literals");
                if (lit != null) {
                    for (Map.Entry<String, JsonElement> e : lit.entrySet()) {
                        literals.put(e.getKey(), toJava(e.getValue()));
                    }
                }
                graph.addNode(NodeGraphFactoryRegistry.create(type, id, literals));
            }
        }

        JsonArray edgeArr = root.getAsJsonArray("edges");
        if (edgeArr != null) {
            for (JsonElement el : edgeArr) {
                JsonObject o = el.getAsJsonObject();
                JsonObject from = o.getAsJsonObject("from");
                JsonObject to = o.getAsJsonObject("to");
                if (from == null || to == null) continue;
                String fromNode = asString(from.get("node"));
                String toNode = asString(to.get("node"));
                if (!graph.nodes.containsKey(fromNode) || !graph.nodes.containsKey(toNode)) continue;
                graph.addEdge(new Edge(asString(o.get("kind")), fromNode, asString(from.get("port")),
                        toNode, asString(to.get("port"))));
            }
        }
        return graph;
    }

    // ---------- 查询 ----------

    public AbstractNode node(String id) {
        return nodes.get(id);
    }

    public Collection<AbstractNode> nodes() {
        return nodes.values();
    }

    /** 第一个入口节点（start / 事件）；没有则返回 null */
    public AbstractNode entry() {
        List<AbstractNode> list = entries();
        return list.isEmpty() ? null : list.get(0);
    }

    /** 所有入口节点（isEntry() 为 true），按声明顺序 */
    public List<AbstractNode> entries() {
        List<AbstractNode> list = new ArrayList<>();
        for (AbstractNode n : nodes.values()) {
            if (n.isEntry()) list.add(n);
        }
        return list;
    }

    /** 所有 type 匹配的入口节点；用于按事件类型触发 */
    public List<AbstractNode> entries(String type) {
        List<AbstractNode> list = new ArrayList<>();
        for (AbstractNode n : nodes.values()) {
            if (n.isEntry() && n.type.equals(type)) list.add(n);
        }
        return list;
    }

    /** 从某节点的某个 exec 输出端口出发，找到下游节点 */
    public AbstractNode nextExec(String nodeId, String portId) {
        for (Edge e : edges) {
            if ("exec".equals(e.kind) && e.fromNode.equals(nodeId) && e.fromPort.equals(portId)) {
                return nodes.get(e.toNode);
            }
        }
        return null;
    }

    /** 指向 (nodeId, portId) 的那条值连线，没有则 null */
    public Edge valueEdgeInto(String nodeId, String portId) {
        for (Edge e : edges) {
            if ("value".equals(e.kind) && e.toNode.equals(nodeId) && e.toPort.equals(portId)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 执行一个节点，并保证异常带上真正抛错的节点 id。
     * 值连线会间接求值到上游节点，所以“是谁抛的”必须在这里盖章，而不是靠调用方猜。
     */
    public String runNode(AbstractNode node, NodeContext ctx) {
        try {
            return node.execute(ctx);
        } catch (NodeExecException e) {
            e.stampNode(node.id);
            throw e;
        } catch (RuntimeException e) {
            NodeExecException wrapped = new NodeExecException(
                    "E_RUNTIME", String.valueOf(e.getMessage()), Map.of());
            wrapped.stampNode(node.id);
            throw wrapped;
        }
    }

    // ---------- JSON 辅助 ----------

    private static Object toJava(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonPrimitive()) {
            JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) return p.getAsNumber();
            return p.getAsString();
        }
        return el.toString();
    }

    private static String asString(JsonElement el) {
        return el == null || el.isJsonNull() ? null : el.getAsString();
    }
}