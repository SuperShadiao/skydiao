package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.executor;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic.*;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.mc.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * type -> 工厂 的注册表。
 * 新增节点类型时，在这里加一行它的 FACTORY 即可（type 必须与扩展包里的 type 一致）。
 */
public final class NodeGraphFactoryRegistry {

    private static final List<AbstractNodeFactory<?>> FACTORIES = List.of(
            IfNode.FACTORY,
            GetVarNode.FACTORY,
            SetVarNode.FACTORY,
            IncVarNode.FACTORY,
            DecVarNode.FACTORY,
            // 作用域变量：节点图作用域 + 全局作用域，各 4 个操作
            ScopedVarNode.GRAPH_GET,
            ScopedVarNode.GRAPH_SET,
            ScopedVarNode.GRAPH_INC,
            ScopedVarNode.GRAPH_DEC,
            ScopedVarNode.GLOBAL_GET,
            ScopedVarNode.GLOBAL_SET,
            ScopedVarNode.GLOBAL_INC,
            ScopedVarNode.GLOBAL_DEC,
            MathAddNode.FACTORY,
            MathSubNode.FACTORY,
            MathMulNode.FACTORY,
            MathDivNode.FACTORY,
            MathModNode.FACTORY,
            MathCmpNode.FACTORY,
            MathIntTextNode.FACTORY,
            MathRoundTextNode.FACTORY,
            LogicNotNode.FACTORY,
            PrintNode.FACTORY,
            StrTrimNode.FACTORY,
            StrConcatNode.FACTORY,
            StrContainsNode.FACTORY,
            StrReplaceNode.FACTORY,
            StrStartsWithNode.FACTORY,
            StrEndsWithNode.FACTORY,
            StrSplitNode.FACTORY,
            // 对象比较
            ObjEqualsNode.FACTORY,
            ObjNullNode.FACTORY,
            ObjContentEmptyNode.FACTORY,
            // 容器：列表 / 集合 / 映射
            ListOpNode.NEW,
            ListOpNode.ADD,
            ListOpNode.INDEX_OF,
            ListOpNode.GET,
            ListOpNode.REMOVE_AT,
            ListOpNode.CLEAR,
            ListOpNode.TO_SET,
            SetOpNode.NEW,
            SetOpNode.ADD,
            SetOpNode.CONTAINS,
            SetOpNode.REMOVE,
            SetOpNode.CLEAR,
            SetOpNode.TO_LIST,
            MapOpNode.NEW,
            MapOpNode.PUT,
            MapOpNode.GET,
            MapOpNode.HAS_KEY,
            MapOpNode.REMOVE,
            MapOpNode.CLEAR,
            OnClientTickStartNode.FACTORY,
            OnChatMessageNode.FACTORY,
            PrintChatMessageNode.FACTORY,
            OnLoadGraphNode.FACTORY,
            OnLevelChangeNode.FACTORY,
            GetSelfOnlineInfoNode.FACTORY
    );

    private static final Map<String, AbstractNodeFactory<?>> TYPE_TO_FACTORY;

    static {
        Map<String, AbstractNodeFactory<?>> map = new HashMap<>();
        for (AbstractNodeFactory<?> factory : FACTORIES) {
            AbstractNodeFactory<?> prev = map.put(factory.getType(), factory);
            if (prev != null) {
                throw new IllegalStateException("节点类型重复注册：" + factory.getType());
            }
        }
        TYPE_TO_FACTORY = Map.copyOf(map);
    }

    private NodeGraphFactoryRegistry() {
    }

    public static Set<String> types() {
        return TYPE_TO_FACTORY.keySet();
    }

    public static AbstractNodeFactory<?> get(String type) {
        return TYPE_TO_FACTORY.get(type);
    }

    /** 按 type 造节点；类型没注册时抛 E_UNKNOWN_TYPE */
    public static AbstractNode create(String type, String id, Map<String, Object> literals) {
        AbstractNodeFactory<?> factory = TYPE_TO_FACTORY.get(type);
        if (factory == null) {
            throw new NodeExecException("E_UNKNOWN_TYPE",
                    "未知节点类型：「" + type + "」（节点 id=" + id + "），请确认对应扩展包已导入",
                    Map.of("type", type, "id", id));
        }
        return factory.createNode(id, literals);
    }
}