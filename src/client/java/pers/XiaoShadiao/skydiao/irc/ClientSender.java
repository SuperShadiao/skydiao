package pers.XiaoShadiao.skydiao.irc;

import com.google.gson.JsonObject;
import pers.XiaoShadiao.skydiao.utils.StatusManager;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;

public class ClientSender extends ChatClient {
    private final byte[] something = new byte[] {81};
    private final ArrayBlockingQueue<ChatPacket> queue = new ArrayBlockingQueue<>(100);

    private final OutputStream out;

    public ClientSender(OutputStream out) {
        this.out = out;
    }

    @Override
    public void run() {
        try {
            while(true) {
                ChatPacket packet = queue.take();

                out.write(HEADER);
                out.write(packet.getBuffer());
                out.write(END);

                if(!"heartbeat".equals(packet.packetType) && !"last_error".equals(packet.packetType)) {
                    String json = packet.getJson().toString();
                    if(json.length() < 500) {
                        log.info("Send packet: " + json);
                    } else {
                        log.info("Send packet " + packet.packetType + " (" + json.length() + ")");
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void send(ChatPacket packet) {
        queue.add(packet);
    }

    public void sendMessage(String msg) {
        ChatPacket packet = new ChatPacket();
        packet.message = msg;
        packet.packetType = "chat";
        packet.initSender();
        send(packet);
    }

    public void sendHeartbeat() {
        ChatPacket packet = new ChatPacket();
        packet.packetType = "heartbeat";
        send(packet);
    }

    public void sendAFK(boolean isAFK) {
        ChatPacket packet = new ChatPacket();
        packet.packetType = "afk";
        packet.message = String.valueOf(isAFK);
        packet.initSender();
        send(packet);
    }

    public void sendOnlineInfo() {
        ChatPacket packet = new ChatPacket();
        packet.packetType = "onlineinfo";

        JsonObject json = new JsonObject();
        StatusManager sm = StatusManager.get();
        if(sm != null) {
            json.addProperty("serverID", String.valueOf(sm.getServerID()));
            json.addProperty("gameType", String.valueOf(sm.getType()));
            json.addProperty("lobbyName", String.valueOf(sm.getLobbyName()));
            json.addProperty("mode", String.valueOf(sm.getMode()));
        }

        packet.message = json.toString();
        packet.initSender();
        send(packet);
    }

}
