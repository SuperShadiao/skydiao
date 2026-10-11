package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点基类。子类只写自己的执行逻辑，端口/外观/文案仍由扩展包
 * （tools/nodegraph_extensions/default.json）声明，两边靠 {@code type} 对上。
 */
public abstract class AbstractNode {

    public final String id, type;

    /** 端口 id -> 字面量（来自图 JSON 的 literals）；被值连线覆盖时以连线为准 */
    protected final Map<String, Object> literals;

    protected AbstractNode(String id, String type, Map<String, Object> literals) {
        this.id = id;
        this.type = type;
        this.literals = literals == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(literals));
    }

    /** 取端口字面量，没有则返回 null */
    public Object literal(String portId) {
        return literals.get(portId);
    }

    /** 取端口字面量，没有则返回默认值 */
    public Object literal(String portId, Object def) {
        Object v = literals.get(portId);
        return v == null ? def : v;
    }

    /** 人类可读名，仅用于日志与错误上报 */
    public String label() {
        return type;
    }

    /** 执行本节点：输出通过 ctx.setOutput 写入，返回下一个 exec 输出端口 id；null 表示结束 */
    public abstract String execute(NodeContext ctx);

    /** 入口节点（start / 事件），对应扩展里的 execIn === false */
    public boolean isEntry() {
        return false;
    }
}