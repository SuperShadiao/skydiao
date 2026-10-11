package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.executor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;
import java.util.Map;

/**
 * 可序列化的运行错误。字段与网页端 toSerializableError 完全一致，
 * 目的就是让宿主程序把出错现场原样丢回编辑器高亮：
 *
 * <pre>
 * {
 *   "code": "E_BAD_OPERATOR",
 *   "message": "运算符不合法：「&gt;=&lt;」…",
 *   "detail": { "port": "op", "value": "&gt;=&lt;" },
 *   "node": { "id": "n4", "type": "math_cmp", "label": "比较" },
 *   "step": 4,
 *   "trace": ["n1", "n2", "n3"]
 * }
 * </pre>
 */
public final class NodeGraphError {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public final String code;
    public final String message;
    public final Map<String, Object> detail;
    public final NodeRef node;
    public final int step;
    public final List<String> trace;

    public static final class NodeRef {
        public final String id;
        public final String type;
        public final String label;

        public NodeRef(String id, String type, String label) {
            this.id = id;
            this.type = type;
            this.label = label;
        }
    }

    public NodeGraphError(String code, String message, Map<String, Object> detail,
                          NodeRef node, int step, List<String> trace) {
        this.code = code;
        this.message = message;
        this.detail = detail;
        this.node = node;
        this.step = step;
        this.trace = trace;
    }

    /** 序列化成上面那份 JSON，可直接写文件后由网页端「导入错误」回放 */
    public String toJson() {
        return GSON.toJson(this);
    }

    @Override
    public String toString() {
        return toJson();
    }
}