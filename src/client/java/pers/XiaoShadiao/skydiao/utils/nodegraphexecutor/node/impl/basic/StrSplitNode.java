package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.basic;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory.AbstractNodeFactory;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.NodeContext;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 分割文本：按分隔符把文本拆成列表；分隔符留空时按单个字符拆分。 */
public final class StrSplitNode extends AbstractNode {

    public static final AbstractNodeFactory<StrSplitNode> FACTORY = new AbstractNodeFactory<>() {
        @Override
        public String getType() {
            return "str_split";
        }

        @Override
        public StrSplitNode createNode(String id, Map<String, Object> literals) {
            return new StrSplitNode(id, literals);
        }
    };

    private StrSplitNode(String id, Map<String, Object> literals) {
        super(id, "str_split", literals);
    }

    @Override
    public String label() {
        return "分割文本";
    }

    @Override
    public String execute(NodeContext ctx) {
        String text = ctx.inputString(this, "text", "");
        String sep = ctx.inputString(this, "sep", "");

        List<String> parts;
        if (sep.isEmpty()) {
            // 空分隔符按单个字符拆：网页端 "abc".split("") 得 [a, b, c]，
            // 而 Java 的 split("", -1) 会多带一个末尾空段，所以这里用默认 limit；
            // 空文本另算：网页端得空列表，Java 会给出 [""]
            parts = text.isEmpty() ? List.of() : Arrays.asList(text.split(""));
        } else {
            // Pattern.quote 防止分隔符里的正则元字符被当成正则；
            // limit = -1 保留末尾空段，与网页端 split 的行为一致
            parts = Arrays.asList(text.split(Pattern.quote(sep), -1));
        }
        ctx.setOutput(id, "value", parts);
        return "out";
    }
}
