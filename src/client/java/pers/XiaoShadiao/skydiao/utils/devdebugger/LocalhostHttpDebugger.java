package pers.XiaoShadiao.skydiao.utils.devdebugger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * 本地 HTTP 调试器：只监听 {@code 127.0.0.1:7891}，仅在开发环境启动。
 *
 * <p>用途是把“写探针 → 编译 → 上传 → 执行”做成闭环，修改调试代码不需要重编译模组，
 * 更不需要重启游戏。
 *
 * <h2>接口</h2>
 * <ul>
 *   <li>{@code POST /class?name=完整类名[&thread=client|io][&timeout=毫秒]} —— 请求体为单个 .class；</li>
 *   <li>{@code POST /jar?name=完整类名[...]} —— 请求体为 jar，可携带多个辅助类；</li>
 *   <li>{@code GET /health} —— 存活与环境信息；</li>
 *   <li>{@code GET /classpath} —— 运行期 classpath，供外部 javac 编译探针。</li>
 * </ul>
 *
 * <p>探针返回值若为 {@code byte[]}，直接作为响应体回传，可用 {@code ?type=image/png} 指定类型。
 *
 * <p>执行线程：默认把探针提交到客户端主线程并等待（{@code timeout} 默认 8 秒）；
 * {@code thread=io} 则在 HTTP 线程直接执行，适合游戏画面卡死时的只读排查。
 * 探针内部不要再调用 {@code Minecraft.execute}，会死锁。
 */
public class LocalhostHttpDebugger {

    private static final int PORT = 7891;
    private static final int MAX_BODY = 8 * 1024 * 1024;
    private static final long DEFAULT_TIMEOUT_MS = 8_000L;
    private static final Pattern CLASS_NAME = Pattern.compile("^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*$");
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private static boolean launched = false;
    private static HttpServer httpServer;

    public static void startDebugger() {
        if (launched) return;
        launched = true;
        if (!ToolList.getInstance().isDevEnvironment()) return;

        try {
            httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            httpServer.createContext("/class", LocalhostHttpDebugger::onClassDebug);
            httpServer.createContext("/jar", LocalhostHttpDebugger::onJarDebug);
            httpServer.createContext("/health", ex -> sendJson(ex, 200, health()));
            httpServer.createContext("/classpath", ex -> sendText(ex, 200, System.getProperty("java.class.path", "")));
            httpServer.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "XSD-Debug-HTTP");
                thread.setDaemon(true);
                return thread;
            }));
            httpServer.start();
            ToolList.getInstance().log.info("本地调试器已监听 http://127.0.0.1:{}/ （/class、/jar、/health、/classpath）", PORT);
        } catch (IOException e) {
            ToolList.getInstance().log.warn("本地调试器启动失败，端口 {}", PORT, e);
        }
    }

    private static void onJarDebug(HttpExchange httpExchange) {
        handleUpload(httpExchange, true);
    }

    private static void onClassDebug(HttpExchange httpExchange) {
        handleUpload(httpExchange, false);
    }

    private static void handleUpload(HttpExchange exchange, boolean jar) {
        long start = System.currentTimeMillis();
        URLClassLoader loader = null;
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, error("只接受 POST"));
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String name = query.get("name");
            if (name == null || !CLASS_NAME.matcher(name).matches()) {
                sendJson(exchange, 400, error("必须提供合法的 name=完整类名，例如 name=pers.XiaoShadiao.probe.MapPng"));
                return;
            }

            byte[] body = readLimited(exchange.getRequestBody());
            if (body.length == 0) {
                sendJson(exchange, 400, error("请求体为空"));
                return;
            }
            if (jar ? !isZip(body) : !isClassFile(body)) {
                sendJson(exchange, 400, error(jar ? "请求体不是合法 jar（缺少 PK 头）" : "请求体不是合法 class（缺少 CAFEBABE 头）"));
                return;
            }

            String mode = query.getOrDefault("thread", "client");
            long timeout = parseLong(query.get("timeout"), DEFAULT_TIMEOUT_MS);

            Path dir = probeRoot().resolve("p" + System.nanoTime());
            Files.createDirectories(dir);

            URL url;
            if (jar) {
                Path file = dir.resolve("probe.jar");
                Files.write(file, body);
                url = file.toUri().toURL();
            } else {
                Path file = dir.resolve(name.replace('.', '/') + ".class");
                Files.createDirectories(file.getParent());
                Files.write(file, body);
                url = dir.toUri().toURL();
            }

            // 父加载器必须是模组自身的加载器，否则探针看不到游戏与模组的类型
            ClassLoader parent = LocalhostHttpDebugger.class.getClassLoader();
            loader = new URLClassLoader(new URL[]{url}, parent);

            Class<?> type = Class.forName(name, true, loader);
            if (!DebugProbe.class.isAssignableFrom(type)) {
                sendJson(exchange, 400, error(name + " 没有实现 " + DebugProbe.class.getName()));
                return;
            }

            DebugProbe probe = (DebugProbe) type.getDeclaredConstructor().newInstance();
            Object result = runProbe(probe, new DebugContext(dir), mode, timeout);

            if (result instanceof byte[] bytes) {
                sendBytes(exchange, 200, query.getOrDefault("type", "application/octet-stream"), bytes);
                return;
            }

            JsonObject reply = new JsonObject();
            reply.addProperty("ok", true);
            reply.addProperty("name", name);
            reply.addProperty("ms", System.currentTimeMillis() - start);
            reply.add("result", GSON.toJsonTree(result));
            sendJson(exchange, 200, reply);
        } catch (Throwable throwable) {
            sendJson(exchange, 500, exception(throwable));
        } finally {
            if (loader != null) {
                try {
                    loader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static Object runProbe(DebugProbe probe, DebugContext context, String mode, long timeoutMs) throws Throwable {
        if (!"client".equalsIgnoreCase(mode)) {
            return probe.run(context);
        }

        CompletableFuture<Object> future = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                future.complete(probe.run(context));
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("探针执行超过 " + timeoutMs + "ms，客户端线程可能被阻塞；"
                    + "探针仍在后台继续，可改用 thread=io 或加大 timeout");
        } catch (ExecutionException e) {
            throw e.getCause() == null ? e : e.getCause();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    // ---------- 响应 ----------

    private static JsonObject health() {
        JsonObject json = new JsonObject();
        json.addProperty("ok", true);
        json.addProperty("port", PORT);
        json.addProperty("java", System.getProperty("java.version", "?"));
        json.addProperty("inWorld", Minecraft.getInstance().level != null);
        return json;
    }

    private static JsonObject error(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("ok", false);
        json.addProperty("error", message);
        return json;
    }

    private static JsonObject exception(Throwable throwable) {
        JsonObject json = error(throwable.getClass().getName() + ": " + throwable.getMessage());
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        json.addProperty("stack", writer.toString());
        return json;
    }

    private static void sendJson(HttpExchange exchange, int status, JsonObject body) {
        sendBytes(exchange, status, "application/json; charset=utf-8", GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
    }

    private static void sendText(HttpExchange exchange, int status, String body) {
        sendBytes(exchange, status, "text/plain; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendBytes(HttpExchange exchange, int status, String contentType, byte[] body) {
        try {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        } catch (IOException ignored) {
        } finally {
            exchange.close();
        }
    }

    // ---------- 工具 ----------

    private static Path probeRoot() throws IOException {
        Path root = FabricLoader.getInstance().getGameDir().resolve("debug-probes");
        Files.createDirectories(root);
        return root;
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BODY) {
                throw new IOException("请求体超过 " + MAX_BODY + " 字节");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int index = pair.indexOf('=');
            if (index < 0) {
                params.put(decode(pair), "");
            } else {
                params.put(decode(pair.substring(0, index)), decode(pair.substring(index + 1)));
            }
        }
        return params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static long parseLong(String value, long fallback) {
        if (value == null) return fallback;
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean isClassFile(byte[] bytes) {
        return bytes.length >= 4
                && (bytes[0] & 0xFF) == 0xCA && (bytes[1] & 0xFF) == 0xFE
                && (bytes[2] & 0xFF) == 0xBA && (bytes[3] & 0xFF) == 0xBE;
    }

    private static boolean isZip(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4;
    }
}
