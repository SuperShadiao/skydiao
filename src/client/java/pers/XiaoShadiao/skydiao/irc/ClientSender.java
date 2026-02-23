package pers.XiaoShadiao.skydiao.irc;

import com.google.gson.JsonObject;
import pers.XiaoShadiao.skydiao.utils.StatusManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

public class ClientSender extends ChatClient {
    private final byte[] something = new byte[] {81};
    private final ArrayBlockingQueue<ChatPacket> queue = new ArrayBlockingQueue<>(100);
    @Override
    public void run() {
        try {
            while(true) {
                ChatPacket packet = queue.take();
                // ByteBuffer buffer = ByteBuffer.allocate(16384);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                buffer.write(HEADER);
                buffer.write(packet.getBuffer());
                buffer.write(END);

                socket.getOutputStream().write(buffer.toByteArray());

                if(!packet.packetType.equals("heartbeat")) log.info("Send packet: " + packet.getJson());
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
