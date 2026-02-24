package pers.XiaoShadiao.skydiao.utils.mircosoftaccount;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import pers.XiaoShadiao.skydiao.screen.mircosoftaccount.AccountSelectScreen;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

public class VerifyHttpServer {

    private HttpServer server;

    public void start() throws Exception {
        // 创建 HTTP 服务器，监听 8080 端口
        server = HttpServer.create(new InetSocketAddress(835), 0);

        // 设置请求处理器
        server.createContext("/", new Handler());
        // 启动服务器
        server.start();
        // System.out.println("HTTP server started on port 8080");
    }

    public void stop() throws Exception {
        server.stop(0);
    }

    // 请求处理器
    class Handler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            String code = params.get("code");

            String response = "登录成功了, 返回你的mc客户端看看?";
            try {
                MinecraftLogin.MinecraftSessionContainer msc = MinecraftLogin.instance.initializeAccount(code, false);
                msc.downloadSkin();

                ToolList.getInstance().log.info("Login! " + msc.name + " - " + msc.uuid);

                AccountSelectScreen.addAccount(msc);
            } catch (Exception e) {
                AccountSelectScreen.tipError("登录失败: " + e);
                e.printStackTrace();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                e.printStackTrace(new PrintStream(out, false, "UTF-8"));
                response = "登录失败: \n\n" + out.toString("UTF-8");
            }

            // 设置响应头
            exchange.sendResponseHeaders(200, response.length());
            // 写入响应体
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.flush();
            os.close();

        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&"); // 按 & 分割参数
            for (String pair : pairs) {
                int idx = pair.indexOf("="); // 查找参数名和参数值之间的 =
                if (idx > 0) {
                    String key = decode(pair.substring(0, idx)); // 解码键
                    String value = decode(pair.substring(idx + 1)); // 解码值
                    params.put(key, value);
                }
            }
        }
        return params;
    }

    private String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

}