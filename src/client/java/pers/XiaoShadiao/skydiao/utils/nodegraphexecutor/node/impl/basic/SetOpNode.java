package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 集合操作：6 个操作共用一个类。
 *
 * <p>单一对象机制：修改型操作（ADD / REMOVE / CLEAR）直接改传进来的那个集合并原样输出同一个对象；
 * 只读操作（CONTAINS）不复制也不修改；NEW 新建空集合，TO_LIST 因结果类型不同输出新列表。
 */
public final class SetOpNode extends AbstractNode {

    public enum Op { NEW, ADD, CONTAINS, REMOVE, CLEAR, TO_LIST }

    private final Op op;

    private static AbstractNodeFactory<SetOpNode> factory(String type, Op op) {
        return new AbstractNodeFactory<>() {
            @Override
            public String getType() {
                return type;
            }

            @Override
            public SetOpNode createNode(String id, Map<String, Object> literals) {
                return new SetOpNode(id, type, literals, op);
            }
        };
    }

    public static final AbstractNodeFactory<SetOpNode> NEW = factory("set_new", Op.NEW);
    public static final AbstractNodeFactory<SetOpNode> ADD = factory("set_add", Op.ADD);
    public static final AbstractNodeFactory<SetOpNode> CONTAINS = factory("set_contains", Op.CONTAINS);
    public static final AbstractNodeFactory<SetOpNode> REMOVE = factory("set_remove", Op.REMOVE);
    public static final AbstractNodeFactory<SetOpNode> CLEAR = factory("set_clear", Op.CLEAR);
    public static final AbstractNodeFactory<SetOpNode> TO_LIST = factory("set_to_list", Op.TO_LIST);

    private SetOpNode(String id, String type, Map<String, Object> literals, Op op) {
        super(id, type, literals);
        this.op = op;
    }

    @Override
    public String label() {
        return switch (op) {
            case NEW -> "创建空集合";
            case ADD -> "添加集合元素";
            case CONTAINS -> "集合是否包含元素";
            case REMOVE -> "移除集合元素";
            case CLEAR -> "移除集合所有元素";
            case TO_LIST -> "集合转列表";
        };
    }

    @Override
    public String execute(NodeContext ctx) {
        switch (op) {
            case NEW -> ctx.setOutput(id, "value", new LinkedHashSet<>());
            case ADD -> {
                Set<Object> set = asSet(ctx.input(this, "set", null));
                Object value = ctx.input(this, "value", null);
                // 判重与 CONTAINS / REMOVE 一样按值相等，避免两边口径不一致
                boolean exists = false;
                for (Object v : set) {
                    if (ObjEqualsNode.looseEquals(v, value)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) set.add(value);
                ctx.setOutput(id, "value", set);
            }
            case CONTAINS -> {
                Set<Object> set = asSet(ctx.input(this, "set", null));
                Object target = ctx.input(this, "value", null);
                boolean found = false;
                for (Object v : set) {
                    if (ObjEqualsNode.looseEquals(v, target)) {
                        found = true;
                        break;
                    }
                }
                ctx.setOutput(id, "value", found);
            }
            case REMOVE -> {
                Set<Object> set = asSet(ctx.input(this, "set", null));
                Object target = ctx.input(this, "value", null);
                // 先复制一份再删，避免遍历时改动集合
                for (Object v : new ArrayList<>(set)) {
                    if (ObjEqualsNode.looseEquals(v, target)) set.remove(v);
                }
                ctx.setOutput(id, "value", set);
            }
            case CLEAR -> {
                // 与其它修改型操作统一：直接清空传进来的那个集合并原样输出
                Set<Object> set = asSet(ctx.input(this, "set", null));
                set.clear();
                ctx.setOutput(id, "value", set);
            }
            case TO_LIST -> {
                // 顺序与遍历集合一致（LinkedHashSet 保留加入顺序）
                ctx.setOutput(id, "value", new ArrayList<>(asSet(ctx.input(this, "set", null))));
            }
        }
        return "out";
    }

    /** 单一对象机制：传进来已经是集合就直接用这个集合本身，不是才新建空集合，免得空指针 */
    @SuppressWarnings("unchecked")
    private static Set<Object> asSet(Object v) {
        if (v instanceof Set<?>) return (Set<Object>) v;
        if (v instanceof Collection<?> c) return new LinkedHashSet<>(c);
        return new LinkedHashSet<>();
    }
}
