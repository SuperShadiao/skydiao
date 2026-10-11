package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.executor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点执行错误。
 * {@link #code} 与 {@link #detail} 都是可序列化的纯数据，宿主可以把整份错误转成 JSON
 * 再交给网页端高亮（格式见 {@link NodeGraphError}）。
 */
public class NodeExecException extends RuntimeException {

    public final String code; // 如 "E_BAD_OPERATOR"
    public final Map<String, Object> detail; // 如 {"port":"op","value":">=<"}

    /** 真正抛错的节点 id。值连线可能间接求值到上游节点，由运行器负责补齐。 */
    private String nodeId;

    public NodeExecException(String code, String message, Map<String, Object> detail) {
        super(message);
        this.code = code;
        this.detail = detail == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(detail));
    }

    /** 没有额外信息时，message 直接用 code */
    public NodeExecException(String code, Map<String, Object> detail) {
        this(code, code, detail);
    }

    public String nodeId() {
        return nodeId;
    }

    /** 只在第一次调用时生效：最内层（真正出错）的节点优先 */
    public void stampNode(String id) {
        if (this.nodeId == null) {
            this.nodeId = id;
        }
    }
}