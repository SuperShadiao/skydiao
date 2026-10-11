package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.executor.GraphRunner;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.executor.NodeGraph;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.executor.NodeGraphError;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/**
 * 节点图管理器：统一负责「加载多个节点图」与「按事件触发执行」。
 *
 * <p>用法：
 * <pre>
 * NodeGraphManager.INSTANCE.loadFolder();
 * // 事件回调里：
 * NodeGraphManager.INSTANCE.triggerAllStartNode("on_tick_start");
 * </pre>
 *
 * <p>图按「名字」存放，同名图会被替换（重复保存不会堆积）。
 * <p>日志走 log4j：坏图用 warn 打印异常后跳过，执行出错用 error 打印。
 * <p>注册 {@link #setErrorListener} 后，每次执行出错都会回调，WebSocket 桥据此把错误推给网页。
 */
public final class NodeGraphManager {

    private static final Logger DEFAULT_LOG = LogManager.getLogger("XSD NodeGraph");
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** 节点图与扩展包共用的根目录：config/小沙雕_config/skydiao/nodeGraphs */
    private static final File folder = ConfigManager.getCustomConfigFileName("nodeGraphs");
    /** 节点图目录：&lt;根&gt;/plugins */
    public static final File graphsFolder = new File(folder, "plugins");
    /** 扩展包目录：&lt;根&gt;/extensions */
    public static final File extensionsFolder = new File(folder, "extensions");

    public static final File defaultExtensionFile = new File(extensionsFolder, "skydiao_extensions.json");

    static {
        graphsFolder.mkdirs();
        extensionsFolder.mkdirs();
    }

    public static final NodeGraphManager INSTANCE = new NodeGraphManager();

    /** 名字 -> 图。同名（或同名文件）会替换旧的那张 */
    private final Map<String, NodeGraph> graphs = new LinkedHashMap<>();
    private volatile Logger logger = DEFAULT_LOG;
    private volatile Consumer<NodeGraphError> errorListener;

    public NodeGraphManager() {
    }

    /** @param logger 日志出口（为 null 时沿用默认日志器） */
    public NodeGraphManager(Logger logger) {
        if (logger != null) {
            this.logger = logger;
        }
    }

    public void setLogger(Logger logger) {
        this.logger = logger != null ? logger : DEFAULT_LOG;
    }

    /** 注册「执行出错」回调（WebSocket 桥用它把错误推给网页）；传 null 取消 */
    public void setErrorListener(Consumer<NodeGraphError> listener) {
        this.errorListener = listener;
    }

    // ---------- 加载 ----------

    /** 从 JSON 文本加载一张图，返回加载好的图 */
    public synchronized NodeGraph loadJson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return put(nameOf(root, null), root);
    }

    /** 从文件加载一张图（UTF-8），图名取文件名 */
    public synchronized NodeGraph loadFile(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return put(stripExt(file.getFileName().toString()), root);
        }
    }

    /**
     * 加载 {@link #graphsFolder} 下所有 {@code .json} 节点图（不递归子目录）。
     * 单个文件坏掉时用 warn 打印异常并跳过，不影响其它图。
     *
     * @return 成功加载的张数
     */
    public synchronized int loadFolder() throws IOException {
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(graphsFolder.toPath(), "*.json")) {
            for (Path file : stream) {
                try {
                    loadFile(file);
                    count++;
                } catch (Exception e) {
                    logger.warn("加载节点图失败，已跳过：" + file.getFileName(), e);
                }
            }
        }
        return count;
    }

    /**
     * 重载 {@link #graphsFolder} 下的全部节点图：先清空内存里已有的图，再从磁盘重新读取。
     *
     * @return 成功加载的张数
     */
    public synchronized int reloadFolder() {
        graphs.clear();
        try {
            return loadFolder();
        } catch (IOException e) {
            logger.warn("重载节点图目录失败：" + graphsFolder, e);
            return 0;
        }
    }

    /**
     * 重载 {@link #graphsFolder} 下的单个节点图文件（同名会替换内存里的那一张）。
     *
     * @param name 图名，可带或不带 {@code .json}
     * @return 加载好的图；文件不存在或损坏时返回 {@code null}
     */
    public synchronized NodeGraph reloadGraph(String name) {
        Path file = graphsFolder.toPath().resolve(safeFileName(name));
        if (!Files.isRegularFile(file)) {
            logger.warn("重载节点图失败，文件不存在：" + file);
            return null;
        }
        try {
            return loadFile(file);
        } catch (Exception e) {
            logger.warn("重载节点图失败，已跳过：" + file.getFileName(), e);
            return null;
        }
    }

    /** 按名字新增/替换一张图并热载入内存（原始 JSON） */
    public synchronized NodeGraph upsert(String name, JsonObject root) {
        return put(name, root);
    }

    /**
     * 把网页端传来的节点图写成 {@code dir/<name>.json}，并热载入内存。
     * 同名文件会被覆盖，不会产生重复条目。
     *
     * @return 实际写入的文件
     */
    public synchronized Path saveGraph(String name, JsonObject root, Path dir) throws IOException {
        String fileName = safeFileName(name);
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            PRETTY.toJson(root, writer);
        }
        put(stripExt(fileName), root);
        return file;
    }

    /**
     * 删除 {@code graphsFolder/<name>.json} 并把它从内存里移除。
     *
     * @param name 图名，可带或不带 {@code .json}
     * @return 磁盘上确实存在该文件并已删除时 {@code true}；文件本就不存在时 {@code false}
     */
    public synchronized boolean removeGraph(String name) {
        String fileName = safeFileName(name);
        graphs.remove(stripExt(fileName));
        Path file = graphsFolder.toPath().resolve(fileName);
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.warn("删除节点图文件失败：" + file, e);
            return false;
        }
    }

    private NodeGraph put(String name, JsonObject root) {
        NodeGraph graph = NodeGraph.fromJson(root);
        graphs.put(name == null || name.isBlank() ? "graph" : name, graph);
        return graph;
    }

    public synchronized List<NodeGraph> graphs() {
        return List.copyOf(graphs.values());
    }

    public synchronized int size() {
        return graphs.size();
    }

    public synchronized void clear() {
        graphs.clear();
    }

    // ---------- 触发 ----------

    /**
     * 触发所有「type 匹配」的入口节点：
     * 每张图里 type 相同的入口节点都会各跑一遍，彼此独立。
     *
     * @param type 入口节点的类型，例如 "on_tick_start"
     * @return 出错列表；全部成功时是空列表
     */
    public List<NodeGraphError> triggerAllStartNode(String type) {
        return triggerAllStartNode(type, Map.of());
    }

    /**
     * 触发所有「type 匹配」的入口节点，并把事件参数交给入口节点。
     *
     * <p>入口节点（如 {@code on_chat_message}）用 {@code ctx.param("msg")} 取出参数、
     * 写到自己的输出端口，下游再用值连线读取，这样事件数据就流进了图里。
     *
     * @param type   入口节点的类型，例如 "on_chat_message"
     * @param params 本次事件参数，无参数时传 {@link Map#of()}
     * @return 出错列表；全部成功时是空列表
     */
    public List<NodeGraphError> triggerAllStartNode(String type, Map<String, Object> params) {
        List<NodeGraph> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(graphs.values());
        }

        List<NodeGraphError> errors = new ArrayList<>();
        for (NodeGraph graph : snapshot) {
            for (AbstractNode start : graph.entries(type)) {
                NodeGraphError error = new GraphRunner(graph, logger).runFrom(start, params);
                if (error != null) {
                    errors.add(error);
                    notifyError(error);
                }
            }
        }
        return errors;
    }

    /** 跑一遍某张图（从它的第一个入口节点起），一般用于「开始执行」类节点 */
    public NodeGraphError trigger(NodeGraph graph) {
        NodeGraphError error = new GraphRunner(graph, logger).run();
        if (error != null) {
            notifyError(error);
        }
        return error;
    }

    private void notifyError(NodeGraphError error) {
        Consumer<NodeGraphError> listener = errorListener;
        if (listener != null) {
            listener.accept(error);
        }
    }

    // ---------- 工具 ----------

    private static String nameOf(JsonObject root, String def) {
        if (root.has("name") && root.get("name").isJsonPrimitive()) {
            String n = root.get("name").getAsString();
            if (!n.isBlank()) {
                return n;
            }
        }
        return def;
    }

    private static String stripExt(String fileName) {
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }

    /** 只保留文件名，挡掉路径分隔符与非法字符，并补上 .json */
    private static String safeFileName(String name) {
        String base = name == null ? "" : name.trim();
        if (base.isEmpty()) {
            base = "main";
        }
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_").replace("..", "_");
        return base.endsWith(".json") ? base : base + ".json";
    }

    private static boolean inited = false;

    public void init() throws IOException {
        if(!inited) inited = true; else return;
        loadFolder();
        FileUtils.copyInputStreamToFile(
                Objects.requireNonNull(NodeGraphManager.class.getClassLoader().getResourceAsStream("skydiao_extensions.json")),
                defaultExtensionFile
        );
        LocalhostWSServer.INSTANCE.start();
    }
}