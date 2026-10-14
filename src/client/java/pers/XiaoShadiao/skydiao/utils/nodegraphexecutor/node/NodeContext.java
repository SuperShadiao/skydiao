package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.executor.NodeGraph;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 节点执行上下文，对应网页端的 ctx：
 * 变量表 {@code vars}、端口输出缓存 {@code outputs}，以及按值连线取输入的能力。
 */
public final class NodeContext {

    /** 值连线递归求值深度上限，防止环形连边把栈打爆（对应网页的 ctx._depth > 40） */
    private static final int MAX_DEPTH = 40;

    /** 全局变量表：整个应用实例共享（进程内唯一），跨节点图、跨触发保留 */
    private static final Map<String, Object> GLOBAL_VARS = new ConcurrentHashMap<>();

    private final NodeGraph graph;
    private final Consumer<String> printer;
    private final Map<String, Object> vars = new HashMap<>();
    private final Map<String, Object> outputs = new HashMap<>(); // "nodeId:portId" -> 值
    private final Map<String, Object> params;                    // 本次触发的事件参数
    private int depth;

    public NodeContext(NodeGraph graph, Consumer<String> printer) {
        this(graph, printer, Map.of());
    }

    /**
     * @param params 触发本次运行的事件参数（如聊天消息），由入口节点取出后写到自己的输出端口
     */
    public NodeContext(NodeGraph graph, Consumer<String> printer, Map<String, Object> params) {
        this.graph = graph;
        this.printer = printer;
        this.params = params == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    // ---------- 事件参数 ----------

    /** 触发本次运行的事件参数（如 on_chat_message 的 "msg"）；没有则返回 def */
    public Object param(String name, Object def) {
        Object v = params.get(name);
        return v == null ? def : v;
    }

    public NodeGraph graph() {
        return graph;
    }

    // ---------- 变量 ----------

    public Object getVar(String name) {
        return vars.get(name);
    }

    public void setVar(String name, Object value) {
        vars.put(name, value);
    }

    /** 变量按数字读取，缺省当 0（对应网页的 Number(ctx.vars[name] || 0)） */
    public double numVar(String name) {
        return asDouble(vars.get(name), 0);
    }

    // ---------- 作用域变量 ----------

    /** 节点图作用域变量表：整张图共享，跨触发保留，图重载时丢弃 */
    public Map<String, Object> graphVars() {
        return graph.vars();
    }

    /** 全局作用域变量表：整个应用实例共享，跨节点图、跨触发保留（进程重启才清空） */
    public static Map<String, Object> globalVars() {
        return GLOBAL_VARS;
    }

    /** 按数字读取任意变量表里的值，缺省当 0 */
    public static double numOf(Map<String, Object> table, String name) {
        return asDouble(table == null ? null : table.get(name), 0);
    }

    // ---------- 输入 ----------

    /** 读端口原始值：值连线优先，其次字面量，最后默认值 */
    public Object input(AbstractNode node, String portId, Object def) {
        NodeGraph.Edge e = graph.valueEdgeInto(node.id, portId);
        if (e != null) {
            Object v = output(e.fromNode, e.fromPort);
            return v == null ? def : v;
        }
        Object raw = node.literal(portId);
        return raw == null ? def : raw;
    }

    public double inputNumber(AbstractNode node, String portId, double def) {
        return asDouble(input(node, portId, def), def);
    }

    public int inputInt(AbstractNode node, String portId, int def) {
        return (int) asDouble(input(node, portId, def), def);
    }

    public String inputString(AbstractNode node, String portId, String def) {
        Object v = input(node, portId, def);
        return v == null ? def : String.valueOf(v);
    }

    public boolean inputBool(AbstractNode node, String portId, boolean def) {
        Object v = input(node, portId, def);
        if (v instanceof Boolean b) return b;
        if (v == null) return def;
        return "true".equals(String.valueOf(v));
    }

    // ---------- 输出 ----------

    public void setOutput(String nodeId, String portId, Object value) {
        outputs.put(nodeId + ":" + portId, value);
    }

    /** 取某节点某输出端口的值；没算过就先把那个节点算出来（可能是上游） */
    public Object output(String nodeId, String portId) {
        String key = nodeId + ":" + portId;
        if (outputs.containsKey(key)) return outputs.get(key);
        if (depth >= MAX_DEPTH) return null;
        AbstractNode n = graph.node(nodeId);
        if (n == null) return null;
        depth++;
        try {
            graph.runNode(n, this);
        } finally {
            depth--;
        }
        return outputs.get(key);
    }

    // ---------- 日志 ----------

    public void print(Object value) {
        if (printer != null) {
            printer.accept(value == null ? "null" : String.valueOf(value));
        }
    }

    private static double asDouble(Object v, double def) {
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return def;
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}