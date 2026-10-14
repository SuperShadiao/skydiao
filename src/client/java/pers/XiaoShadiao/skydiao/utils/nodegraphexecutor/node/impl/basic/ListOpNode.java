package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 列表操作：7 个操作共用一个类，靠工厂区分 type。
 *
 * <p>单一对象机制：修改型操作（ADD / REMOVE_AT / CLEAR）直接改传进来的那个列表并原样输出同一个对象；
 * 只读操作（INDEX_OF / GET）不复制也不修改；NEW 新建空列表，TO_SET 因结果类型不同输出新集合。
 */
public final class ListOpNode extends AbstractNode {

    public enum Op { NEW, ADD, INDEX_OF, GET, REMOVE_AT, CLEAR, TO_SET }

    private final Op op;

    private static AbstractNodeFactory<ListOpNode> factory(String type, Op op) {
        return new AbstractNodeFactory<>() {
            @Override
            public String getType() {
                return type;
            }

            @Override
            public ListOpNode createNode(String id, Map<String, Object> literals) {
                return new ListOpNode(id, type, literals, op);
            }
        };
    }

    public static final AbstractNodeFactory<ListOpNode> NEW = factory("list_new", Op.NEW);
    public static final AbstractNodeFactory<ListOpNode> ADD = factory("list_add", Op.ADD);
    public static final AbstractNodeFactory<ListOpNode> INDEX_OF = factory("list_index_of", Op.INDEX_OF);
    public static final AbstractNodeFactory<ListOpNode> GET = factory("list_get", Op.GET);
    public static final AbstractNodeFactory<ListOpNode> REMOVE_AT = factory("list_remove_at", Op.REMOVE_AT);
    public static final AbstractNodeFactory<ListOpNode> CLEAR = factory("list_clear", Op.CLEAR);
    public static final AbstractNodeFactory<ListOpNode> TO_SET = factory("list_to_set", Op.TO_SET);

    private ListOpNode(String id, String type, Map<String, Object> literals, Op op) {
        super(id, type, literals);
        this.op = op;
    }

    @Override
    public String label() {
        return switch (op) {
            case NEW -> "创建空列表";
            case ADD -> "添加列表元素";
            case INDEX_OF -> "元素取列表索引";
            case GET -> "索引取列表元素";
            case REMOVE_AT -> "移除列表索引";
            case CLEAR -> "移除列表所有元素";
            case TO_SET -> "列表转集合";
        };
    }

    @Override
    public String execute(NodeContext ctx) {
        switch (op) {
            case NEW -> ctx.setOutput(id, "value", new ArrayList<>());
            case ADD -> {
                List<Object> list = asList(ctx.input(this, "list", null));
                list.add(ctx.input(this, "value", null));
                ctx.setOutput(id, "value", list);
            }
            case INDEX_OF -> {
                List<Object> list = asList(ctx.input(this, "list", null));
                Object target = ctx.input(this, "value", null);
                int index = -1;
                for (int i = 0; i < list.size(); i++) {
                    if (ObjEqualsNode.looseEquals(list.get(i), target)) {
                        index = i;
                        break;
                    }
                }
                ctx.setOutput(id, "value", index);
            }
            case GET -> {
                List<Object> list = asList(ctx.input(this, "list", null));
                int i = ctx.inputInt(this, "index", 0);
                ctx.setOutput(id, "value", (i >= 0 && i < list.size()) ? list.get(i) : null);
            }
            case REMOVE_AT -> {
                List<Object> list = asList(ctx.input(this, "list", null));
                int i = ctx.inputInt(this, "index", 0);
                if (i >= 0 && i < list.size()) list.remove(i);
                ctx.setOutput(id, "value", list);
            }
            case CLEAR -> {
                // 与其它修改型操作统一：直接清空传进来的那个列表并原样输出
                List<Object> list = asList(ctx.input(this, "list", null));
                list.clear();
                ctx.setOutput(id, "value", list);
            }
            case TO_SET -> {
                Set<Object> set = new LinkedHashSet<>();
                // 去重口径与 set_add 一致：按值相等，重复的元素只留第一个
                for (Object v : asList(ctx.input(this, "list", null))) {
                    boolean exists = false;
                    for (Object s : set) {
                        if (ObjEqualsNode.looseEquals(s, v)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) set.add(v);
                }
                ctx.setOutput(id, "value", set);
            }
        }
        return "out";
    }

    /**
     * 单一对象机制：传进来已经是列表就直接用这个列表本身（改就是在改它），不是才新建空列表，免得空指针。
     * 不复制副本，避免同一列表连到不同端点时各分支看到的数据不一致，也省掉一次拷贝。
     */
    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object v) {
        if (v instanceof List<?>) return (List<Object>) v;
        if (v instanceof Collection<?> c) return new ArrayList<>(c);
        return new ArrayList<>();
    }
}
