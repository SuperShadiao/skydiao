package pers.XiaoShadiao.skydiao.irc;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import pers.XiaoShadiao.skydiao.SkyDiaoModClient;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;

public class ChatClient extends Thread {

    protected static boolean chatServerAvailable = false;
    private static volatile long heartbeatTime;
    static int retryCount = 0;

    protected static final Logger log = LogManager.getLogger("XSDChat");
    protected static final byte[] HEADER = new byte[] {0x00, (byte) 0xFF, 0x02, (byte) 0xFF, 0x00};
    protected static final byte[] END = new byte[] {0x01, (byte) 0xFF, 0x00, (byte) 0xFF, 0x01};
    public static Socket socket;
    public ClientListener listener;
    public ClientSender sender;

    private static int doWhileToken = 0;
    private int currentWhileToken = 0;
    public static final File chatUrlCache = Path.of(ToolList.mc.gameDirectory.getPath(), "XSDChat", "chaturlcache_new.txt").toFile();

    @Override
    public void run() {
        try {
            if(isWindows8OrHigher()) {
                run0();
            } else {
                log.warn("当前系统低于Windows 8, 无法使用在线聊天功能, 终止中");
            }
        } catch (IOException e) {
            log.error("在线聊天线程发生错误, 停止中", e);
            doWhileToken = 0;
            try {
                socket.close();
            } catch (IOException ex) {
            }
        }
    }

    private void run0() throws IOException {

        try {
            long time = 0;
            String url = "";
            try {
                time = System.currentTimeMillis();
                doWhileToken = currentWhileToken = ToolList.getInstance().random.nextInt();

                log.info("获取URL服务器中...");
                try(InputStream is = ToolList.getInstance().makeReqToURL("https://xiaoshadiao.club/xsdwk/chatlink?action=get")) {
                    url = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                } catch (Exception e) {}

                if(url.trim().isEmpty()) {
                    try {
                        url = FileUtils.readFileToString(chatUrlCache, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException("获取URL服务器失败", e);
                    }
                } else {
                    FileUtils.writeStringToFile(chatUrlCache, url, StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                e.printStackTrace();
                url = "xsdircchat.xiaoshadiao.club:831";
            }

            log.info("URL: " + url.substring(0, 5) + " ...");
            if(url.trim().isEmpty()) throw new RuntimeException("获取URL服务器失败, URL为空");

            FileUtils.writeStringToFile(chatUrlCache, url, StandardCharsets.UTF_8);

            log.info("创建Socket...");
            try {
                String[] hostPort = url.replace("https://", "").replace("http://", "").replace("tcp://", "").split(":");
                // String[] hostPort = {"localhost", "831"};
                socket = new Socket(hostPort[0], Integer.parseInt(hostPort[1]));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            listener = new ClientListener();
            sender = new ClientSender();
            listener.setName("HHOnlineChatListener");
            sender.setName("HHOnlineChatSender");
            listener.start();
            sender.start();
            UncaughtExceptionHandler handler = (t, e) -> {
                log.info("IRC线程" + t.getName() + "发送错误");
                e.printStackTrace();
            };
            listener.setUncaughtExceptionHandler(handler);
            sender.setUncaughtExceptionHandler(handler);

            log.info("已完成, 用时" + ((System.currentTimeMillis() - time) / 1000d) + "s, 开始进入循环!");

            sendInitPackets();

            chatServerAvailable = true;

            int ijjjjj = 0;
            flagHeartbeat();
            while(doWhileToken == currentWhileToken) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
                if(!listener.isAlive()) {
                    throw new RuntimeException("Chat Listener Thread crashed");
                }
                if(!sender.isAlive()) {
                    throw new RuntimeException("Chat Sender Thread crashed");
                }
                if(System.currentTimeMillis() - heartbeatTime > 120000) {
                    throw new IOException("Heartbeat Timeout");
                }
                if(ijjjjj % 60 == 0) sender.sendHeartbeat();
                if(ijjjjj++ == 10) retryCount = 5;
            }
        } catch (Exception e) {
            log.info("在线聊天线程发生错误, 停止中", e);
            chatServerAvailable = false;
        } finally {
            try { listener.interrupt(); } catch (Exception e) { }
            try { sender.interrupt(); } catch (Exception e) { }
            try { socket.close(); } catch (Exception e) { }

            if(retryCount > 0) {
                retryCount--;
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                }
                ChatClientManager.refreshChatClient(false);
            }
        }

    }

    private void sendInitPackets() {
        ChatPacket p = new ChatPacket();
        p.initSender();
        p.packetType = "join";
        p.message = "hhskb " + SkyDiaoModClient.VERSION;
        sender.send(p);

        if(AbstractListener.basicListener.isAFK()) sender.sendAFK(true);

        sender.sendOnlineInfo();
        ToolList.getInstance().updatePartyInfo();
    }

    public void flagHeartbeat() {
        heartbeatTime = System.currentTimeMillis();
    }

    @Deprecated
    public static boolean findProcess(String processName) {
        BufferedReader bufferedReader = null;
        try {
            Process proc = Runtime.getRuntime().exec("tasklist");
            bufferedReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line = null;
            while ((line = bufferedReader.readLine()) != null) {
                // System.out.println(line);
                if (line.contains(processName)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (Exception ex) {
                }
            }
        }

    }

    @Deprecated
    public static boolean isWindows8OrHigher() {
        // 获取操作系统名称
//        String os = System.getProperty("os.name").toLowerCase();
//        if (!os.contains("win")) {
//            System.out.println("当前系统不是Windows");
//            return false; // 如果不是Windows，直接返回false
//        }
//
//        // 获取操作系统版本
//        String version = System.getProperty("os.version");
//        // 输出版本号
//        System.out.println("操作系统版本号: " + version);
//
//        // 将版本号拆分
//        String[] versionParts = version.split("\\.");
//        int majorVersion;
//        try {
//            majorVersion = Integer.parseInt(versionParts[0]); // 主要版本号
//        } catch (NumberFormatException e) {
//            return false; // 如果转换失败，返回false
//        }
//
//        return majorVersion > 6 || (majorVersion == 6 && versionParts.length > 1 && Integer.parseInt(versionParts[1]) >= 2);
        return true;
    }
}
