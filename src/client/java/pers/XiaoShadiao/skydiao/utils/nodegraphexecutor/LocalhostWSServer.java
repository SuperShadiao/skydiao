package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.executor.NodeGraphError;

import java.io.IOException;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

/**
 * 本地 WebSocket 桥：网页端节点图编辑器 ↔ 模组。只监听本机 {@code ws://127.0.0.1:841}。
 *
 * <p>消息统一为 UTF-8 JSON 文本，信封形如 {@code { "type": ..., "data": ... }}：
 *
 * <p><b>网页 → Java</b>
 * <ul>
 *   <li>{@code {"type":"hello"}} —— 握手，请把扩展包与图列表发过来</li>
 *   <li>{@code {"type":"save_graph","name":"main","data":{...}}} —— 保存节点图并热载入</li>
 *   <li>{@code {"type":"request_graph","name":"main"}} —— 按名字读回一张已有节点图</li>
 *   <li>{@code {"type":"delete_graph","name":"main"}} —— 删除磁盘上的节点图并移出内存</li>
 * </ul>
 *
 * <p><b>Java → 网页</b>
 * <ul>
 *   <li>{@code {"type":"extensions","data":[ {扩展包}, ... ]}} —— 磁盘上的扩展包，页面自动载入</li>
 *   <li>{@code {"type":"graphs","data":["main","..."]}} —— 已有节点图名</li>
 *   <li>{@code {"type":"saved","name":"main","file":"main.json"}} —— 保存成功回执</li>
 *   <li>{@code {"type":"load_graph","name":"main","data":{...}}} —— 读取回执（文件不存在时无 data）</li>
 *   <li>{@code {"type":"deleted","name":"main","existed":true}} —— 删除回执</li>
 *   <li>{@code {"type":"error","data":{ NodeGraphError }}} —— 执行出错，页面据此标红+轨迹高亮</li>
 * </ul>
 *
 * <p>目录统一取自 {@link NodeGraphManager}：根目录 {@code config/小沙雕_config/skydiao/nodeGraphs/}，
 * 节点图在其中的 {@code plugins/}，扩展包在其中的 {@code extensions/}。
 */
public final class LocalhostWSServer extends WebSocketServer {

    /** 单例：在客户端初始化里调用 {@code LocalhostWSServer.INSTANCE.start()} 即可开工 */
    public static final LocalhostWSServer INSTANCE = new LocalhostWSServer();

    private static final Logger log = LogManager.getLogger("XSD NodeGraph WS");
    private static final int PORT = 841;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private LocalhostWSServer() {
        super(new InetSocketAddress("127.0.0.1", PORT));
        setReuseAddr(true);
        setDaemon(true); // 不阻止游戏退出
        // 执行出错时，把可序列化的错误推给所有已连接的网页
        NodeGraphManager.INSTANCE.setErrorListener(this::pushError);
    }

    // ---------- 目录 ----------

    /** 节点图目录：config/小沙雕_config/skydiao/nodeGraphs/plugins */
    private static Path graphsDir() {
        return NodeGraphManager.graphsFolder.toPath();
    }

    /** 扩展包目录：config/小沙雕_config/skydiao/nodeGraphs/extensions */
    private static Path extensionsDir() {
        return NodeGraphManager.extensionsFolder.toPath();
    }

    // ---------- 生命周期 ----------

    @Override
    public void onStart() {
        log.info("节点图 WebSocket 已监听 ws://127.0.0.1:{}", PORT);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        log.info("网页已连接：{}", conn.getRemoteSocketAddress());
        conn.send(extensionsMessage());
        conn.send(graphsMessage());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        log.info("网页已断开：{}（{}）", conn.getRemoteSocketAddress(), reason);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        log.warn("WebSocket 出错" + (conn == null ? "" : "（" + conn.getRemoteSocketAddress() + "）"), ex);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        JsonObject msg;
        try {
            msg = JsonParser.parseString(message).getAsJsonObject();
        } catch (Exception e) {
            log.warn("收到无法解析的消息：{}", message);
            return;
        }
        String type = msg.has("type") ? msg.get("type").getAsString() : "";
        switch (type) {
            case "hello" -> {
                conn.send(extensionsMessage());
                conn.send(graphsMessage());
            }
            case "save_graph" -> handleSave(conn, msg);
            case "request_graph" -> handleRequestGraph(conn, msg);
            case "delete_graph" -> handleDelete(conn, msg);
            default -> log.warn("未知消息类型：{}", type);
        }
    }

    // ---------- 保存 ----------

    private void handleSave(WebSocket conn, JsonObject msg) {
        String name = msg.has("name") && !msg.get("name").getAsString().isBlank()
                ? msg.get("name").getAsString().trim()
                : "main";
        if (!msg.has("data") || !msg.get("data").isJsonObject()) {
            log.warn("save_graph 缺少 data 对象（name={}）", name);
            return;
        }
        JsonObject data = msg.getAsJsonObject("data");
        try {
            Path file = NodeGraphManager.INSTANCE.saveGraph(name, data, graphsDir());
            JsonObject reply = new JsonObject();
            reply.addProperty("type", "saved");
            reply.addProperty("name", name);
            reply.addProperty("file", file.getFileName().toString());
            conn.send(GSON.toJson(reply));
            log.info("已保存节点图「{}」→ {}，并已热载入。", name, file);
        } catch (Exception e) {
            log.warn("保存节点图失败：" + name, e);
        }
    }

    // ---------- 读取 ----------

    /** 网页按文件名请求一张已有节点图：读 graphsDir/<name>.json 并回发 */
    private void handleRequestGraph(WebSocket conn, JsonObject msg) {
        String name = msg.has("name") ? msg.get("name").getAsString().trim() : "";
        if (name.isEmpty()) {
            log.warn("request_graph 缺少 name");
            return;
        }
        String fileName = name.endsWith(".json") ? name : name + ".json";
        Path file = graphsDir().resolve(fileName);
        if (!Files.isRegularFile(file)) {
            log.warn("请求的节点图不存在：{}", file);
            // 回一个不带 data 的回执，网页端据此提示「没取到」
            JsonObject empty = new JsonObject();
            empty.addProperty("type", "load_graph");
            empty.addProperty("name", name);
            conn.send(GSON.toJson(empty));
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String base = file.getFileName().toString();
            JsonObject reply = new JsonObject();
            reply.addProperty("type", "load_graph");
            reply.addProperty("name", base.endsWith(".json") ? base.substring(0, base.length() - 5) : base);
            reply.add("data", JsonParser.parseReader(reader));
            conn.send(GSON.toJson(reply));
            log.info("已下发节点图「{}」给网页。", name);
        } catch (Exception e) {
            log.warn("读取节点图失败：" + name, e);
        }
    }

    // ---------- 删除 ----------

    /** 网页删除一张节点图：删掉 graphsDir/<name>.json，并从内存里移除该图 */
    private void handleDelete(WebSocket conn, JsonObject msg) {
        String name = msg.has("name") ? msg.get("name").getAsString().trim() : "";
        if (name.isEmpty()) {
            log.warn("delete_graph 缺少 name");
            return;
        }
        boolean existed = NodeGraphManager.INSTANCE.removeGraph(name);
        JsonObject reply = new JsonObject();
        reply.addProperty("type", "deleted");
        reply.addProperty("name", name);
        reply.addProperty("existed", existed);
        conn.send(GSON.toJson(reply));
        log.info("已删除节点图「{}」（{}）。", name, existed ? "文件已删除并移出内存" : "文件本就不存在");
    }

    // ---------- 推送 ----------

    /** 把一次执行错误广播给所有已连接的网页；没有连接时静默跳过 */
    public void pushError(NodeGraphError error) {
        if (error == null) {
            return;
        }
        Collection<WebSocket> conns = getConnections();
        if (conns == null || conns.isEmpty()) {
            return;
        }
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "error");
        msg.add("data", JsonParser.parseString(error.toJson()));
        broadcast(GSON.toJson(msg));
    }

    // ---------- 组装 ----------

    /** 磁盘上 extensions/ 里的全部扩展包 */
    private String extensionsMessage() {
        JsonArray arr = new JsonArray();
        Path dir = extensionsDir();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : stream) {
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        arr.add(JsonParser.parseReader(reader));
                    } catch (Exception e) {
                        log.warn("跳过无法读取的扩展包：" + file.getFileName(), e);
                    }
                }
            } catch (IOException e) {
                log.warn("读取扩展包目录失败：" + dir, e);
            }
        }
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "extensions");
        msg.add("data", arr);
        return GSON.toJson(msg);
    }

    /** 磁盘上已有的节点图名（文件名，不含 .json） */
    private String graphsMessage() {
        JsonArray arr = new JsonArray();
        Path dir = graphsDir();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : stream) {
                    String fileName = file.getFileName().toString();
                    arr.add(fileName.endsWith(".json")
                            ? fileName.substring(0, fileName.length() - 5)
                            : fileName);
                }
            } catch (IOException e) {
                log.warn("读取节点图目录失败：" + dir, e);
            }
        }
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "graphs");
        msg.add("data", arr);
        return GSON.toJson(msg);
    }
}