package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.executor.NodeExecException;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Map;

/**
 * 作用域变量节点：get / set / inc / dec 四个操作 × 两种作用域 = 8 个类型。
 * 实现完全相同，区别只在读写哪张变量表：
 * <ul>
 *   <li>{@code graph_*} —— 当前节点图（{@link NodeContext#graphVars()}），整张图共享，重载图时丢弃</li>
 *   <li>{@code global_*} —— 整个应用实例（{@link NodeContext#globalVars()}），跨图共享，进程重启前一直有效</li>
 * </ul>
 * 与 {@code get_var}/{@code set_var}/{@code inc_var}/{@code dec_var} 的区别：后者用的是本次运行的临时变量表
 * （{@link NodeContext#getVar(String)}），每次触发都从空开始。
 */
public final class ScopedVarNode extends AbstractNode {

    /** 变量表所在的作用域 */
    public enum Scope { GRAPH, GLOBAL }

    /** 对变量做的操作 */
    public enum Op { GET, SET, INC, DEC }

    // ---------- 工厂 ----------

    private static AbstractNodeFactory<ScopedVarNode> factory(String type, Scope scope, Op op) {
        return new AbstractNodeFactory<>() {
            @Override
            public String getType() {
                return type;
            }

            @Override
            public ScopedVarNode createNode(String id, Map<String, Object> literals) {
                return new ScopedVarNode(id, type, literals, scope, op);
            }
        };
    }

    /** 节点图作用域 */
    public static final AbstractNodeFactory<ScopedVarNode> GRAPH_GET = factory("graph_get_var", Scope.GRAPH, Op.GET);
    public static final AbstractNodeFactory<ScopedVarNode> GRAPH_SET = factory("graph_set_var", Scope.GRAPH, Op.SET);
    public static final AbstractNodeFactory<ScopedVarNode> GRAPH_INC = factory("graph_inc_var", Scope.GRAPH, Op.INC);
    public static final AbstractNodeFactory<ScopedVarNode> GRAPH_DEC = factory("graph_dec_var", Scope.GRAPH, Op.DEC);

    /** 全局作用域 */
    public static final AbstractNodeFactory<ScopedVarNode> GLOBAL_GET = factory("global_get_var", Scope.GLOBAL, Op.GET);
    public static final AbstractNodeFactory<ScopedVarNode> GLOBAL_SET = factory("global_set_var", Scope.GLOBAL, Op.SET);
    public static final AbstractNodeFactory<ScopedVarNode> GLOBAL_INC = factory("global_inc_var", Scope.GLOBAL, Op.INC);
    public static final AbstractNodeFactory<ScopedVarNode> GLOBAL_DEC = factory("global_dec_var", Scope.GLOBAL, Op.DEC);

    // ---------- 实例 ----------

    private final Scope scope;
    private final Op op;

    private ScopedVarNode(String id, String type, Map<String, Object> literals, Scope scope, Op op) {
        super(id, type, literals);
        this.scope = scope;
        this.op = op;
    }

    @Override
    public String label() {
        String s = scope == Scope.GRAPH ? "节点图变量" : "全局变量";
        return switch (op) {
            case GET -> "读取" + s;
            case SET -> "修改" + s;
            case INC -> s + "自增";
            case DEC -> s + "自减";
        };
    }

    @Override
    public String execute(NodeContext ctx) {
        String name = ctx.inputString(this, "name", null);
        if (name == null || name.isBlank()) {
            throw new NodeExecException("E_EMPTY_VAR_NAME", "变量名不得为空", Map.of("port", "name"));
        }
        Map<String, Object> table = scope == Scope.GRAPH ? ctx.graphVars() : NodeContext.globalVars();
        switch (op) {
            case GET -> ctx.setOutput(id, "value", table.get(name));
            case SET -> table.put(name, ctx.input(this, "value", "0"));
            case INC -> {
                double n = NodeContext.numOf(table, name) + 1;
                table.put(name, n);
                ctx.setOutput(id, "value", n);
            }
            case DEC -> {
                double n = NodeContext.numOf(table, name) - 1;
                table.put(name, n);
                ctx.setOutput(id, "value", n);
            }
        }
        return "out";
    }
}