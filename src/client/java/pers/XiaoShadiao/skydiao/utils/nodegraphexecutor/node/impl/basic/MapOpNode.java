package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 映射操作：6 个操作共用一个类（键统一为文本）。
 *
 * <p>单一对象机制：修改型操作（PUT / REMOVE / CLEAR）直接改传进来的那个映射并原样输出同一个对象；
 * 只读操作（GET / HAS_KEY）不复制也不修改；NEW 新建空映射。
 */
public final class MapOpNode extends AbstractNode {

    public enum Op { NEW, PUT, GET, HAS_KEY, REMOVE, CLEAR }

    private final Op op;

    private static AbstractNodeFactory<MapOpNode> factory(String type, Op op) {
        return new AbstractNodeFactory<>() {
            @Override
            public String getType() {
                return type;
            }

            @Override
            public MapOpNode createNode(String id, Map<String, Object> literals) {
                return new MapOpNode(id, type, literals, op);
            }
        };
    }

    public static final AbstractNodeFactory<MapOpNode> NEW = factory("map_new", Op.NEW);
    public static final AbstractNodeFactory<MapOpNode> PUT = factory("map_put", Op.PUT);
    public static final AbstractNodeFactory<MapOpNode> GET = factory("map_get", Op.GET);
    public static final AbstractNodeFactory<MapOpNode> HAS_KEY = factory("map_has_key", Op.HAS_KEY);
    public static final AbstractNodeFactory<MapOpNode> REMOVE = factory("map_remove", Op.REMOVE);
    public static final AbstractNodeFactory<MapOpNode> CLEAR = factory("map_clear", Op.CLEAR);

    private MapOpNode(String id, String type, Map<String, Object> literals, Op op) {
        super(id, type, literals);
        this.op = op;
    }

    @Override
    public String label() {
        return switch (op) {
            case NEW -> "创建空映射";
            case PUT -> "设置映射键值";
            case GET -> "键取映射值";
            case HAS_KEY -> "映射是否包含键";
            case REMOVE -> "移除映射键";
            case CLEAR -> "移除映射所有键值";
        };
    }

    @Override
    public String execute(NodeContext ctx) {
        switch (op) {
            case NEW -> ctx.setOutput(id, "value", new LinkedHashMap<String, Object>());
            case PUT -> {
                Map<String, Object> map = asMap(ctx.input(this, "map", null));
                map.put(ctx.inputString(this, "key", ""), ctx.input(this, "value", null));
                ctx.setOutput(id, "value", map);
            }
            case GET -> ctx.setOutput(id, "value",
                    asMap(ctx.input(this, "map", null)).get(ctx.inputString(this, "key", "")));
            case HAS_KEY -> ctx.setOutput(id, "value",
                    asMap(ctx.input(this, "map", null)).containsKey(ctx.inputString(this, "key", "")));
            case REMOVE -> {
                Map<String, Object> map = asMap(ctx.input(this, "map", null));
                map.remove(ctx.inputString(this, "key", ""));
                ctx.setOutput(id, "value", map);
            }
            case CLEAR -> {
                // 与其它修改型操作统一：直接清空传进来的那个映射并原样输出
                Map<String, Object> map = asMap(ctx.input(this, "map", null));
                map.clear();
                ctx.setOutput(id, "value", map);
            }
        }
        return "out";
    }

    /** 单一对象机制：传进来已经是映射就直接用这个映射本身，不是才新建空映射，免得空指针 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object v) {
        if (v instanceof Map<?, ?>) return (Map<String, Object>) v;
        return new LinkedHashMap<>();
    }
}
